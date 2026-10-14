# Goldy's Auto Category Naming (LLM-suggested taxonomy)

**Status:** Draft — for review
**Date:** 2026-10-14
**Scope:** Automate the *naming* step of the internal product categorisation pipeline.
Instead of a human naming each cluster by hand, the existing OpenAI model drafts a
taxonomy (category name + parent group + merge/split advice) after every clustering
run, and the human reviews and improves that draft before applying it. Jev remains the
per-product assigner.

This is a follow-on to `2026-10-13-product-categorisation-design.md`; it changes the
curation step of that pipeline, not the embed/cluster/assign steps.

## 1. Objective

`Run classification` today produces clusters but no categories — categories only appear
after a human opens the Clusters tab and merges/names clusters one at a time. The result
is that a first-time user sees "No categories yet" and assumes the run did nothing.

Make the naming step automatic-but-guarded: after clustering, the LLM **proposes** a
taxonomy (a list of categories with names, optional groups, and merge/split advice); the
human **reviews and improves** it; only then is it **applied** to create real
`product_category` rows. Nothing becomes a live category without explicit human approval.

## 2. Decisions (locked)

| Decision | Choice |
|---|---|
| Interaction | **Suggest → review → improve → apply** (not fully automatic) |
| Naming model | The **existing** OpenAI `ChatModel` (`gpt-4o-mini`, Spring AI) already wired for Ask Goldy's — no new external model |
| Suggestion scope | **Category name + parent group + merge/split advice** |
| Trigger | **Automatically after clustering** (CLUSTER and FULL runs) |
| Storage | **Approach A** — a persisted draft in a new `category_suggestion` table, applied on approval |
| Assignment | **Jev unchanged** — per-product `Choice` against *confirmed* categories only |
| Failure mode | **Best-effort** — a failed/missing namer never fails the run |

Jev cannot generate text (it is a `choice`/`score`/`noul` decision model), so naming is
a *generative* task done by the OpenAI model. Merge/split "advice" is not a special
field: it is simply how the LLM groups the flat-cut clusters — a category covering two
clusters *is* the merge advice; a cluster the LLM subdivides *is* the split advice —
surfaced to the reviewer as a human-readable `hint` per suggestion.

## 3. Current baseline

- `classification` package (from the categorisation feature) owns the pipeline:
  `ProductClassificationService` orchestrates `embed → cluster → assign`, with
  `runClustering()` / `runAssignment()` / `runFull()` and a `submit(kind)` →
  `executor.execute(() -> execute(runId))` pattern. `requireEnabled(kind)` gates the
  embedding/Jev flags and fails fast when a required service is disabled.
- Ports + adapters: `EmbeddingClient` / `EmbeddingServiceHttpClient` (local Python
  service) and `ProductClassifier` / `JevProductClassifier` (hosted TypeSafe). Both
  adapters use the JDK `HttpClient`, not Spring AI.
- Curation writes live in `ProductCategoryService` (`create`/`rename`/`regroup`/`move`/
  `clearOverride`), each behind `permissions.require(role, products.category, WRITE)`.
- The OpenAI `ChatModel` is wired in `conversational.ConversationalAiConfig`, gated on
  `spring.ai.model.chat=openai` (Spring AI 1.1.8, `spring-ai-starter-model-openai`).
  Production has it configured (`gpt-4o-mini`, key present).
- Schema is at `V64`; the next migration is `V65`.

## 4. Scope

### In scope

- `V65`: `category_suggestion` draft table.
- `ClusterNamer` port + `OpenAiClusterNamer` adapter (Spring AI `ChatModel`) in
  `classification`.
- `ProductClassificationService`: after clustering, generate + persist suggestions.
- `ProductCategoryService` (curation): suggestion CRUD + apply, behind
  `products.category` WRITE.
- `ProductCategoryReportingService` + `ProductCategoryController`: `/suggestions`
  endpoints and a `semantic.CategorySuggestionView` record.
- Frontend: Api contract (live + demo) and a **Suggestions** tab (review → improve →
  apply).

### Out of scope (explicitly deferred)

- Fully-automatic apply (no human approval).
- LLM-determined *product-level* assignment (that is Jev's job; the LLM drafts the
  taxonomy shape only).
- Re-clustering at a finer/coarser cut chosen by the LLM (the LLM works over the
  existing flat cut).
- Reusing `Ask Goldy's` conversational `ChatClient`/tool dispatcher — the namer uses the
  raw `ChatModel` directly.
- Batch/async naming outside the run executor.

## 5. Schema (Flyway)

### `V65__category_suggestion.sql`

A working draft, keyed to the run that produced it (curated/resolved state, **not**
bitemporal — like the other `classification_*` tables):

```sql
CREATE TABLE category_suggestion (
    id            uuid PRIMARY KEY,
    run_id        uuid NOT NULL REFERENCES classification_run(id),
    name          varchar(128) NOT NULL,
    group_name    varchar(128),
    display_order int NOT NULL DEFAULT 0,
    hint          varchar(512),
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_category_suggestion_run ON category_suggestion (run_id);
```

No `status` column: the table *is* the draft. Applying promotes its rows to
`product_category` and deletes them. The active draft is the set of rows for the latest
run that generated suggestions.

No permission migration is needed — `products.category` (seeded `V62`) already covers
the read and write surfaces.

## 6. Packages & boundaries

Stays inside `com.goldys.platform.classification`:

- **`ClusterNamer`** (port) — `List<CategorySuggestion> suggest(List<ClusterSummary>)`.
  Value records:
  - `ClusterSummary(int index, List<String> topProducts, List<String> oldCategories, int size)`
    — one per flat-cut cluster, with the products/old-category names the model needs to
    name it meaningfully.
  - `CategorySuggestion(String name, String groupName, String hint)` — the proposed
    category; `groupName` nullable; `hint` carries the merge/split context.
- **`OpenAiClusterNamer`** (adapter) — Spring AI `ChatModel` → a prompt listing the
  clusters → JSON array back → parsed with the shared Jackson `ObjectMapper`. Uses the
  raw `ChatModel`, so `classification` gains a Spring AI dependency (third-party, not a
  platform package — the ArchUnit boundary is unchanged).
- **`NamingProperties`** (`classification.naming.enabled`, default `false`).

The namer bean is created only when `classification.naming.enabled=true` **and** the
OpenAI `ChatModel` is configured (gate on `spring.ai.model.chat=openai`, mirroring
`ConversationalAiConfig`). `ProductClassificationService` consumes it via
`ObjectProvider<ClusterNamer>` and silently skips naming when absent.

`ArchitectureBoundariesTest` is unchanged: `classification` still reaches only
`canonical`, `semantic`, `auth`, and third-party deps (now including Spring AI).

## 7. Naming pipeline

`ProductClassificationService.execute(runId)`, for `CLUSTER` and `FULL` kinds, after
`cluster(run)` succeeds and **before** `assign(run)` (FULL), inserts:

```
if (namer present && namingProperties.enabled()) {
    List<ClusterSummary> clusters = summarize(clusterResult);   // in-memory flat cut
    List<CategorySuggestion> suggestions = namer.suggest(clusters);
    suggestions.forEach(s -> suggestionsRepo.save(toEntity(s, run.id())));
}
```

- `cluster(run)` is refactored to also surface, in memory, the flat-cut labels the
  embedding service returns (`ClusterResult.labels`, currently dropped) together with
  the product names those labels cover — no new persistence. `summarize(...)` then
  builds one `ClusterSummary` per flat-cut cluster (top N product names by frequency,
  distinct old category names, leaf count), capped so the prompt stays bounded.
- Naming is **best-effort**: any `RuntimeException` from the namer (bad JSON, HTTP
  error, missing model) is caught, logged, and the run still ends `SUCCEEDED` with
  clustering/assignment results intact. Suggestions are absent, not a failure.
- `ASSIGN`-only runs do **not** name (no clustering happened).

The prompt (system + user) asks for a JSON array of categories, each with `name`,
optional `group`, and an optional `hint` explaining the merge/split reasoning. The
adapter parses and validates it; an unparseable payload yields an empty list + log.

## 8. Review & apply model

`ProductCategoryService` gains the suggestion write path, each behind
`permissions.require(role, products.category, WRITE)` (list is READ):

- `listSuggestions()` — the latest run's suggestions, in `display_order`.
- `addSuggestion(name, groupName)` — the reviewer adds a category the LLM missed.
- `updateSuggestion(id, name, groupName)` — rename / regroup (null = unchanged).
- `deleteSuggestion(id)` — reject/remove.
- `applySuggestions()` — for each current suggestion, in order, create a
  `product_category` row (`name`, `groupName`, `display_order`) **skipping** any whose
  `name` already exists (global-unique); then delete all suggestions of the run. Returns
  the created `CategoryNode`s.

Merge/split *editing* in the UI is expressed with these primitives: merging two
suggestions = delete one + rename the other; splitting one = add a second + rename.
No dedicated merge/split endpoints.

Apply does **not** assign products — it only creates the taxonomy. The operator then
runs `ASSIGN` (or `FULL`) as today, and Jev fills `product_category_assignment` against
the confirmed categories.

## 9. REST + semantic

New `semantic.CategorySuggestionView`:
`(UUID id, UUID runId, String name, String groupName, int displayOrder, String hint)`.

Under `/api/products/categories`:

| Method | Path | Body | Result |
|---|---|---|---|
| GET | `/suggestions` | — | `List<CategorySuggestionView>` (latest run) |
| POST | `/suggestions` | `{ name, groupName }` | created view |
| PATCH | `/suggestions/{id}` | `{ name?, groupName? }` | `void` (null = unchanged) |
| DELETE | `/suggestions/{id}` | — | `void` |
| POST | `/suggestions/apply` | — | `List<CategoryNode>` (created categories) |

All authorized through `ProductCategoryReportingService` (READ for list, WRITE for the
rest), mirroring the existing controller/service split.

## 10. Frontend

- **Api contract** (`types.ts` / `live.ts` / `demo.ts`): `getCategorySuggestions`,
  `addCategorySuggestion`, `updateCategorySuggestion`, `deleteCategorySuggestion`,
  `applyCategorySuggestions`. Demo implementations return a couple of canned
  suggestions whose "apply" merges into the demo taxonomy fixtures.
- **Suggestions tab** on the Products page: lists each suggestion with inline-editable
  name and group, its merge/split `hint`, a delete control, an "Add category" action,
  and an **"Apply taxonomy"** button that applies and jumps to the Categories tab.
  Empty state: "Run classification to generate suggestions."
- The existing Categories/Clusters/Pipeline tabs are unchanged; the Clusters tab's
  manual merge/rename remains as the fallback for after-apply tweaks.

## 11. Failure & edge handling

- **Namer absent** (OpenAI not configured, or `classification.naming.enabled=false`):
  clustering runs as today, no suggestions, no error. The Suggestions tab shows the
  empty state.
- **Namer error mid-run** (non-JSON, HTTP 4xx/5xx, timeout): logged; run `SUCCEEDED`;
  zero suggestions.
- **Duplicate name on apply**: skip the suggestion (the confirmed category already
  exists); never error or create duplicates.
- **Two suggestions share a name**: apply the first, skip the rest.
- **Re-run before applying**: a new run's suggestions supersede the old run's draft
  (the review surface always shows the latest run's suggestions).
- **Empty taxonomy on assign**: unchanged from the base feature — all products
  `UNASSIGNED` until categories exist.

## 12. Testing

- **Backend**:
  - `OpenAiClusterNamerTest` — fake `ChatModel` returning a JSON body → parsed
    suggestions; empty/non-JSON/error payloads → empty list.
  - `ProductClassificationServiceTest` — naming called after clustering for CLUSTER and
    FULL, not for ASSIGN; disabled/missing namer skips without failing; namer throwing
    still leaves the run `SUCCEEDED`.
  - `ProductCategoryServiceTest` — suggestion CRUD + apply (creates categories in
    order, skips duplicate names, clears the draft), each requires `WRITE`.
  - `ProductCategoryReportingServiceTest` / `ProductCategoryControllerTest` —
    authorization and delegation for the five endpoints.
  - `ArchitectureBoundariesTest` — still green (no new platform dependency).
- **Frontend**: Suggestions tab render/edit/delete/apply, empty state, demo parity for
  the new `Api` methods.

## 13. Boundaries

### Always

- Suggestions are a draft; only **applied** suggestions become `product_category`.
- Read direction preserved: the frontend reads via `semantic` records, never the
  `classification` tables directly.
- Jev remains the only per-product assigner; the LLM never writes
  `product_category_assignment`.
- Naming is best-effort and never fails a clustering/assignment run.
- Old `category_name` / `category` columns untouched.

### Ask first

- Fully-automatic apply (skip the human review).
- LLM-driven re-clustering (choosing a different dendrogram cut) or product-level
  assignment by the LLM.
- Introducing a second naming model / provider beyond the existing OpenAI `ChatModel`.

### Never

- Write back to Lightspeed/CTB.
- Let `api`/`reporting` reach `classification` (only `semantic`).
- Persist embeddings (unchanged: recomputed per run).

## 14. Follow-on (gated)

- Per-suggestion confidence from the LLM (a `score`/`noul` gate on "is this name good?",
  via Jev).
- Batch-apply from the Clusters tab without a separate Suggestions tab.
- Auto-trigger an `ASSIGN` run immediately after apply.
