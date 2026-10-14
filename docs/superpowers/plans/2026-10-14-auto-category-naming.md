# Auto Category Naming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After every clustering run, have the existing OpenAI model draft a taxonomy (category name + optional group + merge/split hint) that staff review, improve, and then apply to create real `product_category` rows — so naming is no longer a manual, one-cluster-at-a-time chore.

**Architecture:** A new `ClusterNamer` port (mirroring `ProductClassifier`) whose `OpenAiClusterNamer` adapter reuses the Spring AI `ChatModel` already wired for Ask Goldy's, in the `classification` package. `ProductClassificationService` calls it after clustering (best-effort) and persists the draft to a new `category_suggestion` table; a new Suggestions tab on the Products page reviews/edits the draft and applies it via `ProductCategoryService`, which creates `product_category` rows. Jev remains the per-product assigner.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring AI 1.1.8 (`spring-ai-starter-model-openai`, `ChatModel`), Flyway (PostgreSQL 16, Testcontainers), Next.js 15 / React 19 / TypeScript / Tailwind (Bun).

**Spec:** `docs/superpowers/specs/2026-10-14-auto-category-naming-design.md`

## Global Constraints

- Schema changes are Flyway migrations only (`hibernate.ddl-auto: validate`); never edit an applied migration. Next version is `V65`.
- `category_suggestion` is curated/resolved state, **not** bitemporal — no `valid_from`/`superseded_at`.
- `classification` may depend on `canonical`, `semantic`, `auth`, and third-party deps (now including Spring AI); it never reaches `api`/`application`/`reporting`/`conversational`.
- Naming is **best-effort**: a missing/disabled namer or a bad LLM response never fails a run.
- Jev never writes `product_category_assignment` here; the LLM never writes `product_category_assignment`. Apply only creates `product_category`.
- Permission resource is `products.category` (READ = view, WRITE = edit/apply), seeded `ALL × OWNER`.
- Frontend demo mode: every new `Api` method needs a live + demo implementation.
- The existing OpenAI `ChatModel` is injected via `ObjectProvider<ChatModel>` and read with `getIfAvailable()` (mirror `LlmInvoicePdfExtractor`).

## Review Focus

1. **First run, empty taxonomy** → apply works (creates categories); a later ASSIGN leaves products `UNASSIGNED` until categories exist, nothing throws. → Task 6.
2. **Namer disabled/absent** (`classification.naming.enabled=false` or no `ChatModel`) → clustering runs as today, zero suggestions, run `SUCCEEDED`, no error. → Task 5.
3. **Namer returns garbage** (non-JSON, wrong shape, empty) → empty suggestion list, run still `SUCCEEDED`. → Task 3.
4. **Apply with a duplicate name** (a suggested name already exists as a confirmed category) → skip it, never error, never create a duplicate. → Task 6.
5. **Two suggestions share a name** → apply the first, skip the rest. → Task 6.
6. **Re-run before applying** → the new run's suggestions replace the old draft (the draft is cleared before writing). → Task 5.

---

### Task 1: `category_suggestion` migration + entity + repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V65__category_suggestion.sql`
- Create: `backend/src/main/java/com/goldys/platform/classification/CategorySuggestion.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/CategorySuggestionRepository.java`

**Interfaces:**
- Consumes: nothing (new).
- Produces (used by Tasks 4, 5, 6):
  - `CategorySuggestion` (package-private entity): `static CategorySuggestion create(UUID runId, String name, String groupName, int displayOrder, String hint)`; accessors `id()`, `runId()`, `name()`, `groupName()`, `displayOrder()`, `hint()`; mutators `rename(String newName)`, `regroup(String newGroupName)`.
  - `CategorySuggestionRepository extends JpaRepository<CategorySuggestion, UUID>` with `List<CategorySuggestion> findAllByOrderByDisplayOrderAsc()`.

- [ ] **Step 1: Write the migration**

`V65__category_suggestion.sql`, matching the `V61` DDL style (`uuid PK`, `timestamp(6) with time zone`). `run_id` is **nullable** so staff-added suggestions (which the LLM missed) need no run:

```sql
CREATE TABLE category_suggestion (
    id            uuid PRIMARY KEY,
    run_id        uuid REFERENCES classification_run(id),
    name          varchar(128) NOT NULL,
    group_name    varchar(128),
    display_order int NOT NULL DEFAULT 0,
    hint          varchar(512),
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_category_suggestion_run ON category_suggestion (run_id);
```

- [ ] **Step 2: Write the entity `CategorySuggestion`**

Package-private, mirroring `ClassificationCluster`/`ProductCategory`. Fields map the columns above exactly (`@Id UUID id`, `@Column(name="run_id") UUID runId`, `name`, `group_name`, `display_order`, `hint`, `created_at Instant`). `create(...)` assigns `UUID.randomUUID()` and `Instant.now()` for `created_at`; `rename`/`regroup` mirror `ProductCategory`'s mutators (no `updated_at` column, so they only set the field). Include package-private accessors and the two mutators.

- [ ] **Step 3: Write the repository `CategorySuggestionRepository`**

Package-private, one derived method: `List<CategorySuggestion> findAllByOrderByDisplayOrderAsc();` (`deleteAll()` is inherited from `JpaRepository`).

- [ ] **Step 4: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: build succeeds. (The migration is exercised end-to-end by Task 8's permission-boundary test; the entity/repo are exercised by Tasks 4–6.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V65__category_suggestion.sql backend/src/main/java/com/goldys/platform/classification/CategorySuggestion.java backend/src/main/java/com/goldys/platform/classification/CategorySuggestionRepository.java
git commit -m "feat(categorisation): category_suggestion draft table + entity + repository"
```

---

### Task 2: `ClusterNamer` port, records, `NamingProperties`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/ClusterNamer.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClusterSummary.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/SuggestedCategory.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/NamingProperties.java`
- Modify: `backend/src/main/resources/application.yml:154-161`

**Interfaces:**
- Consumes: nothing.
- Produces (used by Tasks 3, 5):
  - `public record ClusterSummary(int index, List<String> productTexts, int size)` — `productTexts` are the capped feature texts of the products in that flat-cut cluster.
  - `public record SuggestedCategory(String name, String groupName, String hint)` — the LLM's proposal; `groupName`/`hint` nullable.
  - `public interface ClusterNamer { List<SuggestedCategory> suggest(List<ClusterSummary> clusters); }`
  - `@ConfigurationProperties(prefix = "classification.naming") public record NamingProperties(boolean enabled) {}`

> Note: the spec's port output record was named `CategorySuggestion`; it is renamed `SuggestedCategory` here to avoid colliding with the `CategorySuggestion` entity from Task 1.

- [ ] **Step 1: Write the four files**

`ClusterSummary`/`SuggestedCategory` are plain records; `ClusterNamer` is the port. `NamingProperties` mirrors `EmbeddingProperties`/`JevProperties` (`@ConfigurationProperties(prefix = "classification.naming")`, auto-bound by the app's `@ConfigurationPropertiesScan`).

- [ ] **Step 2: Add the config key**

In `application.yml`, under the existing `classification:` block (lines 154–161), append:

```yaml
  naming:
    enabled: ${CLASSIFICATION_NAMING_ENABLED:false}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: build succeeds. (No test — the port is exercised by Tasks 3 and 5.)

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification/ClusterNamer.java backend/src/main/java/com/goldys/platform/classification/ClusterSummary.java backend/src/main/java/com/goldys/platform/classification/SuggestedCategory.java backend/src/main/java/com/goldys/platform/classification/NamingProperties.java backend/src/main/resources/application.yml
git commit -m "feat(categorisation): ClusterNamer port + naming properties"
```

---

### Task 3: `OpenAiClusterNamer` adapter

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/OpenAiClusterNamer.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/OpenAiClusterNamerTest.java`

**Interfaces:**
- Consumes: `ClusterNamer`, `ClusterSummary`, `SuggestedCategory` (Task 2); Spring AI `ChatModel` via `ObjectProvider<ChatModel>`; the shared `ObjectMapper`.
- Produces (used by Task 5): `@Component OpenAiClusterNamer implements ClusterNamer`.

- [ ] **Step 1: Write the failing test**

`OpenAiClusterNamerTest`, mocking `ChatModel` with `when(model.call(any(Prompt.class)))` and `ObjectProvider<ChatModel>` with `when(provider.getIfAvailable()).thenReturn(model)` (mirror `LlmInvoicePdfExtractorTest`):

```java
// names: model returns a JSON array → parsed into SuggestedCategory list
when(model.call(any(Prompt.class))).thenReturn(chatResponse("""
    [{"name":"Beer","group":"Drinks","hint":"merges clusters 0 and 2"},
     {"name":"Red Wine","group":"Wine","hint":null}]"""));
assertThat(namer.suggest(List.of(summary(0), summary(1)))).containsExactly(
    new SuggestedCategory("Beer", "Drinks", "merges clusters 0 and 2"),
    new SuggestedCategory("Red Wine", "Wine", null));

// no model → empty list, no call
when(provider.getIfAvailable()).thenReturn(null);
assertThat(namer.suggest(List.of(summary(0)))).isEmpty();
verify(model, never()).call(any(Prompt.class));

// non-JSON / empty output → empty list
when(model.call(any(Prompt.class))).thenReturn(chatResponse("not json"));
assertThat(namer.suggest(List.of(summary(0)))).isEmpty();
```

(Add a `chatResponse(String)` helper building a `ChatResponse` whose `getResult().getOutput().getText()` returns the given string, and a `summary(int)` helper.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.classification.OpenAiClusterNamerTest'`
Expected: FAIL (compile — `OpenAiClusterNamer` does not exist).

- [ ] **Step 3: Implement `OpenAiClusterNamer`**

Mirror `LlmInvoicePdfExtractor` exactly:
- `@Component`; fields `ObjectProvider<ChatModel> chatModel` + `ObjectMapper mapper`.
- `suggest(clusters)`: `ChatModel model = chatModel.getIfAvailable(); if (model == null || clusters.isEmpty()) return List.of();`
- Build `new Prompt(List.of(new SystemMessage(SCHEMA_PROMPT), new UserMessage(clusterText)), temperatureZero())`; `model.call(prompt)` → `response.getResult().getOutput().getText()`.
- `SCHEMA_PROMPT` forces a JSON array `[{"name": string, "group": string|null, "hint": string|null}]` with rules: short 1–3 word category name; `group` a parent like "Beer"/"Wine"/"Food" or null; `hint` a short note only when merging or splitting clusters.
- `clusterText` serializes the `ClusterSummary` list (index + product texts + size) as the user message.
- Parse: extract the `[ … ]` span (tolerating a ```json fence), `mapper.readValue(json, new TypeReference<List<SuggestedCategory>>() {})`; any `RuntimeException`/parse failure → `log.warn` + `List.of()`.
- `temperatureZero()` = `new DefaultChatOptionsBuilder().temperature(0.0).build()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.classification.OpenAiClusterNamerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification/OpenAiClusterNamer.java backend/src/test/java/com/goldys/platform/classification/OpenAiClusterNamerTest.java
git commit -m "feat(categorisation): OpenAI cluster namer adapter"
```

---

### Task 4: `CategorySuggestionView` + `ProductCategoryQuery.suggestions()`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/CategorySuggestionView.java`
- Modify: `backend/src/main/java/com/goldys/platform/classification/ProductCategoryQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/ProductCategoryQueryTest.java` (extend if present, else create)

**Interfaces:**
- Consumes: `CategorySuggestionRepository` (Task 1).
- Produces (used by Tasks 6, 7, 9):
  - `public record CategorySuggestionView(UUID id, UUID runId, String name, String groupName, int displayOrder, String hint) {}` (semantic leaf).
  - `ProductCategoryQuery.suggestions()` → `List<CategorySuggestionView>` in `displayOrder`.

- [ ] **Step 1: Write the failing test**

Add to `ProductCategoryQueryTest` a test that constructs the query with a fake `CategorySuggestionRepository` returning two suggestions (one flat, one grouped, with a hint) and asserts:

```java
assertThat(query.suggestions())
    .containsExactly(
        new CategorySuggestionView(s1.id(), s1.runId(), "Beer", "Drinks", 0, "merges clusters 0 and 2"),
        new CategorySuggestionView(s2.id(), s2.runId(), "Red Wine", null, 1, null));
```

The query's constructor gains a `CategorySuggestionRepository` parameter — update every existing construction in the test to pass a mock.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.classification.ProductCategoryQueryTest'`
Expected: FAIL (compile — constructor signature changed).

- [ ] **Step 3: Implement `suggestions()`**

Create `CategorySuggestionView`. Add the repository field + constructor arg to `ProductCategoryQuery`, and:

```java
public List<CategorySuggestionView> suggestions() {
    return suggestions.findAllByOrderByDisplayOrderAsc().stream()
        .map(s -> new CategorySuggestionView(s.id(), s.runId(), s.name(),
            s.groupName(), s.displayOrder(), s.hint()))
        .toList();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.classification.ProductCategoryQueryTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/semantic/CategorySuggestionView.java backend/src/main/java/com/goldys/platform/classification/ProductCategoryQuery.java backend/src/test/java/com/goldys/platform/classification/ProductCategoryQueryTest.java
git commit -m "feat(categorisation): suggestion read facade + CategorySuggestionView"
```

---

### Task 5: Wire naming into `ProductClassificationService`

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/classification/ProductClassificationService.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/ProductClassificationServiceTest.java`

**Interfaces:**
- Consumes: `ClusterNamer` (Task 2), `OpenAiClusterNamer` (Task 3 bean), `CategorySuggestionRepository` (Task 1), `NamingProperties` (Task 2).
- Produces: the `execute()` pipeline now emits `category_suggestion` rows after clustering (best-effort). Internal: `cluster(run)` returns a `Clustering` record instead of `int`.

- [ ] **Step 1: Write the failing tests**

Extend `ProductClassificationServiceTest` (its test-friendly constructor now also takes `ClusterNamer`, `CategorySuggestionRepository`, `NamingProperties` — update all constructions). Add:

```java
// naming called after clustering for CLUSTER, with the flat-cut labels + products
when(namer.suggest(anyList())).thenReturn(List.of(
    new SuggestedCategory("Beer", "Drinks", null)));
// ... runClustering(); execute(run.id());
assertThat(suggestionStore).hasSize(1);            // category_suggestion persisted
assertThat(suggestionStore.get(0).name()).isEqualTo("Beer");

// disabled naming → no suggestion rows, run still SUCCEEDED
// (NamingProperties(false,...)) ; assert suggestionStore empty, run SUCCEEDED

// namer throws → run still SUCCEEDED, no suggestion rows
// when(namer.suggest(anyList())).thenThrow(new RuntimeException("boom"))
```

Add a `suggestionStore` recording fake for `CategorySuggestionRepository.save(...)` (like the existing `runStore`), and stub `findAllByOrderByDisplayOrderAsc()` / `deleteAll()`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.classification.ProductClassificationServiceTest'`
Expected: FAIL (compile — new constructor args; `cluster()` returns `int`).

- [ ] **Step 3: Refactor `cluster()` and wire naming**

- Add a private record `Clustering(List<String> products, Map<String,String> texts, List<Integer> flatLabels)`. Change `cluster(run)` to build and return it (it already holds `texts`, `products`, and `result.labels()` — return them instead of `products.size()`).
- Add fields `ClusterNamer namer`, `CategorySuggestionRepository suggestions`, `NamingProperties namingProperties` to both constructors.
- In `execute()`, replace the switch with explicit per-kind flow so naming runs after clustering and before assignment (FULL), best-effort:

```java
int productCount;
if (run.kind() == ClassificationRunKind.CLUSTER) {
    Clustering c = cluster(run);
    nameSuggestions(run, c);
    productCount = c.products().size();
} else if (run.kind() == ClassificationRunKind.ASSIGN) {
    productCount = assign(run);
} else { // FULL
    Clustering c = cluster(run);
    nameSuggestions(run, c);
    productCount = c.products().size();
    assign(run);
}
run.markSucceeded(productCount, clock.instant());
```

- Add:

```java
private void nameSuggestions(ClassificationRun run, Clustering c) {
    if (!namingProperties.enabled()) return;
    try {
        List<SuggestedCategory> named = namer.suggest(summarize(c));
        suggestions.deleteAll();
        int order = 0;
        for (SuggestedCategory s : named) {
            suggestions.save(CategorySuggestion.create(run.id(), s.name(), s.groupName(), order++, s.hint()));
        }
    } catch (RuntimeException e) {
        log.warn("Category naming failed; continuing without suggestions", e);
    }
}
```

- Add `summarize(Clustering)` that groups `c.flatLabels()` by label (in a `TreeMap` for determinism), collecting each product's `c.texts().get(product)`, and emits one `ClusterSummary(index, cappedProductTexts, size)` per label — cap `productTexts` at 15.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.classification.ProductClassificationServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification/ProductClassificationService.java backend/src/test/java/com/goldys/platform/classification/ProductClassificationServiceTest.java
git commit -m "feat(categorisation): generate taxonomy suggestions after clustering"
```

---

### Task 6: `ProductCategoryService` suggestion CRUD + apply

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/classification/ProductCategoryService.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/ProductCategoryServiceTest.java`

**Interfaces:**
- Consumes: `CategorySuggestionRepository` (Task 1); existing `ProductCategoryRepository`, `ProductCategoryAssignmentRepository`, `PermissionService`.
- Produces (used by Task 7):
  - `CategorySuggestionView addSuggestion(UserRole role, String name, String groupName)`
  - `void updateSuggestion(UserRole role, UUID id, String name, String groupName)` (null = unchanged)
  - `void deleteSuggestion(UserRole role, UUID id)`
  - `List<CategoryNode> applySuggestions(UserRole role)`

- [ ] **Step 1: Write the failing tests**

Extend `ProductCategoryServiceTest` (constructor gains `CategorySuggestionRepository`). Add:

```java
// addSuggestion appends a suggestion and returns its view
assertThat(svc.addSuggestion(owner(), "Cocktails", "Drinks").name()).isEqualTo("Cocktails");

// updateSuggestion renames/regroups (null leaves unchanged)
svc.updateSuggestion(owner(), id, "Beer", null);
svc.updateSuggestion(owner(), id, null, "Drinks");

// applySuggestions creates product_category rows in order, skips a name already taken,
// returns the created CategoryNodes, and clears the draft
when(suggestions.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(s("Beer", "Drinks", 0), s("Beer", "Drinks", 1), s("Wine", null, 2)));
when(categories.findByName("Beer")).thenReturn(Optional.of(existingBeer));  // duplicate
List<CategoryNode> created = svc.applySuggestions(owner());
assertThat(created).extracting(CategoryNode::name).containsExactly("Wine"); // Beer skipped
verify(suggestions).deleteAll();

// every method requires WRITE on products.category
// (permission throws → propagates)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.classification.ProductCategoryServiceTest'`
Expected: FAIL (compile — methods absent).

- [ ] **Step 3: Implement the methods**

Mirror the existing methods' `permissions.require(role, RESOURCE, PermissionAction.WRITE)` pattern:

```java
public CategorySuggestionView addSuggestion(UserRole role, String name, String groupName) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    int order = (int) suggestions.count();
    CategorySuggestion s = suggestions.save(CategorySuggestion.create(null, name, groupName, order, null));
    return new CategorySuggestionView(s.id(), s.runId(), s.name(), s.groupName(), s.displayOrder(), s.hint());
}
public void updateSuggestion(UserRole role, UUID id, String name, String groupName) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    CategorySuggestion s = suggestions.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("No suggestion with id " + id));
    if (name != null) s.rename(name);
    if (groupName != null) s.regroup(groupName);
}
public void deleteSuggestion(UserRole role, UUID id) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    suggestions.deleteById(id);
}
@Transactional
public List<CategoryNode> applySuggestions(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    List<CategoryNode> created = new ArrayList<>();
    for (CategorySuggestion s : suggestions.findAllByOrderByDisplayOrderAsc()) {
        if (categories.findByName(s.name()).isEmpty()) {
            ProductCategory c = categories.save(ProductCategory.create(s.name(), s.groupName(), s.displayOrder(), null));
            created.add(new CategoryNode(c.id(), c.name(), c.groupName(), 0));
        }
    }
    suggestions.deleteAll();
    return created;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.classification.ProductCategoryServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification/ProductCategoryService.java backend/src/test/java/com/goldys/platform/classification/ProductCategoryServiceTest.java
git commit -m "feat(categorisation): suggestion curation + apply"
```

---

### Task 7: Reporting service + controller endpoints

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/application/ProductCategoryReportingService.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/ProductCategoryController.java`
- Test: `backend/src/test/java/com/goldys/platform/application/ProductCategoryReportingServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ProductCategoryControllerTest.java`

**Interfaces:**
- Consumes: `ProductCategoryQuery.suggestions()` (Task 4), `ProductCategoryService` (Task 6), `semantic.CategorySuggestionView`.
- Produces (used by Task 9): the REST endpoints under `/api/products/categories/suggestions`.

- [ ] **Step 1: Write the failing tests**

`ProductCategoryReportingServiceTest`: `listSuggestions(role)` calls `permissions.require(role, products.category, READ)` and returns `query.suggestions()`; `addSuggestion`/`updateSuggestion`/`deleteSuggestion`/`applySuggestions` each delegate to the mocked `ProductCategoryService`; a throwing `PermissionService` propagates.

`ProductCategoryControllerTest` (mirror the existing controller test): `GET /api/products/categories/suggestions` → 200 + list; `POST /suggestions` → `addSuggestion(role, name, groupName)`; `PATCH /suggestions/{id}` → `updateSuggestion`; `DELETE /suggestions/{id}` → `deleteSuggestion`; `POST /suggestions/apply` → `applySuggestions` → 200 + `List<CategoryNode>`. Verify the controller imports only `semantic` records.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.application.ProductCategoryReportingServiceTest' --tests 'com.goldys.platform.api.ProductCategoryControllerTest'`
Expected: FAIL (compile — methods absent).

- [ ] **Step 3: Implement reporting + controller**

`ProductCategoryReportingService`: add the five methods (list does `permissions.require(role, RESOURCE, READ)` then `query.suggestions()`; the rest delegate to `categories.<method>(role, …)`).

`ProductCategoryController`: add the endpoints plus two request records:

```java
@GetMapping("/suggestions") List<CategorySuggestionView> suggestions(@AuthenticationPrincipal AccountUserDetails user) { return reporting.listSuggestions(currentUser.roleOf(user)); }
@PostMapping("/suggestions") CategorySuggestionView add(@RequestBody AddSuggestionRequest body, @AuthenticationPrincipal AccountUserDetails user) { return reporting.addSuggestion(currentUser.roleOf(user), body.name(), body.groupName()); }
@PatchMapping("/suggestions/{id}") void update(@PathVariable UUID id, @RequestBody UpdateSuggestionRequest body, @AuthenticationPrincipal AccountUserDetails user) { reporting.updateSuggestion(currentUser.roleOf(user), id, body.name(), body.groupName()); }
@DeleteMapping("/suggestions/{id}") void delete(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) { reporting.deleteSuggestion(currentUser.roleOf(user), id); }
@PostMapping("/suggestions/apply") List<CategoryNode> apply(@AuthenticationPrincipal AccountUserDetails user) { return reporting.applySuggestions(currentUser.roleOf(user)); }

record AddSuggestionRequest(String name, String groupName) {}
record UpdateSuggestionRequest(String name, String groupName) {}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.application.ProductCategoryReportingServiceTest' --tests 'com.goldys.platform.api.ProductCategoryControllerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/application/ProductCategoryReportingService.java backend/src/main/java/com/goldys/platform/api/ProductCategoryController.java backend/src/test/java/com/goldys/platform/application/ProductCategoryReportingServiceTest.java backend/src/test/java/com/goldys/platform/api/ProductCategoryControllerTest.java
git commit -m "feat(categorisation): suggestion REST endpoints"
```

---

### Task 8: Architecture + permission boundary tests

**Files:**
- Modify: `backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java`
- Modify: `backend/src/test/java/com/goldys/platform/classification/ProductCategoryPermissionBoundaryTest.java` (if present; else create alongside the existing permission-boundary test)

**Interfaces:**
- Consumes: the packages from Tasks 1–7.

- [ ] **Step 1: Add the architecture assertions**

Extend `ArchitectureBoundariesTest` with a rule that `..classification..` may not depend on `..api..`, `..application..`, `..reporting..`, `..conversational..` (Spring AI is third-party and stays allowed) — and confirm the existing `apiDoesNotReachClassification` / `reportingDoesNotReachClassification` rules still hold.

- [ ] **Step 2: Run to verify it passes**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.architecture.ArchitectureBoundariesTest'`
Expected: PASS (the new rule holds; nothing in `classification` reaches a higher platform package).

- [ ] **Step 3: Add the permission-boundary integration test**

Extend the Testcontainers permission test: a user with `ALL × OWNER` can `GET /api/products/categories/suggestions` (200) and `POST /suggestions` (200); a user with no `products.category` grant gets 403/denied.

- [ ] **Step 4: Run the permission test**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.classification.ProductCategoryPermissionBoundaryTest'`
Expected: PASS (also exercises `V65` under `ddl-auto: validate`).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java backend/src/test/java/com/goldys/platform/classification/ProductCategoryPermissionBoundaryTest.java
git commit -m "test(categorisation): suggestion architecture + permission boundaries"
```

---

### Task 9: Frontend Api contract (types / live / demo / index)

**Files:**
- Modify: `frontend/lib/api/types.ts`
- Modify: `frontend/lib/api/live.ts`
- Modify: `frontend/lib/api/demo.ts`
- Modify: `frontend/lib/api/index.ts`
- Test: `frontend/lib/api/demo.test.ts`

**Interfaces:**
- Consumes: the REST contract from Task 7.
- Produces (used by Task 10):
  - `export interface CategorySuggestion { id: string; runId: string | null; name: string; groupName: string | null; displayOrder: number; hint: string | null; }`
  - `Api` methods: `getCategorySuggestions(): Promise<CategorySuggestion[]>`, `addCategorySuggestion(input: { name: string; groupName?: string | null }): Promise<CategorySuggestion>`, `updateCategorySuggestion(id: string, input: { name?: string | null; groupName?: string | null }): Promise<void>`, `deleteCategorySuggestion(id: string): Promise<void>`, `applyCategorySuggestions(): Promise<CategoryNode[]>`.

- [ ] **Step 1: Write the failing demo test**

In `demo.test.ts`, add a case: `getCategorySuggestions()` returns the demo fixtures (including one flat suggestion with `groupName: null`); `applyCategorySuggestions()` then makes those names appear in `getCategoryTaxonomy()`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- demo`
Expected: FAIL (methods absent).

- [ ] **Step 3: Implement the contract**

- `types.ts`: add `CategorySuggestion` and the five `Api` methods (place beside the existing `getCategoryTaxonomy` block, ~line 1026).
- `live.ts`: the five `fetchApi` calls against `/api/products/categories/suggestions…` (POST add with JSON body; PATCH `/{id}`; DELETE `/{id}`; POST `/apply` → `CategoryNode[]`).
- `demo.ts`: a `demoCategorySuggestions: CategorySuggestion[]` fixture (2–3 entries, one flat, one with a hint); `getCategorySuggestions` returns a copy; `addCategorySuggestion` unshifts; `updateCategorySuggestion` mutates in place; `deleteCategorySuggestion` filters; `applyCategorySuggestions` pushes each name into `demoCategoryTaxonomy` and clears `demoCategorySuggestions`.
- `index.ts`: add `CategorySuggestion` to the `export type { … }` block.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && bun run test -- demo`
Expected: PASS.

- [ ] **Step 5: Run typecheck**

Run: `cd frontend && bun run typecheck`
Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/api
git commit -m "feat(categorisation): frontend Api contract for suggestions"
```

---

### Task 10: Suggestions tab on the Products page

**Files:**
- Create: `frontend/components/classification/suggestions-panel.tsx`
- Modify: `frontend/app/(app)/products/page.tsx`
- Test: `frontend/app/(app)/products/products-page.test.tsx`

**Interfaces:**
- Consumes: the `Api` methods from Task 9; the existing `Tabs`/`PageHeader`/`Badge`/`Button`/`Input` components and `useApiData`.
- Produces: a fourth **Suggestions** tab that lists the draft, edits name/group inline, deletes, adds, and applies.

- [ ] **Step 1: Write the failing page test**

In `products-page.test.tsx`, extend the `use-api-data` mock to route `getCategorySuggestions` from a `state.suggestions` fixture, and add:

```jsx
it("renders the Suggestions tab with the draft and applies it", async () => {
  seed(); state.suggestions = [ { id:"sg-1", runId:null, name:"Beer", groupName:"Drinks", displayOrder:0, hint:null } ];
  render(<ProductsPage />);
  fireEvent.mouseDown(screen.getByRole("tab", { name: "Suggestions" }));
  expect(screen.getByDisplayValue("Beer")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Apply taxonomy" }));
  expect(state.api.applyCategorySuggestions).toHaveBeenCalled();
});

it("shows the empty state when there are no suggestions", () => {
  seed(); state.suggestions = [];
  render(<ProductsPage />);
  fireEvent.mouseDown(screen.getByRole("tab", { name: "Suggestions" }));
  expect(screen.getByText(/Run classification to generate suggestions/)).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bunx vitest run "app/(app)/products/products-page.test.tsx"`
Expected: FAIL (no Suggestions tab).

- [ ] **Step 3: Implement `SuggestionsPanel` + the tab**

- `suggestions-panel.tsx`: a presentational component taking `suggestions: CategorySuggestion[]`, `onUpdate(id, {name?, groupName?})`, `onDelete(id)`, `onAdd(name, groupName)`, `onApply()`, and `applying: boolean`. Renders one row per suggestion with inline `<Input>`s for name and group (blank = null group), a delete control, the `hint` as muted text; an "Add category" control; and an "Apply taxonomy" `Button` (disabled while `applying`). Empty → an `EmptyState` "Run classification to generate suggestions."
- `page.tsx`: add `"suggestions"` to the `Tab` union; `const suggestionsQuery = useApiData((api) => api.getCategorySuggestions())`; a `SuggestionsTab`-wrapping `TabsContent` that renders `<SuggestionsPanel …>` wired to `api` (update/delete/add call `suggestionsQuery.reload()`; apply calls `applyCategorySuggestions()` then `reloadAll()` + `suggestionsQuery.reload()` and switches to the Categories tab). Add a `TabsTrigger value="suggestions">Suggestions</TabsTrigger>`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && bunx vitest run "app/(app)/products/products-page.test.tsx"`
Expected: PASS (including the two new tests and the pre-existing suite).

- [ ] **Step 5: Run typecheck + lint**

Run: `cd frontend && bun run typecheck && bun run lint`
Expected: both pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/components/classification/suggestions-panel.tsx frontend/app/\(app\)/products/page.tsx frontend/app/\(app\)/products/products-page.test.tsx
git commit -m "feat(categorisation): Suggestions tab with review, improve, apply"
```

---

## Execution notes (not steps)

- The `ChatModel` mock pattern is `when(model.call(any(Prompt.class))).thenReturn(response)` — see `LlmInvoicePdfExtractorTest` and `ConversationServiceTest` for the exact `ChatResponse` stub.
- `OpenAiClusterNamer` mirrors `LlmInvoicePdfExtractor` almost line-for-line (ObjectProvider, temperature 0, fence-tolerant JSON parse, `log.warn` on failure) — use it as the reference implementation.
- `ProductClassificationService` gains three constructor args; both the `@Autowired` and the test-friendly constructor change in lockstep, and every construction in `ProductClassificationServiceTest` must be updated (Task 5).
- Tasks 9 and 10 are independent of 6–8 once the contract (Task 7) exists; parallelise if using subagent-driven development.
