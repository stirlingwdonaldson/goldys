# Goldy's Internal Product Categorisation

**Status:** Draft — for review
**Date:** 2026-10-13
**Scope:** A new, internal product taxonomy — a single category label per product with an
optional `group` parent, viewable flat or grouped — derived by ML classification over the
~650 distinct products, surfaced for browse/curate/report and visualised with React Flow.
The existing source categories are left untouched.

## 1. Objective

Lightspeed's product categories (`FAST BAR`, `FOOD MENU`, `COCKTAILS`, …) are too vague
to slice the business by. Give the platform a **clean, internal category dimension** for
products, so staff can browse, curate and report on products by a meaningful taxonomy —
without disturbing the source categories that already flow through
`category_name` (Lightspeed POS) and `category` (CTB stock).

This is a *derived, curatable* dimension, not a new source: products already exist as
canonical `product_name_key` strings across `canonical_product_sales`,
`canonical_sale_item`, `canonical_invoice_line`, `canonical_stock_count` and
`canonical_wastage`. No new connector, no write-back, no change to existing category
columns.

## 2. Current baseline

- **Product identity** is `product_name_key` (the `ProductNameKey.normalize` of a
  product/sale-item name), shared across the canonical tables above.
- **Old categories**: `CanonicalSaleItem.categoryName` (`Products POS Category Name`),
  `CanonicalInvoiceLine.category` (CTB stock category). Both remain as-is.
- **Resolved sales**: `resolved_product_sales` (`ResolvedProductSales`, keyed
  `product_name_key × trading_date`) carries per-product/day `quantity_sold`/`amount`.
  `semantic.ProductMetricsQuery.productSales(from, to)` reads it.
- **Recipe composition** (`V47`–`V49`): `canonical_sale_recipe_link` maps a
  `sale_item_stock_description` to a `recipe_id`/`recipe_name`/`recipe_folder_name`;
  `canonical_recipe_ingredient` adds ingredients. Recipe folder/name are the richest
  structured signal available about a product.
- **Runtime precedent**: `forecast-service/` (Python FastAPI) behind the
  `TimesFmClient` port is a documented second-runtime exception for PyTorch-only models.
- **React Flow** (`@xyflow/react` v12.8.6) is already a frontend dependency with a
  reusable read-only canvas in `frontend/components/flow/` (`FlowCanvas`,
  `layoutColumns`, `StepNode`, `ColumnHeading`, tone/status colour system).
- **LLM precedent**: the conversational AI already calls an external LLM via Spring AI
  `ChatClient`; a separate hosted API client is precedented.

## 3. Decisions (locked)

| Decision | Choice |
|---|---|
| Taxonomy source | **Hybrid**: cluster to discover groupings, human curates/names them, a classifier assigns every product |
| Surface | **Browse + curate + report**: a Products screen (curate/rename/merge/correct) plus a reporting dimension |
| Taxonomy shape | **One label + optional group** — viewable flat or as `group → category` |
| Discovery runtime | **Local embedding service** (Python FastAPI, mirrors `forecast-service`) |
| Discovery model | Agglomerative clustering → **dendrogram** |
| Assignment | **Jev** (`Choice` primitive, hosted TypeSafe API) behind a port |
| Scale | ~600–700 distinct products |
| Curation interaction | **Select-then-action** (no drag-to-reassign in v1) |

**Jev** is TypeSafe's "System One" decision model (`https://docs.typesafe.ai/`): it
evaluates typed questions against a *state* and returns structured answers — `Choice`
(pick from a list, with `probabilities` + `confidence`), `Score`, `Noul`. It never
generates text. Assignment is a `Choice` question per product (state = the product's
feature text, options = category names + descriptions); `confidence` drives a
"Needs review" queue.

## 4. Scope

### In scope

- Flyway `V53`/`V54`: taxonomy, assignment, run/artifact tables + permission seed.
- `product-classify-service/`: `POST /embed`, `POST /cluster`, `GET /health`.
- Java `classification` package: ports + adapters (`EmbeddingClient`,
  `ProductClassifier`/`JevProductClassifier`), taxonomy/assignment/run entities,
  `ProductClassificationService` (orchestrator), `ProductCategoryService` (curation).
- Read endpoints: taxonomy tree, dendrogram, embedding-space projection, and the
  assignment list for the Products screen.
- Reporting: `semantic.ProductCategoryMetricsQuery.salesByCategory(from, to)` +
  an Ask Goldy's tool + a dashboard widget.
- Frontend: Products screen (browse + curate) and the visualisation views (taxonomy
  tree, dendrogram, embedding scatter, pipeline flow).

### Out of scope (explicitly deferred)

- Write-back to Lightspeed/CTB (permanent invariant).
- Touching `category_name` / `category` columns.
- Multi-source *reconciliation* of categories (this is a single internal computation
  with manual overrides, not a cross-source conflict).
- Arbitrary-depth taxonomy (flat, or one optional group level, only).
- UMAP for the scatter (PCA first; UMAP as a follow-on if separation looks poor).
- Drag-to-reassign curation.

## 5. Schema (Flyway)

### `V53__product_categorisation.sql`

- **`product_category`** — the curated taxonomy (staff-authored, like `resolution_rule`).
  One category label per row; the parent group is optional so the list can be viewed flat
  (one-level) or grouped (two-level):
  `id uuid PK`, `name varchar(128) NOT NULL` (globally unique — the category label),
  `group_name varchar(128)` (nullable — `NULL` = a flat, top-level category),
  `display_order int NOT NULL DEFAULT 0`, `description varchar(512)`,
  `created_at timestamptz NOT NULL DEFAULT now()`,
  `updated_at timestamptz NOT NULL DEFAULT now()`, UNIQUE (`name`).

- **`product_category_assignment`** — the resolved product→category mapping (derived from
  Jev, overridable by staff):
  `product_name_key varchar(512) PK`,
  `product_category_id uuid NOT NULL REFERENCES product_category(id)`,
  `confidence numeric(6,4)`, `source varchar(16) NOT NULL`
  (`JEV` | `MANUAL` | `CLUSTERED`), `status varchar(16) NOT NULL`
  (`ASSIGNED` | `NEEDS_REVIEW` | `UNASSIGNED`),
  `assigned_at timestamptz NOT NULL`, `updated_at timestamptz NOT NULL`.
  Index on `product_category_id`; index on `status`.

- **`classification_run`** — observable job lifecycle (the "failures must be observable"
  invariant applied to an internal job):
  `id uuid PK`, `kind varchar(16) NOT NULL` (`CLUSTER` | `ASSIGN` | `FULL`),
  `status varchar(16) NOT NULL` (`RUNNING` | `SUCCEEDED` | `FAILED` | `PARTIAL`),
  `product_count int`, `started_at timestamptz NOT NULL`, `finished_at timestamptz`,
  `error_message text`.

- **`classification_cluster`** — disposable dendrogram artifact (leaves are products,
  internal nodes are merges):
  `id uuid PK`, `run_id uuid NOT NULL REFERENCES classification_run(id)`,
  `parent_id uuid NULL`, `is_leaf boolean NOT NULL`,
  `product_name_key varchar(512)` (null on internal nodes),
  `merge_height double precision`, `size int`. Index on `run_id`, `parent_id`.

- **`classification_projection`** — disposable 2-D coordinates for the scatter:
  `run_id uuid NOT NULL REFERENCES classification_run(id)`,
  `product_name_key varchar(512) NOT NULL`,
  `x double precision NOT NULL`, `y double precision NOT NULL`,
  PK (`run_id`, `product_name_key`).

### `V54__seed_product_categorisation_permissions.sql`

Seed `ALL × OWNER` for one resource, matching the `V6`/`V13`/`V23` convention:

```sql
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'products.category', true, true);
```

`products.category` READ covers viewing the taxonomy/assignments/visualisations and the
reporting dimension; WRITE covers curation (rename/merge/split categories, move products).

Embeddings are **not** persisted: they are recomputed on demand (seconds for ~650
strings) and the artifacts above are regenerated per run.

## 6. Packages & boundaries

New package **`com.goldys.platform.classification`** owns the whole feature. Dependencies:

- `classification` → `canonical` (feature extraction: `CanonicalProductSalesQuery`,
  `CanonicalSaleItemQuery`, `CanonicalSaleRecipeLinkQuery`) — precedented by
  `reconciliation`.
- `classification` → `semantic` (implements `ProductCategoryMetricsQuery`; consumes
  `ProductMetricsQuery` for the reporting join) — no cycle (`semantic` is a leaf).
- `classification` → `auth` (permission checks in the curation service).

It does **not** reach `reconciliation` internals: the reporting join reads
`resolved_product_sales` through the existing `semantic.ProductMetricsQuery`.

`ArchitectureBoundariesTest` additions:

1. `classification` may depend on `canonical`, `semantic`, `auth` (and JPA), and nothing
   else in-platform.
2. `semantic.ProductCategoryMetricsQuery` stays a leaf.
3. `api` and `reporting` never import `classification` (they consume `semantic` only).

The ML clients sit behind ports exactly as `TimesFmClient` does:

- `EmbeddingClient` (port) / `EmbeddingServiceHttpClient` (adapter) /
  `EmbeddingProperties` / `EmbeddingException` → calls `product-classify-service`.
- `ProductClassifier` (port) / `JevProductClassifier` (adapter) / `JevProperties` /
  `JevException` → calls the TypeSafe Jev API. A deterministic fake backs tests.

## 7. ML pipeline

`ProductClassificationService` orchestrates, writing `classification_run` + artifacts:

1. **Candidate set** — distinct current `product_name_key`s across
   `canonical_product_sales` and `canonical_sale_item`.
2. **Feature text** — per product, concatenate: the display name (the original,
   un-normalized product/sale-item name — never the folded `product_name_key`, so the
   embedder and Jev see readable text) + old POS category (where present) + CTB recipe
   folder/name (via `canonical_sale_recipe_link`, matched by `ProductNameKey.normalize`).
   This is the `state` handed to both the embedder and Jev.
3. **Embed** — `POST /embed` (local service) → 384-dim vectors.
4. **Cluster** — `POST /cluster` (vectors) → agglomerative merges (average linkage,
   cosine) and a 2-D **PCA** projection of the embeddings. The service returns a default
   cut (~20–40 clusters) plus the full merge tree; staff refine the cut in the dendrogram
   during curation. Stored as `classification_cluster` + `classification_projection` for
   the run.
5. **Curate** — staff name/merge/split clusters into `product_category` (see §9),
   optionally assigning a `group` to some categories.
6. **Assign** — per product, one Jev `Choice` question (options = category names +
   descriptions) → `choice` + `confidence`. Written to `product_category_assignment`:
   high confidence → `ASSIGNED` (`source=JEV`); low confidence / abstain →
   `NEEDS_REVIEW`; products with no category yet → `UNASSIGNED`.

### Python service contract (`product-classify-service/`)

Data-agnostic; knows nothing about Goldy's schema or permissions. Mirrors
`forecast-service/app.py` (swappable predictor, lazy model import, `/health` prewarm).

- `POST /embed` — `{ "texts": ["…"] }` → `{ "vectors": [[…384 floats…]] }`.
- `POST /cluster` — `{ "vectors": [[…]] }` → `{ "merges": [{ "left", "right",
  "height", "size" }], "labels": [int…], "projection": [{ "x", "y" }…] }`.
- `GET /health` → `{ "status": "ok", "model": "all-MiniLM-L6-v2" }`.

Embedding model: **`sentence-transformers/all-MiniLM-L6-v2`** (~80 MB, CPU-fast,
Apache-2.0, 384-dim). Clustering/PCA via `scikit-learn`/`numpy`. Jev is *not* called from
this service — the Java adapter calls TypeSafe directly.

## 8. Curation model

`ProductCategoryService` is the single write path, mirroring the reconciliation
override services (one `WRITE` check at the mutation boundary, `products.category`):

- `create/rename/merge/split` categories and groups (`product_category`).
- `move(productNameKey, categoryId)` — writes/updates an assignment with `source=MANUAL`
  and `status=ASSIGNED`; the manual row is the override and **survives** re-assignment.
- `clearOverride(productNameKey)` — reverts to the Jev-derived value.

`ProductClassificationService` re-assignment never overwrites `source=MANUAL` rows; it
only fills `UNASSIGNED`/`NEEDS_REVIEW` rows (and updates `ASSIGNED`/`JEV` rows).

## 9. Visualisation

React Flow is the primary surface (already a dependency; reuse `frontend/components/flow/`).

| View | What it shows | Library / layout |
|---|---|---|
| **Taxonomy tree** | `group → category` with product counts (flat, ungrouped categories sit at the top level); drill into a category → its products | React Flow, new `layoutTree` |
| **Dendrogram** | the merge tree + heights; preview "cut here", merge/split clusters | React Flow, new `layoutDendrogram` |
| **Embedding space** | 2-D PCA scatter, coloured by category; hover/select/lasso | Recharts `ScatterChart` |
| **Pipeline flow** | products → embed → cluster → curate → assign → report, with counts + status tones | existing `FlowCanvas`/`ColumnGraph` |

Two deterministic layout functions are added beside `layoutColumns`, returning the same
`FlowNode`/`FlowEdge` shapes so they reuse `StepNode`, `ColumnHeading` and the
tone/colour system:

- `layoutTree` — top-down tree (group → category → optional product leaves; ungrouped
  categories at the top).
- `layoutDendrogram` — leaves in a row, internal merge nodes at their merge height,
  straight-line edges.

Interaction model (v1 = **select-then-action**, no drag-to-reassign):

- Everywhere: zoom/pan/drag, hover tooltips, click-to-drill, keyboard-focusable nodes,
  accessible `role="region"` labels, empty/loading states.
- Dendrogram: select a merge node → preview the cut; select two sibling clusters →
  "Merge"; select a cluster → "Split".
- Scatter: hover → name + confidence; box/lasso-select → "Move to category…".
- Pipeline flow: click a stage → drill into its detail (e.g. failed embeddings).

Tone mapping: `NEEDS_REVIEW` → `warn` (`status-conflict`), `UNASSIGNED` → `missing`
(`status-missing`), assigned → `ok`/neutral — consistent with the platform's status
vocabulary. `FlowCanvas` is currently read-only; selection + context actions are a
deliberate extension of it (new selection model, `StepNode` variants for cluster/leaf).

### Read endpoints

- `GET /api/products/categories/taxonomy` → category tree (flat + grouped) + product counts.
- `GET /api/products/categories/dendrogram` → merge tree for the latest `CLUSTER`/`FULL`
  run.
- `GET /api/products/categories/embedding-space` → products + (x, y) + category + confidence.
- `GET /api/products/categories/assignments?status=…` → the Products screen table /
  "Needs review" queue.

## 10. Reporting

- `semantic.ProductCategoryMetricsQuery` (leaf): `salesByCategory(from, to)` →
  `List<CategorySales>` (`group` nullable, `category`, `quantity`, `amount`), joining
  `ProductMetricsQuery.productSales(from, to)` to `product_category_assignment` +
  `product_category`. Unassigned products group under an internal "Uncategorised"
  category (mirrors `SaleItemProjector`'s existing fallback). The flat view is a rollup by
  `category` alone; the grouped view rolls up by `group → category`.
- Ask Goldy's tool `get_sales_by_category` (resource `products.category`, READ) →
  a `table`/`bar-chart` widget. Follows the `add-reporting-tool` recipe.
- Dashboard widget: category-mix bar chart + table from the same semantic query.

## 11. Failure & edge handling

- Embedding/Jev service unavailable → the run marks `FAILED`/`PARTIAL` with
  `error_message`; previously persisted assignments remain readable; read paths degrade
  to "Uncategorised".
- New product appears → no assignment row until the next run (or manual assignment);
  surfacing: the Products screen shows `UNASSIGNED`.
- Jev mid-run failure → partial results persisted, run `PARTIAL`, retryable.
- No category yet defined (first run) → assignment step is skipped; everything sits in
  `UNASSIGNED` until curation produces `product_category` rows.

## 12. Testing

- **Backend**: `ProductCategoryServiceTest` (curation + manual override survives
  re-assignment), `ProductClassificationServiceTest` (fake `EmbeddingClient` + fake
  `ProductClassifier`; run lifecycle, partial failure), `ProductCategoryMetricsQueryTest`
  (join + Uncategorised fallback), `JevProductClassifierTest` (request/response mapping,
  confidence→status), `GetSalesByCategoryToolTest`, `ArchitectureBoundariesTest`
  additions, permission boundary test.
- **Python**: `test_app.py` — embed shape/determinism, cluster merge/projection shape,
  health.
- **Frontend**: `layoutTree`/`layoutDendrogram` unit tests, curation select-then-action,
  "Needs review" queue, taxonomy tree / scatter / pipeline-flow rendering, demo-mode
  fixtures for every new `Api` method.

## 13. Boundaries

### Always

- Old `category_name` / `category` columns untouched.
- Read direction preserved: final consumers read via `semantic`, never
  `classification` tables directly.
- The ML clients sit behind ports; the Python service stays data-agnostic.
- Manual assignment (`source=MANUAL`) is an override and survives re-assignment.
- Clustering/embedding artifacts are disposable and regenerable.

### Ask first

- Adding deeper nesting (a group-of-groups, i.e. a third level) or per-source category
  reconciliation.
- Changing the embedding model (the stored artifacts are keyed to a run, not a model,
  so a model swap is a re-run, not a migration — but confirm before changing defaults).
- Drag-to-reassign, UMAP, or editing products from the graph.

### Never

- Write back to Lightspeed/CTB.
- Mutate canonical rows from the classifier.
- Let `api`/`reporting` reach `classification` (only `semantic`).

## 14. Follow-on (gated)

- UMAP projection if PCA separation is poor.
- Drag-to-reassign curation.
- Automatic periodic re-assignment of `UNASSIGNED` products (currently on-demand/manual).
- Jev `Score`/`Noul` verification passes (deferred; `Choice` alone is v1).
