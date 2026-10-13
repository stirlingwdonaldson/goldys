# Product Categorisation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an internal product category dimension (one label per product with an optional `group` parent, viewable flat or grouped) — discovered by embedding + agglomerative clustering, curated by staff, assigned by Jev `Choice`, and surfaced for browse/curate/report with React Flow visualisations.

**Architecture:** A new Java package `com.goldys.platform.classification` owns the whole feature. It reads `canonical` (product names, sale items, recipe links) to build feature text — a deliberate deviation from the `forecasting` leaf, because categorisation is a *producer of resolved state* (like `reconciliation`), not a pure consumer. ML runs behind two ports: `EmbeddingClient` (a new `product-classify-service/` Python FastAPI runtime, mirroring `forecast-service`) and `ProductClassifier` (the hosted TypeSafe Jev API). `semantic.ProductCategoryMetricsQuery` is the leaf interface; `reporting`/`api` consume it via `application`.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL 16 (Testcontainers), Python 3.11 + FastAPI + sentence-transformers + scikit-learn, Next.js 15 / React 19 / TypeScript / Tailwind (Bun), `@xyflow/react` 12.8.6.

**Spec:** `docs/superpowers/specs/2026-10-13-product-categorisation-design.md`

## Global Constraints

- Schema changes are Flyway migrations only (`hibernate.ddl-auto: validate`); never edit an applied migration. Next versions are `V53` and `V54`.
- New tables are *not* bitemporal canonical: `product_category`/`product_category_assignment`/`classification_*` are curated/resolved state (like `resolution_rule`/`resolved_*`), so they do **not** extend `BitemporalEntity` and carry no `valid_from`/`superseded_at`.
- `semantic` is a leaf (no in-platform deps). `api` never reads `canonical`/raw. `reporting` depends only on `semantic` + `auth`.
- Permission resource is `products.category` (READ = view/report, WRITE = curate), seeded `ALL × OWNER` only.
- Money/quantity columns are `numeric(14,4)`. Embedding/confidence floats are `double precision` / `numeric(6,4)`.
- Old category columns (`category_name`, `category`) are never read for reporting and never written — the classification reads them *only* as a feature input.
- ML clients sit behind ports; the Python service is data-agnostic (knows nothing about Goldy's schema/permissions). Manual assignment (`source=MANUAL`) survives re-assignment.
- Build/checks: `cd backend && ./gradlew test spotlessCheck`; `cd frontend && bun run typecheck && bun run lint && bun run test`. Commit on `feature/product-categorisation`; never `main`.
- Frontend demo mode: every `Api` method gets a live + demo implementation.

## Review Focus

The five input classes the spec implies but no single task's happy-path test would otherwise catch:

1. **A product with no recipe link and no old category** → feature text is just the name; it still embeds and classifies, never throws. → Task 7 (feature extraction).
2. **A leaf rename that collides with an existing name** → `product_category.name` is globally unique; rename/merge must reject the collision (or merge), never silently create duplicates. → Task 6.
3. **Jev returns low confidence or abstains** → `NEEDS_REVIEW` (not a hard failure), surfaced in the assignments queue. → Task 7.
4. **A manual override must survive the Jev re-assignment pass** → re-assignment never overwrites `source=MANUAL`. → Task 7.
5. **A category with a `NULL` group (flat)** → renders top-level in the tree and rolls up directly in the flat report; the group→category join never emits a `NULL`-grouped row that breaks the frontend. → Task 8 / Task 13.
6. **First run with an empty taxonomy** → the assignment step is skipped, every product is `UNASSIGNED`, nothing throws. → Task 7.

---

### Task 1: Flyway migrations

**Files:**
- Create: `backend/src/main/resources/db/migration/V53__product_categorisation.sql`
- Create: `backend/src/main/resources/db/migration/V54__seed_product_categorisation_permissions.sql`

**Interfaces:**
- Produces: `product_category`, `product_category_assignment`, `classification_run`, `classification_cluster`, `classification_projection` tables; `products.category` permission row. Every later task reads these.

- [ ] **Step 1: Write `V53__product_categorisation.sql`**

Five tables (match `V47__recipe_composition.sql` DDL style — `uuid PK`, `timestamp(6) with time zone`, `numeric(14,4)`):

`product_category` — `id uuid PK`, `name varchar(128) NOT NULL`, `group_name varchar(128)` (NULL = flat), `display_order int NOT NULL DEFAULT 0`, `description varchar(512)`, `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()`, plus `CONSTRAINT uq_product_category_name UNIQUE (name)`.

`product_category_assignment` — `product_name_key varchar(512) PRIMARY KEY`, `product_category_id uuid NOT NULL REFERENCES product_category(id)`, `confidence numeric(6,4)`, `source varchar(16) NOT NULL`, `status varchar(16) NOT NULL`, `assigned_at timestamptz NOT NULL`, `updated_at timestamptz NOT NULL`; `CREATE INDEX ix_assignment_category ON product_category_assignment (product_category_id)` and `ix_assignment_status ON product_category_assignment (status)`.

`classification_run` — `id uuid PK`, `kind varchar(16) NOT NULL`, `status varchar(16) NOT NULL`, `product_count int`, `started_at timestamptz NOT NULL`, `finished_at timestamptz`, `error_message text`.

`classification_cluster` — `id uuid PK`, `run_id uuid NOT NULL REFERENCES classification_run(id)`, `parent_id uuid`, `is_leaf boolean NOT NULL`, `product_name_key varchar(512)` (NULL on internal nodes), `merge_height double precision`, `size int`; index on `run_id`, `parent_id`.

`classification_projection` — `run_id uuid NOT NULL REFERENCES classification_run(id)`, `product_name_key varchar(512) NOT NULL`, `x double precision NOT NULL`, `y double precision NOT NULL`, `PRIMARY KEY (run_id, product_name_key)`.

- [ ] **Step 2: Write `V54__seed_product_categorisation_permissions.sql`**

```sql
-- Seed owner access to the internal product categorisation (browse/curate/report), matching
-- the V6/V13/V23 convention (ALL x OWNER). BOH/FOH granular grants stay deferred.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'products.category', true, true);
```

- [ ] **Step 3: Verify migrations apply and Hibernate validates**

Run: `cd backend && ./gradlew compileJava`
Expected: build succeeds. (Migrations are exercised end-to-end by the Task 9 integration test.)

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V53__product_categorisation.sql backend/src/main/resources/db/migration/V54__seed_product_categorisation_permissions.sql
git commit -m "feat(categorisation): Flyway migrations for product categorisation + products.category permission"
```

---

### Task 2: Semantic interface + records

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/ProductCategoryMetricsQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/CategorySales.java`

**Interfaces:**
- Produces (used by Tasks 8, 9, 10):
  - `public interface ProductCategoryMetricsQuery { List<CategorySales> salesByCategory(LocalDate from, LocalDate to); }`
  - `public record CategorySales(String group, String category, BigDecimal quantity, BigDecimal amount)` — `group` is nullable (flat categories).

- [ ] **Step 1: Write the files**

`ProductCategoryMetricsQuery` is a leaf interface (semantic package); `CategorySales` a plain record. No test needed — compilation is the check (Task 11 asserts it stays a leaf).

- [ ] **Step 2: Verify compile**

Run: `cd backend && ./gradlew compileJava`
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/semantic/ProductCategoryMetricsQuery.java backend/src/main/java/com/goldys/platform/semantic/CategorySales.java
git commit -m "feat(categorisation): semantic ProductCategoryMetricsQuery + CategorySales"
```

---

### Task 3: classification entities, enums and repositories

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductCategory.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductCategoryAssignment.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClassificationRun.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClassificationCluster.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClassificationProjection.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/AssignmentSource.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/AssignmentStatus.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClassificationRunKind.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClassificationRunStatus.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductCategoryRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductCategoryAssignmentRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClassificationRunRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClassificationClusterRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClassificationProjectionRepository.java`

**Interfaces:**
- Produces (used by Tasks 6, 7, 8):
  - `ProductCategory`: `static ProductCategory create(String name, String groupName, int displayOrder, String description)`; accessors `id()`, `name()`, `groupName()`, `displayOrder()`, `description()`; `rename(String newName)`, `regroup(String newGroupName)`.
  - `ProductCategoryAssignment`: `static ProductCategoryAssignment create(String productNameKey, Instant now)`; accessors `productNameKey()`, `categoryId()`, `confidence()`, `source()`, `status()`, `assignedAt()`, `updatedAt()`; `markAssigned(UUID categoryId, BigDecimal confidence, AssignmentSource source, Instant now)`, `markNeedsReview(BigDecimal confidence, Instant now)`, `markUnassigned(Instant now)`.
  - `ClassificationRun`: `static ClassificationRun create(ClassificationRunKind kind, Instant now)`; accessors `id()`, `kind()`, `status()`, `productCount()`, `startedAt()`, `finishedAt()`, `errorMessage()`; `markRunning()`, `markSucceeded(int productCount, Instant now)`, `markFailed(String message, Instant now)`, `markPartial(String message, Instant now)`.
  - `ClassificationCluster` (fields per Task 1 table); `ClassificationProjection` with `@IdClass` on `(runId, productNameKey)`.
  - Repositories (package-private, `JpaRepository`): `ProductCategoryRepository` adds `Optional<ProductCategory> findByName(String name)` and `List<ProductCategory> findAllByOrderByGroupNameAscDisplayOrderAscNameAsc()`; `ProductCategoryAssignmentRepository` adds `long countByStatus(AssignmentStatus status)`; `ClassificationRunRepository` adds `List<ClassificationRun> findAllByOrderByStartedAtDesc()` and `Optional<ClassificationRun> findTopByKindOrderByStartedAtDesc(ClassificationRunKind kind)`; `ClassificationClusterRepository` adds `List<ClassificationCluster> findByRunIdOrderByIdAsc(UUID runId)`.

- [ ] **Step 1: Write the failing repository test**

`ProductCategoryAssignmentRepositoryTest` is covered end-to-end by Task 6/7 (no standalone repo test needed for plain JPA). Instead, write `ProductCategoryTest` (pure JUnit) asserting `rename` updates `name`, `regroup` sets/clears `groupName`, and `create` sets `displayOrder`.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ProductCategoryTest'` → FAIL (compile).

- [ ] **Step 3: Implement the entities, enums and repositories**

Plain JPA `@Entity`/`@Table` matching Task 1 columns exactly (Hibernate `validate`). Enums use `@Enumerated(EnumType.STRING)`. `ProductCategoryAssignment` uses `@Id String productNameKey` (no surrogate). Mirror the package-private style of `ResolvedProductSales`.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification backend/src/test/java/com/goldys/platform/classification
git commit -m "feat(categorisation): classification entities, enums and repositories"
```

---

### Task 4: ML ports, value records, properties, exceptions

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/EmbeddingClient.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductClassifier.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClusterResult.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ClusterNode.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/Point2d.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductClassificationRequest.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/CategoryOption.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductClassification.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/EmbeddingProperties.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/JevProperties.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/EmbeddingException.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/JevException.java`

**Interfaces:**
- Produces (used by Tasks 5, 7):
  - `public interface EmbeddingClient { List<float[]> embed(List<String> texts); ClusterResult cluster(List<float[]> vectors); }`
  - `public interface ProductClassifier { ProductClassification classify(ProductClassificationRequest request); }`
  - `public record ClusterResult(List<ClusterNode> nodes, List<Integer> labels, List<Point2d> projection)`
  - `public record ClusterNode(int id, Integer parentId, boolean isLeaf, Integer productIndex, double height, int size)`
  - `public record Point2d(double x, double y)`
  - `public record ProductClassificationRequest(String featureText, List<CategoryOption> options)`
  - `public record CategoryOption(UUID categoryId, String name, String description)`
  - `public record ProductClassification(UUID categoryId, Map<UUID, BigDecimal> probabilities, BigDecimal confidence)`
  - `@ConfigurationProperties(prefix = "classification.embedding") public record EmbeddingProperties(boolean enabled, String baseUrl)`
  - `@ConfigurationProperties(prefix = "classification.jev") public record JevProperties(boolean enabled, String baseUrl, String apiKey)`
  - `EmbeddingException` / `JevException` extend `RuntimeException` (both `(String)` and `(String, Throwable)` ctors, mirroring `TimesFmException`).

- [ ] **Step 1: Write the files**

Ports + records + properties + exceptions only. No test (compilation + Task 5/7 fake use is the check).

- [ ] **Step 2: Verify compile** — `cd backend && ./gradlew compileJava` → PASS.

- [ ] **Step 3: Add config keys to `backend/src/main/resources/application.yml`** (after the `forecasting` block, line ~149):

```yaml
classification:
  embedding:
    enabled: ${CLASSIFICATION_EMBEDDING_ENABLED:false}
    base-url: ${CLASSIFICATION_EMBEDDING_BASE_URL:http://localhost:8002}
  jev:
    enabled: ${CLASSIFICATION_JEV_ENABLED:false}
    base-url: ${CLASSIFICATION_JEV_BASE_URL:https://api.typesafe.ai}
    api-key: ${CLASSIFICATION_JEV_API_KEY:}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification backend/src/main/resources/application.yml
git commit -m "feat(categorisation): embedding + classifier ports, records, properties, exceptions"
```

---

### Task 5: HTTP adapters (embedding service + Jev)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/EmbeddingServiceHttpClient.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/JevProductClassifier.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/JevProductClassifierTest.java`

**Interfaces:**
- Consumes: `EmbeddingClient`, `ProductClassifier`, the records + properties from Task 4.
- Produces: Spring `@Component` adapters. `EmbeddingServiceHttpClient` POSTs `{texts:[…]}` → `{vectors:[[…]]}` and `{vectors:[[…]]}` → `{nodes:[…], labels:[…], projection:[…]}` against `{baseUrl}/embed` and `{baseUrl}/cluster`; `JevProductClassifier` POSTs the Jev `Choice` request against the TypeSafe API.

- [ ] **Step 1: Write the failing Jev adapter test**

`JevProductClassifierTest` (mock the JDK `HttpClient`/`ObjectMapper` as `TimesFmHttpClient` does — construct the adapter with a `JevProperties` and a stub HTTP transport):
- `maps_choice_to_classification` — a JSON body `{"choice": "<categoryId>", "probabilities": {"<a>":0.8,"<b>":0.2}, "confidence":0.81}` maps to `ProductClassification` with the right `categoryId`, `probabilities` (by `UUID`), and `confidence`.
- `non_2xx_throws_jev_exception` — a 500 → `JevException` with an operator-facing message (never the body).

> Note: the exact TypeSafe Jev `Choice` HTTP request/response schema must be confirmed against `https://docs.typesafe.ai/primitives/choice` during this task. The Java port and `ProductClassification` value type are fixed regardless; only the adapter's JSON mapping follows the docs.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*JevProductClassifierTest'` → FAIL (compile).

- [ ] **Step 3: Implement `EmbeddingServiceHttpClient` and `JevProductClassifier`**

Mirror `TimesFmHttpClient`: `@Component`, constructor takes properties + shared `ObjectMapper`, JDK `HttpClient` pinned to HTTP/1.1, `POST` JSON, non-2xx / IO / unparseable → `EmbeddingException` / `JevException`. `EmbeddingServiceHttpClient.cluster` maps the `nodes` array (with `parentId` null → `null`, `productIndex` null → `null`) into `List<ClusterNode>`.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification/EmbeddingServiceHttpClient.java backend/src/main/java/com/goldys/platform/classification/JevProductClassifier.java backend/src/test/java/com/goldys/platform/classification/JevProductClassifierTest.java
git commit -m "feat(categorisation): embedding-service and Jev HTTP adapters"
```

---

### Task 6: ProductCategoryService (curation)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductCategoryService.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/ProductCategoryServiceTest.java`

**Interfaces:**
- Consumes: `ProductCategoryRepository`, `ProductCategoryAssignmentRepository`, `PermissionService` (Task 3 + auth).
- Produces (used by Tasks 7, 9):
  - `ProductCategory create(String name, String groupName, int displayOrder, String description)`
  - `void rename(UUID id, String name)`
  - `void regroup(UUID id, String groupName)`
  - `void move(String productNameKey, UUID categoryId)` — sets `source=MANUAL`, `status=ASSIGNED`
  - `void clearOverride(String productNameKey)` — deletes the manual row (or resets to `UNASSIGNED`)
  - Every method calls `permissions.require(role, new ResourceKey("products.category"), WRITE)`; each takes a `UserRole role` first argument.

- [ ] **Step 1: Write the failing service test**

`ProductCategoryServiceTest` (construct with fakes for both repositories + a fake `PermissionService`):
- `create_persists_category` — `create("Cocktails", "Drinks", 0, null)` → a `ProductCategory` with matching fields.
- `rename_rejects_duplicate_name` — after `create("Draft Beer", "Drinks", …)`, `rename(id2, "Draft Beer")` throws (global-unique `name`); use a fake repository that throws on duplicate `name`.
- `regroup_clears_group` — `regroup(id, null)` → `groupName()` is null (flat).
- `move_marks_manual_and_assigned` — `move("kids-fish", catId)` → assignment has `source=MANUAL`, `status=ASSIGNED`, `categoryId=catId`.
- `requires_write_permission` — a `PermissionService` that throws → the exception propagates (assert the `require` call recorded `WRITE` on `products.category`).

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ProductCategoryServiceTest'` → FAIL (compile).

- [ ] **Step 3: Implement `ProductCategoryService`**

`@Service`; one `WRITE` check at the top of each mutation (mirror `ResolutionRuleService`'s placement). `move` looks up the assignment by `productNameKey` and calls `markAssigned(categoryId, null, MANUAL, now)` (creating it if absent).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification/ProductCategoryService.java backend/src/test/java/com/goldys/platform/classification/ProductCategoryServiceTest.java
git commit -m "feat(categorisation): product category curation service"
```

---

### Task 7: ProductClassificationService (embed → cluster → assign orchestration)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductClassificationService.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/FeatureTextExtractor.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/ProductClassificationServiceTest.java`

**Interfaces:**
- Consumes: `CanonicalProductSalesQuery`, `CanonicalSaleItemQuery`, `CanonicalSaleRecipeLinkQuery` (canonical); `ProductCategoryRepository`, `ProductCategoryAssignmentRepository`, `ClassificationRunRepository`, `ClassificationClusterRepository`, `ClassificationProjectionRepository`; `EmbeddingClient`, `ProductClassifier`; `@Qualifier("applicationTaskExecutor") TaskExecutor`.
- Produces (used by Tasks 8, 9):
  - `public void runClustering()` — build feature texts from canonical, `embed`, `cluster`, persist `classification_cluster` + `classification_projection`, mark run `SUCCEEDED`.
  - `public void runAssignment()` — for each current `product_name_key` (skipping `source=MANUAL`), one `ProductClassifier.classify(...)` → `markAssigned`/`markNeedsReview`; empty taxonomy → all `markUnassigned`, run `SUCCEEDED` with `productCount` only.
  - `public void runFull()` — clustering then assignment in one run (`kind=FULL`).
  - `FeatureTextExtractor`: `Map<String,String> featureTexts()` — key = `product_name_key`, value = display name + old POS category + recipe folder/name, using `ProductNameKey.normalize` to join recipe links to sale items. A product with no link/category yields value = name only (Review Focus #1).

- [ ] **Step 1: Write the failing service test**

`ProductClassificationServiceTest` (construct with fakes: canonical queries returning a fixed product set, fakes for `EmbeddingClient`/`ProductClassifier`, and recording fake repositories):
- `clustering_persists_nodes_and_projection` — `runClustering()` writes `classification_cluster` leaves (one per product) + one `classification_projection` row per product, and a `SUCCEEDED` run.
- `feature_text_falls_back_to_name_only` — a `FeatureTextExtractor` given a `product_name_key` with no matching sale-item row and no recipe link returns a non-empty value containing the display name only (Review Focus #1).
- `assignment_skips_manual_overrides` — a product with a pre-existing `source=MANUAL` assignment is not passed to the fake `ProductClassifier` (assert the fake saw no request for it), while `UNASSIGNED`/`JEV` products are (Review Focus #4).
- `low_confidence_marks_needs_review` — a fake classifier returning `confidence=0.2` → `markNeedsReview` (Review Focus #3).
- `empty_taxonomy_marks_all_unassigned` — no `product_category` rows → `ProductClassifier` never called, all products `UNASSIGNED`, run `SUCCEEDED` (Review Focus #6).
- `classifier_failure_marks_run_failed` — the fake `ProductClassifier` throws → run `FAILED` with `errorMessage`, previously-written assignments untouched.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ProductClassificationServiceTest'` → FAIL (compile).

- [ ] **Step 3: Implement `FeatureTextExtractor` + `ProductClassificationService`**

`FeatureTextExtractor` joins `CanonicalSaleRecipeLinkQuery.currentLinks()` (map `ProductNameKey.normalize(saleItemStockDescription)` → `recipeFolderName`/`recipeName`) and `CanonicalSaleItemQuery` (name + `categoryName`) onto `CanonicalProductSalesQuery.distinctProductNameKeys()`. The display name is the original sale-item/product name, not the folded key. `ProductClassificationService` mirrors `ForecastService`: `submit`-style `runX()` persist a `RUNNING` run then `executor.execute(() -> execute(...))`; `execute` marks `RUNNING`, calls the ports, writes artifacts/assignments, then `markSucceeded`/`markPartial`/`markFailed` (only trust `EmbeddingException`/`JevException` messages).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification/ProductClassificationService.java backend/src/main/java/com/goldys/platform/classification/FeatureTextExtractor.java backend/src/test/java/com/goldys/platform/classification/ProductClassificationServiceTest.java
git commit -m "feat(categorisation): classification orchestration (embed -> cluster -> assign)"
```

---

### Task 8: Read facades + semantic implementation

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductCategoryQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/CategoryNode.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/DendrogramNode.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ProductPoint.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/AssignmentView.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ResolvedProductCategoryQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/ResolvedProductCategoryQueryTest.java`

**Interfaces:**
- Consumes: `ProductCategoryRepository`, `ProductCategoryAssignmentRepository`, `ClassificationRunRepository`, `ClassificationClusterRepository`, `ClassificationProjectionRepository` (Task 3); `ProductMetricsQuery` (semantic, for the reporting join).
- Produces (used by Tasks 9, 10):
  - `public record CategoryNode(UUID id, String name, String groupName, int productCount)`
  - `public record DendrogramNode(UUID id, UUID parentId, boolean isLeaf, String productNameKey, double mergeHeight, int size)`
  - `public record ProductPoint(String productNameKey, double x, double y, UUID categoryId, BigDecimal confidence)`
  - `public record AssignmentView(String productNameKey, UUID categoryId, String categoryName, String groupName, BigDecimal confidence, String source, String status)`
  - `ProductCategoryQuery`: `List<CategoryNode> taxonomy()`, `List<DendrogramNode> dendrogram()`, `List<ProductPoint> embeddingSpace()`, `List<AssignmentView> assignments(AssignmentStatus status)` (null = all).
  - `ResolvedProductCategoryQuery implements ProductCategoryMetricsQuery` — `salesByCategory(from,to)` joins `ProductMetricsQuery.productSales(from,to)` (grouped by `productName`) to `product_category_assignment` + `product_category`, emitting `CategorySales(group, category, quantity, amount)` with the "Uncategorised" category for unmatched products.

- [ ] **Step 1: Write the failing query test**

`ResolvedProductCategoryQueryTest` (construct with fakes for `ProductMetricsQuery` + the repositories):
- `joins_sales_to_category` — one `ProductSalesMetric` ("Kids Fish", qty 2, amount 30) + an assignment to category "Mains"/group "Food" → one `CategorySales("Food","Mains",2,30)`.
- `unassigned_products_roll_up_to_uncategorised` — a product with no assignment → `CategorySales(null,"Uncategorised",…)`.
- `flat_category_has_null_group` — an assignment to a category with `groupName=null` → `CategorySales(null,"Cocktails",…)` (Review Focus #5).
- `groups_by_category_and_sums` — two products in the same leaf sum quantity/amount into one `CategorySales`.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ResolvedProductCategoryQueryTest'` → FAIL (compile).

- [ ] **Step 3: Implement `ProductCategoryQuery` + `ResolvedProductCategoryQuery`**

`ProductCategoryQuery` reads the repositories and maps to the records (`taxonomy` groups counts per category; `dendrogram` maps `classification_cluster` from the latest `CLUSTER`/`FULL` run; `embeddingSpace` joins `classification_projection` to the latest assignment). `ResolvedProductCategoryQuery` does the join in memory (pub scale); use `BigDecimal` sums.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification backend/src/test/java/com/goldys/platform/classification/ResolvedProductCategoryQueryTest.java
git commit -m "feat(categorisation): read facades + semantic sales-by-category implementation"
```

---

### Task 9: Application service + REST controller

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/application/ProductCategoryReportingService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/ProductCategoryController.java`
- Test: `backend/src/test/java/com/goldys/platform/application/ProductCategoryReportingServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ProductCategoryControllerTest.java`

**Interfaces:**
- Consumes: `ProductCategoryQuery`, `ProductCategoryMetricsQuery`, `ProductCategoryService` (classification); `PermissionService`, `CurrentUserService` (auth).
- Produces:
  - `ProductCategoryReportingService`: `List<CategorySales> salesByCategory(UserRole role, LocalDate from, LocalDate to)`, `List<CategoryNode> taxonomy(UserRole role)`, `List<DendrogramNode> dendrogram(UserRole role)`, `List<ProductPoint> embeddingSpace(UserRole role)`, `List<AssignmentView> assignments(UserRole role, AssignmentStatus status)`; each calls `permissions.require(role, new ResourceKey("products.category"), READ)` first.
  - `GET /api/products/categories/taxonomy`, `.../dendrogram`, `.../embedding-space`, `.../assignments?status=`, and curation `POST .../categories` (create), `PATCH .../categories/{id}` (rename/regroup), `POST .../assignments/{productNameKey}/move` (move), `DELETE .../assignments/{productNameKey}/override` (clearOverride).

- [ ] **Step 1: Write the failing service test**

`ProductCategoryReportingServiceTest`: `authorizes_and_returns_sales` — a fake `PermissionService` (record the `require`) + fake `ProductCategoryMetricsQuery`; `salesByCategory(role, from, to)` calls `require(role, products.category, READ)` and returns the query list. `denies_without_permission` — a `PermissionService` that throws → propagates.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ProductCategoryReportingServiceTest'` → FAIL (compile).

- [ ] **Step 3: Implement `ProductCategoryReportingService`**

Copy `RecipeReportingService` shape (resource `new ResourceKey("products.category")`).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Write the failing controller test**

`ProductCategoryControllerTest` (plain controller with fakes for the service + `CurrentUserService`): `GET /api/products/categories/taxonomy` returns `200` + the service's list; verify the controller never imports `canonical`/`classification`.

- [ ] **Step 6: Run test to verify it fails** — FAIL (compile).

- [ ] **Step 7: Implement `ProductCategoryController`**

`@RestController @RequestMapping("/api/products/categories")`, methods delegating to `reporting.<method>(currentUser.roleOf(user), …)` (read) or `categoryService.<method>(currentUser.roleOf(user), …)` (curation). Copy `RecipeController`'s shape; keep DTO mapping minimal (the records serialize directly).

- [ ] **Step 8: Run test to verify it passes** — PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/application/ProductCategoryReportingService.java backend/src/main/java/com/goldys/platform/api/ProductCategoryController.java backend/src/test/java/com/goldys/platform/application backend/src/test/java/com/goldys/platform/api
git commit -m "feat(categorisation): reporting service + REST controller"
```

---

### Task 10: Ask Goldy's tool

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` (add `GET_SALES_BY_CATEGORY`)
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetSalesByCategoryInput.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetSalesByCategoryTool.java`
- Test: `backend/src/test/java/com/goldys/platform/reporting/GetSalesByCategoryToolTest.java`

**Interfaces:**
- Consumes: `ProductCategoryMetricsQuery` (semantic), `Column`, `BarChartWidgetSpec`/`TableWidgetSpec` (widget).
- Produces: `ToolResult` whose widget is a `TableWidgetSpec` with columns `group`, `category`, `quantity`, `amount`.

- [ ] **Step 1: Write the failing tool test**

`GetSalesByCategoryToolTest`: `returns_table_widget` — a fake `ProductCategoryMetricsQuery` returning one `CategorySales("Food","Burgers",10,150)` → `execute(new GetSalesByCategoryInput(start, end), role)` returns a `ToolResult` with a `TableWidgetSpec`; assert `columns` keys are exactly `group, category, quantity, amount` and the row carries the right values. `resource_is_products_category` — `resource().value().equals("products.category")`. `to_metric_queries_is_empty` — `toMetricQueries(...)` returns `List.of()`.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*GetSalesByCategoryToolTest'` → FAIL (compile).

- [ ] **Step 3: Implement**

`public record GetSalesByCategoryInput(LocalDate startDate, LocalDate endDate) implements ToolInput`. `GetSalesByCategoryTool implements ReportingTool` — `id()=GET_SALES_BY_CATEGORY`, `name()="get_sales_by_category"`, `description()="Sales totals grouped by the internal product category."`, `resource()=new ResourceKey("products.category")`; `execute` reads `ProductCategoryMetricsQuery.salesByCategory(from,to)` and builds the table (copy `GetRecipeCostingTool`'s shape; `amount` format `"currency"`).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reporting backend/src/test/java/com/goldys/platform/reporting/GetSalesByCategoryToolTest.java
git commit -m "feat(categorisation): Ask Goldy's get_sales_by_category tool"
```

---

### Task 11: Architecture + permission boundary tests

**Files:**
- Modify: `backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java`
- Create: `backend/src/test/java/com/goldys/platform/classification/ProductCategoryPermissionBoundaryTest.java`

**Interfaces:**
- Consumes: the packages created in Tasks 2–10.

- [ ] **Step 1: Write the failing boundary assertions**

Extend `ArchitectureBoundariesTest`:
- `classificationDoesNotReachHigherPackages` — `noClasses().that().resideInAPackage("..classification..").should().dependOnClassesThat().resideInAnyPackage("..api..","..application..","..reporting..","..conversational..","..reconciliation..","..ingestion..","..connectors..","..dashboard..","..widget..","..forecasting..","..config..","..metrics..")`.
- `apiDoesNotReachClassification` — `noClasses().that().resideInAPackage("..api..").should().dependOnClassesThat().resideInAPackage("..classification..")`.
- `reportingDoesNotReachClassification` — `noClasses().that().resideInAPackage("..reporting..").should().dependOnClassesThat().resideInAPackage("..classification..")`.
- `productCategoryMetricsImplementedInClassification` — `classes().that().implement(ProductCategoryMetricsQuery.class).should().resideInAPackage("..classification..")`.
- Add `..classification..` to the existing `semanticIsALeaf` deny list is already covered (semantic denies all in-platform deps).

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ArchitectureBoundariesTest'` → FAIL (rules absent/violated).

- [ ] **Step 3: Implement** — add the rules (copy the `forecasting`/`recipe` rule blocks, ArchUnit DSL already present).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Write the failing permission boundary test**

`ProductCategoryPermissionBoundaryTest` (Testcontainers, mirror `PermissionBoundaryIntegrationTest`): a user with `ALL×OWNER` reads `GET /api/products/categories/taxonomy` successfully; a user with no `products.category` grant gets `403`/denied.

- [ ] **Step 6: Run test to verify it passes** — PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java backend/src/test/java/com/goldys/platform/classification/ProductCategoryPermissionBoundaryTest.java
git commit -m "test(categorisation): architecture + permission boundary coverage"
```

---

### Task 12: Python classification service

**Files:**
- Create: `product-classify-service/app.py`
- Create: `product-classify-service/predictor.py`
- Create: `product-classify-service/test_app.py`
- Create: `product-classify-service/requirements.txt`
- Create: `product-classify-service/Dockerfile`

**Interfaces:**
- Consumes: the `POST /embed` and `POST /cluster` contracts from Task 5.
- Produces:
  - `POST /embed` `{ "texts": ["…"] }` → `{ "vectors": [[…384 floats…]] }`.
  - `POST /cluster` `{ "vectors": [[…]] }` → `{ "nodes": [{ "id", "parentId", "isLeaf", "productIndex", "height", "size" }], "labels": [int…], "projection": [{ "x", "y" }…] }`.
  - `GET /health` → `{ "status": "ok", "model": "all-MiniLM-L6-v2" }`.

- [ ] **Step 1: Write `predictor.py`**

`Predictor` protocol with `embed(texts: list[str]) -> list[list[float]]` and `cluster(vectors: list[list[float]]) -> dict`; `EmbeddingPredictor` implements it: lazy `SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")`, `sklearn.cluster.AgglomerativeClustering` (average linkage, cosine, `distance_threshold`) for the dendrogram + a default flat cut (~20–40 clusters), and `sklearn.decomposition.PCA(n_components=2)` for the projection. `cluster` returns the `nodes` list with `id`/`parentId`/`isLeaf`/`productIndex`/`height`/`size` (leaves = products 0..n-1; internal nodes = merges), `labels` (cluster id per product at the cut), and `projection`.

- [ ] **Step 2: Write `app.py`**

Mirror `forecast-service/app.py`: Pydantic `EmbedRequest`/`ClusterRequest`/`ClusterResponse`, `@app.get("/health")`, `@app.post("/embed")`, `@app.post("/cluster")`, module-level `predictor: Predictor = EmbeddingPredictor()` swapped for a fake in tests, `_prewarm_enabled()`/`lifespan` prewarm on `CLASSIFY_PREWARM`.

- [ ] **Step 3: Write `test_app.py`**

`FakePredictor` with `embed`/`cluster`; a fixture swapping `app_module.predictor`; tests: `embed_returns_vectors`, `cluster_returns_nodes_labels_projection`, `health_reports_model`, and `embed_empty_texts` (empty list → `[]`).

- [ ] **Step 4: Write `requirements.txt` and `Dockerfile`**

`requirements.txt`: `fastapi`, `uvicorn[standard]`, `pydantic`, `numpy`, `sentence-transformers`, `scikit-learn`, plus test-only `httpx`, `pytest`. `Dockerfile`: copy `forecast-service/Dockerfile` shape (Python 3.11-slim, CPU torch/sentence-transformers, `EXPOSE 8002`, `CMD uvicorn app:app --port 8002`), drop the torch-from-index line if not needed.

- [ ] **Step 5: Run the Python tests**

Run: `cd product-classify-service && python -m pytest -q`
Expected: PASS (uses the fake predictor; no model download).

- [ ] **Step 6: Commit**

```bash
git add product-classify-service
git commit -m "feat(categorisation): Python embedding + clustering service"
```

---

### Task 13: Frontend Api contract

**Files:**
- Modify: `frontend/lib/api/types.ts` (add types + Api methods)
- Modify: `frontend/lib/api/index.ts` (re-export types)
- Modify: `frontend/lib/api/live.ts` (add live methods)
- Modify: `frontend/lib/api/demo.ts` (add demo fixtures)
- Test: `frontend/lib/api/demo.test.ts` (extend, if a categorisation case fits)

**Interfaces:**
- Produces (used by Tasks 14, 15):
  - Types: `CategoryNode { id, name, groupName, productCount }`, `DendrogramNode { id, parentId, isLeaf, productNameKey, mergeHeight, size }`, `ProductPoint { productNameKey, x, y, categoryId, confidence }`, `AssignmentRow { productNameKey, categoryId, categoryName, groupName, confidence, source, status }`, `CategorySales { group, category, quantity, amount }`.
  - `Api` additions: `getCategoryTaxonomy(): Promise<CategoryNode[]>`, `getCategoryDendrogram(): Promise<DendrogramNode[]>`, `getCategoryEmbeddingSpace(): Promise<ProductPoint[]>`, `getCategoryAssignments(status?: string): Promise<AssignmentRow[]>`, `getSalesByCategory(from: string, to: string): Promise<CategorySales[]>`, plus curation `renameCategory`, `moveProduct`, `clearProductOverride`.

- [ ] **Step 1: Write the failing demo test**

`demo.test.ts`: `getCategoryTaxonomy` returns fixtures including a flat category (`groupName: null`) — assert the flat node is present with a null `groupName` (Review Focus #5).

- [ ] **Step 2: Run test to verify it fails** — `cd frontend && bun run test -- demo` → FAIL.

- [ ] **Step 3: Implement the contract**

`types.ts`: add the interfaces + `Api` methods (mirror `RecipeCosting` style). `live.ts`: `getCategoryTaxonomy: () => fetchApi<CategoryNode[]>("/api/products/categories/taxonomy")`, etc.; curation methods `fetchApi` POST/PATCH/DELETE. `demo.ts`: `async` methods `await delay(300)` returning 4–6 fixtures (flat + grouped categories, a `NEEDS_REVIEW` assignment, a couple of points in the scatter). `index.ts`: add the new types to the `export type { … }` block.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Run typecheck** — `cd frontend && bun run typecheck` → pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/api
git commit -m "feat(categorisation): frontend Api contract for product categorisation"
```

---

### Task 14: React Flow tree + dendrogram layouts

**Files:**
- Modify: `frontend/components/flow/layout.ts` (add `layoutTree`, `layoutDendrogram`)
- Test: `frontend/components/flow/layout-tree.test.ts`

**Interfaces:**
- Consumes: `FlowNode`/`FlowEdge`/`StepNodeData` (types.ts), `NODE_WIDTH`/`NODE_HEIGHT` constants.
- Produces (used by Task 15):
  - `layoutTree(roots: { id: string; data: StepNodeData; children?: … }[], options?: { direction?: "horizontal" | "vertical" }): { nodes: FlowNode[]; edges: FlowEdge[] }` — top-down tree: group node → category nodes → (optional) product leaves; flat (ungrouped) categories are roots. Deterministic (no physics).
  - `layoutDendrogram(nodes: { id: string; parentId: string | null; isLeaf: boolean; label: string; height: number; size: number }[], options?): { nodes: FlowNode[]; edges: FlowEdge[] }` — leaves in a row, internal nodes at `y = height` (scaled), straight-line edges; deterministic.

- [ ] **Step 1: Write the failing layout test**

`layout-tree.test.ts`:
- `tree_places_flat_category_at_root` — a flat category (no children) is emitted as a top-level node with no parent edge.
- `tree_nests_group_then_category` — group "Drinks" with child "Cocktails" → an edge `Drinks->Cocktails`, both positioned (child below/right of parent).
- `dendrogram_leaves_share_a_row` — all `isLeaf` nodes have equal `y`; an internal node's `y` is above its children (proportional to `height`).
- `dendrogram_is_deterministic` — calling twice yields identical positions.

- [ ] **Step 2: Run test to verify it fails** — `cd frontend && bun run test -- layout-tree` → FAIL.

- [ ] **Step 3: Implement `layoutTree` and `layoutDendrogram`**

Add to `layout.ts` beside `layoutColumns`; return the same `FlowNode`/`FlowEdge` shapes (reuse `EDGE_STROKE`, `MarkerType`). Hardcode spacing constants consistent with `NODE_WIDTH=216`/`NODE_HEIGHT=64`.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/flow/layout.ts frontend/components/flow/layout-tree.test.ts
git commit -m "feat(categorisation): React Flow tree + dendrogram layouts"
```

---

### Task 15: Products page + visualisation views + curation

**Files:**
- Create: `frontend/app/(app)/products/page.tsx`
- Create: `frontend/components/classification/taxonomy-tree.tsx`
- Create: `frontend/components/classification/dendrogram-view.tsx`
- Create: `frontend/components/classification/embedding-scatter.tsx`
- Create: `frontend/components/classification/pipeline-flow.tsx`
- Create: `frontend/components/classification/category-colours.ts`
- Test: `frontend/app/(app)/products/products-page.test.tsx`
- Test: `frontend/components/classification/dendrogram-view.test.tsx`

**Interfaces:**
- Consumes: the `Api` methods (Task 13), `layoutTree`/`layoutDendrogram` (Task 14), `FlowCanvas`, `StepNode`, the design-system tones (`status-conflict`, `status-missing`), Recharts `ScatterChart`, `@tanstack/react-table` `DataTable`.
- Produces: a live Products page with three tabs — **Categories** (taxonomy tree + assignment table), **Clusters** (dendrogram + embedding scatter), **Pipeline** (flow) — plus the select-then-action curation (select node/row → rename/move action bar).

- [ ] **Step 1: Write the failing page test**

`products-page.test.tsx` (Vitest + the `use-api-data` mock harness from `recipes-page.test.tsx`): renders taxonomy categories including a flat (null-group) node at top level; renders a `NEEDS_REVIEW` assignment with a `warn`/`status-conflict` treatment; renders the empty state when `getCategoryTaxonomy` returns `[]`.

- [ ] **Step 2: Run test to verify it fails** — `cd frontend && bun run test -- products-page` → FAIL.

- [ ] **Step 3: Implement the page + views**

`products/page.tsx` mirrors `recipes/page.tsx` (PageHeader + tabs + `useApiData`). `taxonomy-tree.tsx` wraps `FlowCanvas` with a `ColumnGraph` built by `layoutTree` (group → category nodes; tone = `ok`, `missing` for empty leaves). `dendrogram-view.tsx` uses `layoutDendrogram`; selecting a node sets `selected` and shows an action bar (`Rename`/`Merge`/`Split` → `renameCategory` etc.). `embedding-scatter.tsx` is a Recharts `ScatterChart` coloured by `category-colours.ts` (reuse `SERIES_COLORS` from `widgets/chart-style.ts`), with `onClick` selection → move action. `pipeline-flow.tsx` is a plain `FlowCanvas` with a `ColumnGraph` (products → embed → cluster → curate → assign → report; counts + tones from the assignment/taxonomy data). All canvases have `aria-label` + keyboard-focusable nodes.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Run frontend checks** — `cd frontend && bun run typecheck && bun run lint && bun run test` → all pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/app frontend/components/classification
git commit -m "feat(categorisation): Products page with taxonomy, dendrogram, scatter and pipeline views"
```

---

### Task 16: Whole-feature verification

- [ ] **Step 1: Backend suite**

Run: `cd backend && ./gradlew test spotlessCheck`
Expected: all green, spotless clean.

- [ ] **Step 2: Frontend suite**

Run: `cd frontend && bun run typecheck && bun run lint && bun run test`
Expected: all pass. (`bun run build` optional; run once at the end if time allows — do not run concurrently with typecheck.)

- [ ] **Step 3: Integration sanity**

Confirm the Testcontainers tests (`*IntegrationTest`, e.g. `ProductCategoryPermissionBoundaryTest`) apply `V53`/`V54` and `ddl-auto=validate` passes.

- [ ] **Step 4: Final commit** (only if lint/format fixes were needed)

```bash
git commit -am "chore(categorisation): final checks and formatting"
```

---

## Execution notes (not steps)

- The TypeSafe Jev `Choice` HTTP schema (Task 5) is the one external unknown: the port + `ProductClassification` type are fixed, but the adapter's JSON mapping must be confirmed against `https://docs.typesafe.ai/primitives/choice`. Keep the adapter fully behind the port so a schema correction is one-file.
- The `classification` package reads `canonical` directly — this is the deliberate, spec-documented deviation from `forecasting` (which is a pure leaf). The ArchUnit rule in Task 11 encodes exactly which in-platform packages `classification` may *not* reach.
- `product_category_assignment` has no `id` surrogate; `product_name_key` is the PK. Manual moves are an in-place `source=MANUAL` update, not a second row.
- Tasks are ordered for a single implementer, but Tasks 12 (Python service), 13–15 (frontend) are independent of 6–11 once the ports/contracts (Tasks 4, 13) exist — parallelise if using subagent-driven execution.
