# Recipe Composition (Slice D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Canonicalize CTB recipe composition (recipe-level cost + sale↔recipe links) as a single-source domain, enrich it with Lightspeed price/cost, and render a live Recipes page (cost/portion, actual cost, cost gap, menu price, margin, GP%).

**Architecture:** Follow the `add-domain` recipe and mirror the `Reservation` domain (the leanest full single-source example). The cross-source price join happens in the projector (reconciliation reads `canonical_sale_item`), not in the application layer, so the read path stays resolved-only; margin/gp/cost-gap are derived in the semantic layer so REST and the Ask Goldy's tool share one computation. CTB is single-authoritative; there is no reconciliation engine or override table.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA, Flyway (schema), PostgreSQL 16 (Testcontainers in tests), Next.js 15/React 19/TypeScript/Tailwind (Bun), Vitest.

**Spec:** `docs/superpowers/specs/2026-10-12-recipe-composition-design.md`

## Global Constraints

- Schema changes are Flyway migrations only (`hibernate.ddl-auto: validate`); never edit an applied migration. Next versions are `V44` and `V45`.
- Canonical entities extend `BitemporalEntity`; `logical_entity_id` is a deterministic UUID; a correction closes the row (`superseded_at`) and inserts a successor.
- `semantic` is a leaf (no in-platform deps); `reporting` depends only on `semantic` + `auth`; `api` never reads `canonical`/raw; business consumers read resolved projections through the semantic layer.
- Permission resources are seeded `ALL × OWNER` only; new resource is `recipes.cost`.
- Money/quantity columns are `numeric(14,4)`; ratios are `BigDecimal` at scale 4 (HALF_UP), `null` when the denominator is zero/unknown.
- CTB JSON contains literal `\u0000` escapes; parsers must neutralise them before writing `text`/`varchar` columns.
- Build/checks: `cd backend && ./gradlew test spotlessCheck`; `cd frontend && bun run typecheck && bun run lint && bun run test`. Commit on `docs/recipe-composition-design`; never commit to `main`.

## Review Focus

The five input classes the spec implies but no single task's happy-path test would otherwise catch:

1. **A recipe with no linked menu item** (no `sale-recipe-link`, or `is_recipe_active=false`) → `folder`, `menu_price`, `actual_cost` all null; margin/gp/cost-gap must be `null` (not `0`), rendered as "no price", never a fabricated zero. → Task 6 (projector), Task 7 (derivation), Task 11 (page).
2. **`cost_per_portion` missing/zero** → margin/gp/cost-gap are `null` when `cost_per_portion` is null, even when `menu_price` exists. → Task 7.
3. **`portionSize` is non-numeric** (e.g. `"1 serve"`) → `portion_size` keeps the raw text, `portion_size_value` is `null`; nothing throws. → Task 4.
4. **`"New "`-prefix rename** (`sale_item_stock_description` = "New Kids Fish", sale item `item_name` = "Kids Fish") → must still match to the mode price via `ProductNameKey.normalize`. → Task 6.
5. **`\u0000` embedded in a CTB string** → parser strips it; canonical `text`/`varchar` columns never contain a control character; parse does not fail. → Task 4.
6. **No `ctb-recipe-cost` raw yet** (backfill still pending) → `resolved_recipe` is empty; the Recipes page shows an empty state, not an error. → Task 11.

---

### Task 1: Flyway migrations

**Files:**
- Create: `backend/src/main/resources/db/migration/V47__recipe_composition.sql`
- Create: `backend/src/main/resources/db/migration/V48__seed_recipe_permissions.sql`

**Interfaces:**
- Produces: `canonical_recipe_cost`, `canonical_sale_recipe_link`, `resolved_recipe` tables; `recipes.cost` permission row. Every later task reads these.

- [ ] **Step 1: Write `V47__recipe_composition.sql`**

Three tables mirroring `V22__inventory.sql` (columns exactly as the spec §1):

`canonical_recipe_cost` — columns `id uuid PK`, `logical_entity_id uuid NOT NULL`, `source_system varchar(255) NOT NULL`, `source_record_ref varchar(512) NOT NULL`, `raw_record_id uuid NOT NULL REFERENCES raw_record(id)`, `recipe_id integer NOT NULL`, `recipe_name varchar(512)`, `cost_per_portion numeric(14,4)`, `cost_per_portion_incl_labour numeric(14,4)`, `total_cost numeric(14,4)`, `portion_size varchar(255)`, `portion_size_value numeric(14,4)`, `valid_from timestamptz NOT NULL`, `valid_to timestamptz`, `recorded_at timestamptz NOT NULL`, `superseded_at timestamptz`. Unique index `uq_recipe_cost_current_source_fact ON (source_system, source_record_ref) WHERE superseded_at IS NULL`.

`canonical_sale_recipe_link` — `id uuid PK`, `logical_entity_id uuid NOT NULL`, `source_system varchar(255) NOT NULL`, `source_record_ref varchar(512) NOT NULL`, `raw_record_id uuid NOT NULL REFERENCES raw_record(id)`, `sale_item_recipe_id integer`, `sale_item_stock_code varchar(255)`, `sale_item_stock_description varchar(512)`, `recipe_id integer NOT NULL`, `recipe_name varchar(512)`, `recipe_folder_name varchar(512)`, `is_recipe_active boolean`, plus the four bitemporal timestamps. Unique index `uq_sale_recipe_link_current_source_fact ON (source_system, source_record_ref) WHERE superseded_at IS NULL`.

`resolved_recipe` — `recipe_id integer PRIMARY KEY`, `recipe_name varchar(512)`, `recipe_folder_name varchar(512)`, `cost_per_portion numeric(14,4)`, `cost_per_portion_incl_labour numeric(14,4)`, `total_cost numeric(14,4)`, `portion_size varchar(255)`, `portion_size_value numeric(14,4)`, `menu_price numeric(14,4)`, `actual_cost numeric(14,4)`, `resolution_type text NOT NULL`, `authoritative_source text NOT NULL`, `resolved_at timestamptz NOT NULL`.

- [ ] **Step 2: Write `V48__seed_recipe_permissions.sql`**

```sql
-- Seed owner access to recipes.cost, matching the V6/V13/V23 convention (ALL x OWNER).
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'recipes.cost', true, true);
```

- [ ] **Step 3: Verify migrations apply and Hibernate validates**

Run: `cd backend && ./gradlew test --tests '*ArchitectureBoundariesTest'` (sanity) and `./gradlew compileJava`
Expected: build succeeds. The migration is exercised end-to-end by the Task 6 integration test (Testcontainers applies all migrations, `ddl-auto=validate` passes).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V47__recipe_composition.sql backend/src/main/resources/db/migration/V48__seed_recipe_permissions.sql
git commit -m "feat(recipe): Flyway migrations for recipe composition + recipes.cost permission"
```

---

### Task 2: Canonical recipe cost (entity + service + ingest + query)

Mirror the `CanonicalReservation*` file set exactly (same shape, recipe fields).

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/RecipeCostInput.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalRecipeCost.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalRecipeCostRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalRecipeCostService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/RecipeCostRecorded.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/RecipeCostIngest.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalRecipeCostQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/RecipeCostView.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalRecipeCostServiceTest.java`

**Interfaces:**
- Produces (used by Task 4, 5, 6):
  - `public record RecipeCostInput(String sourceSystem, int recipeId, String recipeName, BigDecimal costPerPortion, BigDecimal costPerPortionInclLabour, BigDecimal totalCost, String portionSize, BigDecimal portionSizeValue, UUID rawRecordId)`
  - `public void RecipeCostIngest.record(RecipeCostInput input)` (public facade)
  - `public List<RecipeCostView> CanonicalRecipeCostQuery.currentRecipeCosts()`
  - `public record RecipeCostView(int recipeId, String recipeName, BigDecimal costPerPortion, BigDecimal costPerPortionInclLabour, BigDecimal totalCost, String portionSize, BigDecimal portionSizeValue)`
  - `public record RecipeCostRecorded(int recipeId)` (event, published on record)

- [ ] **Step 1: Write the failing service test**

`CanonicalRecipeCostServiceTest` (pure JUnit, no Spring — construct `CanonicalRecipeCostService` with a fake `CanonicalRecipeCostRepository` and a recording `ApplicationEventPublisher`, mirroring `CanonicalReservationServiceTest` if one exists):
- `records_new_fact_and_publishes_event` — `record(input)` returns an entity with `recipeId()==input.recipeId()` and publishes one `RecipeCostRecorded(input.recipeId())`.
- `supersedes_on_changed_cost` — record a cost, then record the same `recipeId` with a different `costPerPortion`; the first row's `supersededAt` is set and a second current row exists (assert via the fake repository's saved rows).
- `same_fact_is_a_noop` — recording identical input returns the existing entity and inserts nothing new.
- `logical_id_is_deterministic` — `UUID.nameUUIDFromBytes("recipe:" + recipeId)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*CanonicalRecipeCostServiceTest'`
Expected: FAIL (compilation — `CanonicalRecipeCostService` undefined).

- [ ] **Step 3: Implement the file set**

`CanonicalRecipeCost` extends `BitemporalEntity` (copy `CanonicalReservation`'s `create(...)`/`sameFact(...)` shape, package-private). `source_record_ref = "recipe-cost:" + recipeId`; `logical_entity_id = UUID.nameUUIDFromBytes("recipe:" + recipeId)`. `CanonicalRecipeCostRepository` declares `lockCurrentRecipeCost(String ref, String source)` (PESSIMISTIC_WRITE) and `findAllCurrent()` (copy `CanonicalReservationRepository`). `CanonicalRecipeCostService.record/recordAt` copies `CanonicalReservationService` (lock → supersede → save → publish `RecipeCostRecorded`). `RecipeCostIngest` is the public facade. `CanonicalRecipeCostQuery.currentRecipeCosts()` maps current entities to `RecipeCostView`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*CanonicalRecipeCostServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical backend/src/test/java/com/goldys/platform/canonical
git commit -m "feat(recipe): canonical recipe cost entity, service, ingest and query"
```

---

### Task 3: Canonical sale-recipe link (entity + service + ingest + query)

Same file set as Task 2, for the link entity.

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/SaleRecipeLinkInput.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleRecipeLink.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleRecipeLinkRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleRecipeLinkService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/SaleRecipeLinkRecorded.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/SaleRecipeLinkIngest.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleRecipeLinkQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/SaleRecipeLinkView.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalSaleRecipeLinkServiceTest.java`

**Interfaces:**
- Produces (used by Task 5, 6):
  - `public record SaleRecipeLinkInput(String sourceSystem, int saleItemRecipeId, String saleItemStockCode, String saleItemStockDescription, int recipeId, String recipeName, String recipeFolderName, boolean isRecipeActive, UUID rawRecordId)`
  - `public void SaleRecipeLinkIngest.record(SaleRecipeLinkInput input)`
  - `public List<SaleRecipeLinkView> CanonicalSaleRecipeLinkQuery.currentLinks()`
  - `public record SaleRecipeLinkView(int recipeId, String recipeName, String recipeFolderName, String saleItemStockDescription)`
  - `public record SaleRecipeLinkRecorded(int recipeId)`

- [ ] **Step 1: Write the failing service test**

`CanonicalSaleRecipeLinkServiceTest` mirrors Task 2: `records_new_fact_and_publishes_event`, `supersedes_on_changed_link` (change `recipeFolderName`), `same_fact_is_a_noop`, `logical_id_is_deterministic` (`"sale-recipe-link:" + saleItemRecipeId`).

- [ ] **Step 2: Run test to verify it fails** — `./gradlew test --tests '*CanonicalSaleRecipeLinkServiceTest'` → FAIL (compile).

- [ ] **Step 3: Implement** — same shape as Task 2; `source_record_ref = "sale-recipe-link:" + saleItemRecipeId`.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(recipe): canonical sale-recipe link entity, service, ingest and query"`.

---

### Task 4: CTB parsers (recipe cost + sale-recipe link), with `\u0000` neutralisation

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbRecipeCostParser.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbSaleRecipeLinkParser.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/CtbRecipeCostParserTest.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/CtbSaleRecipeLinkParserTest.java`

**Interfaces:**
- Consumes: `RecipeCostInput`, `SaleRecipeLinkInput` (Task 2/3).
- Produces (used by Task 5):
  - `public List<RecipeCostInput> CtbRecipeCostParser.parse(byte[] json, UUID rawRecordId)`
  - `public List<SaleRecipeLinkInput> CtbSaleRecipeLinkParser.parse(byte[] json, UUID rawRecordId)`
  - Both neutralise `\u0000` before mapping; `recipeName`/`portionSize`/`saleItemStockDescription`/`recipeFolderName` are stripped of control characters; a non-JSON body throws `ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", ...)`.

- [ ] **Step 1: Write the failing parser tests**

`CtbRecipeCostParserTest`:
- `parses_recipe_cost_fields` — a `GetSelectedRecipeFullRecords` `{"data":[{recipeId, recipeName, costPerPortion, costPerPortionIncludingLabour, totalCost, portionSize}]}` body maps each field (note the source key `costPerPortionIncludingLabour` → `costPerPortionInclLabour`).
- `portion_size_non_numeric_yields_null_value` — `portionSize:"1 serve"` → `portionSize()=="1 serve"`, `portionSizeValue()==null`; `portionSize:"4"` → `portionSizeValue()==4.0000`.
- `strips_null_escapes` — a `recipeName` value containing `\u0000` is mapped with the null character removed; no control character in the output string.
- `non_json_throws_schema_mismatch` — a body not starting with `{`/`[` throws `ConnectorFetchException` with failure type `CONNECTOR_SCHEMA_MISMATCH`.

`CtbSaleRecipeLinkParserTest`:
- `parses_link_fields` — `saleItemRecipeId, saleItemStockCode, saleItemStockDescription, recipeId, recipeName, recipeFolderName, isRecipeActive`.
- `strips_null_escapes` and `non_json_throws_schema_mismatch` as above.

- [ ] **Step 2: Run tests to verify they fail** — FAIL (compile).

- [ ] **Step 3: Implement** — use Jackson `ObjectMapper.readTree`, sanitise each string field with a small `sanitize(String)` helper (replace `"\u0000"` with `""`); read `portion_size_value` only when `portionSize` parses as a `BigDecimal`.

- [ ] **Step 4: Run tests to verify they pass** — PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(recipe): CTB recipe-cost and sale-recipe-link parsers"`.

---

### Task 5: Canonicalization triggers (scheduled links + one-off cost replay)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbConnector.java` (the `ctb-sale-recipe-links` pull)
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/RecipeCostCanonicalizationRunner.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/CtbConnectorTest.java` (extend if present; else create)
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/RecipeCostCanonicalizationRunnerTest.java`

**Interfaces:**
- Consumes: `SaleRecipeLinkIngest`, `CtbSaleRecipeLinkParser` (Task 3/4); `RecipeCostIngest`, `CtbRecipeCostParser` (Task 2/4).
- Produces (used by Task 6): canonical rows in `canonical_sale_recipe_link` (each scheduled pull) and `canonical_recipe_cost` (after the one-off replay); both publish their `*Recorded` events.

- [ ] **Step 1: Write the failing test for the scheduled-link ingest**

In `CtbConnectorTest` (mock `CtbClient` + capturing sink + mock `SaleRecipeLinkIngest`): `pullSaleRecipeLinks_ingests_canonical` — after `fetch(...)`, the mocked `SaleRecipeLinkIngest.record` is called once per link parsed from the `searchDistinctSaleItemsForLinking` page.

- [ ] **Step 2: Run test to verify it fails** — FAIL (no ingest call).

- [ ] **Step 3: Implement the `CtbConnector` change**

In the existing `pull("ctb-sale-recipe-links", …)` callback, after `writeRaw(...)`, parse the same JSON with `CtbSaleRecipeLinkParser` and `saleRecipeLinkIngest.record(...)` each input (the same pattern `pullRevenue` uses for daily/product sales). Inject `SaleRecipeLinkIngest` + `CtbSaleRecipeLinkParser` into `CtbConnector`'s constructor.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Write the failing runner test**

`RecipeCostCanonicalizationRunnerTest`: given a fake raw-query returning two `ctb-recipe-cost` raw records, `run()` parses both and calls `RecipeCostIngest.record` for every parsed `RecipeCostInput`. (Projection is driven by the `RecipeCostRecorded` event → the Task 6 listener; the runner does not depend on the projector.)

- [ ] **Step 6: Run test to verify it fails** — FAIL.

- [ ] **Step 7: Implement `RecipeCostCanonicalizationRunner`**

A flag-gated `@Component` + `ApplicationRunner` (`@ConditionalOnProperty(name="app.recipe-cost-canonicalize", havingValue="true")`), mirroring `CtbDetailBackfillRunner`/`CtbInvoicePdfReenrichRunner`. It reads raw `ctb-recipe-cost` records (by `fetcher_identity`, via the raw-ledger query), parses each, and records via `RecipeCostIngest` — which publishes `RecipeCostRecorded`, so the Task 6 listener projects each. Idempotent via `source_record_ref` dedupe.

- [ ] **Step 8: Run tests to verify they pass** — PASS (both tests).

- [ ] **Step 9: Commit** — `git commit -m "feat(recipe): wire sale-recipe-link ingest + one-off recipe-cost canonicalization"`.

---

### Task 6: Resolved projection + projector + listener + seeder

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedRecipe.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedRecipeRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjector.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjectionListener.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleItemQuery.java` (add a price/cost method)
- Create: `backend/src/main/java/com/goldys/platform/canonical/SaleItemPriceView.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/StartupProjectionSeeder.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/RecipeProjectorTest.java`

**Interfaces:**
- Consumes: `CanonicalRecipeCostQuery.currentRecipeCosts()`, `CanonicalSaleRecipeLinkQuery.currentLinks()`, `SaleItemPriceView` (below), `RecipeCostRecorded`/`SaleRecipeLinkRecorded`.
- Produces (used by Task 7):
  - `public void RecipeProjector.recomputeAll()` — rebuilds every `resolved_recipe` row.
  - `public record SaleItemPriceView(String itemName, BigDecimal soldPriceIncTax, BigDecimal costIncTax)` and `public List<SaleItemPriceView> CanonicalSaleItemQuery.currentSaleItemPrices()`.
  - `resolved_recipe` rows: `resolution_type='SINGLE_SOURCE'`, `authoritative_source='CTB'`, `menu_price`/`actual_cost` = mode (most-common) `soldPriceIncTax`/`costIncTax` of the recipe's matched menu items.

- [ ] **Step 1: Write the failing projector test**

`RecipeProjectorTest` (construct `RecipeProjector` with fake query facades):
- `projects_cost_and_folder` — one recipe cost + one link → one `resolved_recipe` row with the cost fields and `recipe_folder_name` from the link.
- `latest_cost_wins` — two current costs for one recipe (simulate via the fake returning the superseded one too) → the projector picks the latest; assert only the current row is materialised.
- `joins_price_by_normalized_name` — a link with `saleItemStockDescription="Kids Fish"` and a sale item `itemName="New Kids Fish"` with `soldPriceIncTax=18.50`, `costIncTax=6.00` → `menu_price=18.50`, `actual_cost=6.00` (the `"New "` alias folds in).
- `mode_not_mean` — two sale items for one name, `soldPriceIncTax` 18.50 twice and 2.00 once → `menu_price=18.50`.
- `no_match_yields_null_price` — a recipe with no matching sale item → `menu_price`/`actual_cost` null.
- `recomputeAll_is_idempotent` — calling twice yields the same rows.

- [ ] **Step 2: Run test to verify it fails** — FAIL (compile).

- [ ] **Step 3: Implement**

`ResolvedRecipe` is a package-private `@Entity` with `@Id Integer recipeId` and the `resolved_recipe` columns; `ResolvedRecipeRepository extends JpaRepository<ResolvedRecipe, Integer>`. Add `currentSaleItemPrices()` to `CanonicalSaleItemQuery` returning `SaleItemPriceView`s (query current `canonical_sale_item`, project `item_name, sold_price_inc_tax, cost_inc_tax`). `RecipeProjector.recomputeAll()` deletes all, then: build `Map<String normalizedName, Mode>` from `currentSaleItemPrices()` (normalise with `ProductNameKey.normalize`); map each link's `saleItemStockDescription` (normalised) to its recipe; write rows joining latest cost + folder + price/cost mode. `RecipeProjectionListener` listens on both `*Recorded` events → `recomputeAll()` (idempotent; the one-off backfill fires it per recorded recipe — acceptable at pub scale, and the runner can instead trigger a single rebuild after the batch if it proves slow). Register `RecipeProjector` in `StartupProjectionSeeder` (call `recomputeAll()` unconditionally, like `reservationProjector`).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(recipe): resolved recipe projection + projector + listener"`.

---

### Task 7: Semantic query (RecipeMetricsQuery + records + ResolvedRecipeQuery)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/RecipeSummary.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/RecipeCosting.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/RecipeMetricsQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedRecipeQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ResolvedRecipeQueryTest.java`

**Interfaces:**
- Consumes: `ResolvedRecipeRepository` (Task 6).
- Produces (used by Task 8, 9):
  - `public record RecipeSummary(int recipeId, String name, String folder, BigDecimal costPerPortion, BigDecimal costPerPortionInclLabour, BigDecimal totalCost, String portionSize, BigDecimal portionSizeValue, BigDecimal menuPrice, BigDecimal actualCost)`
  - `public record RecipeCosting(int recipeId, String name, String folder, BigDecimal costPerPortion, BigDecimal actualCost, BigDecimal costGap, BigDecimal menuPrice, BigDecimal margin, BigDecimal gpPercent)`
  - `public interface RecipeMetricsQuery { List<RecipeSummary> recipes(); List<RecipeCosting> costing(); }`
  - `public class ResolvedRecipeQuery implements RecipeMetricsQuery`

- [ ] **Step 1: Write the failing query test**

`ResolvedRecipeQueryTest` (construct with a fake `ResolvedRecipeRepository`):
- `recipes_returns_resolved_rows` — one `ResolvedRecipe` → `recipes()` returns one `RecipeSummary` with matching fields.
- `costing_computes_margin_gp_costgap` — `costPerPortion=6.00`, `menuPrice=18.50`, `actualCost=7.20` → `margin=12.50`, `gpPercent=0.6757`, `costGap=1.20`.
- `costing_null_when_price_or_cost_null` — `menuPrice` null → `margin`/`gpPercent` null; `actualCost` null → `costGap` null; `costPerPortion` null → `margin`/`costGap` null.
- `costing_null_when_menu_price_zero` — `menuPrice=0` → `gpPercent` null (division guarded), `margin` still computed.

- [ ] **Step 2: Run test to verify it fails** — FAIL (compile).

- [ ] **Step 3: Implement** — `ResolvedRecipeQuery` reads `ResolvedRecipeRepository.findAll()` (order by `recipe_name`), maps to the records; `costing()` derives `margin = menuPrice − costPerPortion`, `gpPercent = margin / menuPrice` (scale 4, HALF_UP), `costGap = actualCost − costPerPortion`, each `null`-guarded per the tests.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(recipe): semantic recipe metrics query + derived costing"`.

---

### Task 8: Application service + REST controller

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/application/RecipeReportingService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/RecipeController.java`
- Test: `backend/src/test/java/com/goldys/platform/application/RecipeReportingServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/api/RecipeControllerTest.java`

**Interfaces:**
- Consumes: `RecipeMetricsQuery.costing()` (Task 7), `PermissionService.require(...)`, `CurrentUserService.roleOf(...)`.
- Produces (used by Task 11):
  - `public List<RecipeCosting> RecipeReportingService.costing(UserRole role)` — authorizes `recipes.cost` READ, delegates.
  - `GET /api/recipes` → `200` with `List<RecipeCosting>`; `403` when unauthorized.

- [ ] **Step 1: Write the failing service test**

`RecipeReportingServiceTest`: `authorizes_and_returns_costing` — with a fake `PermissionService` (record the require call) and a fake `RecipeMetricsQuery`, `costing(role)` calls `require(role, new ResourceKey("recipes.cost"), READ)` and returns the query's list. `denies_without_permission` — a `PermissionService` that throws → the exception propagates.

- [ ] **Step 2: Run test to verify it fails** — FAIL (compile).

- [ ] **Step 3: Implement** — `RecipeReportingService` copies `ReservationReportingService` (resource `new ResourceKey("recipes.cost")`, method `costing(UserRole)`).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Write the failing controller test**

`RecipeControllerTest` (Spring MVC slice or plain `RecipeController` with a fake `RecipeReportingService` + fake `CurrentUserService`): `GET /api/recipes` returns `200` and the service's list; verify the controller never imports `canonical`.

- [ ] **Step 6: Run test to verify it fails** — FAIL (compile).

- [ ] **Step 7: Implement `RecipeController`** — `@RestController @RequestMapping("/api/recipes")`, `@GetMapping List<RecipeCosting> list(@AuthenticationPrincipal AccountUserDetails user)` delegating to `reporting.costing(currentUser.roleOf(user))`. Copy `ReservationController`'s shape.

- [ ] **Step 8: Run test to verify it passes** — PASS.

- [ ] **Step 9: Commit** — `git commit -m "feat(recipe): recipe reporting service + REST endpoint"`.

---

### Task 9: Ask Goldy's reporting tool

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetRecipeCostingInput.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetRecipeCostingTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` (add `GET_RECIPE_COSTING`)
- Test: `backend/src/test/java/com/goldys/platform/reporting/GetRecipeCostingToolTest.java`

**Interfaces:**
- Consumes: `RecipeMetricsQuery.costing()` (Task 7), `Column`, `TableWidgetSpec` (`com.goldys.platform.widget`).
- Produces: `ToolResult` whose `widget` is a `TableWidgetSpec` with columns `recipe` (name), `folder`, `cost_per_portion`, `actual_cost`, `cost_gap`, `menu_price`, `margin`, `gp_percent`.

- [ ] **Step 1: Write the failing tool test**

`GetRecipeCostingToolTest`: `returns_table_widget` — with a fake `RecipeMetricsQuery` returning one `RecipeCosting`, `execute(new GetRecipeCostingInput(), role)` returns a `ToolResult` whose widget is a `TableWidgetSpec`; assert `columns` has exactly the 8 keys above and `rows` has one row with the correct `margin` value. `resource_is_recipes_cost` — `resource().value().equals("recipes.cost")`. `to_metric_queries_is_empty` — `toMetricQueries(...)` returns `List.of()`.

- [ ] **Step 2: Run test to verify it fails** — FAIL (compile).

- [ ] **Step 3: Implement**

`public record GetRecipeCostingInput(int limit) implements ToolInput` (default/`limit<=0` means all; validate non-negative). `GetRecipeCostingTool implements ReportingTool` — `id()=GET_RECIPE_COSTING`, `name()="get_recipe_costing"`, `description()="Recipe cost, margin and gross profit per dish."`, `resource()=new ResourceKey("recipes.cost")`. `execute` reads `RecipeMetricsQuery.costing()`, applies `limit` if positive, builds `List<Column>` (`format` "currency"/"percent" where relevant) and `List<Map<String,Object>>` rows, and returns `new ToolResult(new TableWidgetSpec(uuid, "Recipe costing", null, columns, rows, null), List.of(), List.of(), List.of())`. `toMetricQueries` returns `List.of()`.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(recipe): Ask Goldy's get_recipe_costing tool"`.

---

### Task 10: Architecture boundary + permission boundary tests

**Files:**
- Modify: `backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java`
- Test: `backend/src/test/java/com/goldys/platform/architecture/RecipePermissionBoundaryTest.java` (or extend an existing permission-boundary test)

**Interfaces:**
- Consumes: the packages created in Tasks 2–9.

- [ ] **Step 1: Write the failing boundary assertions**

Extend `ArchitectureBoundariesTest` mirroring the `reservation*`/`inventory*` rules:
- `api.RecipeController` does not import/access `canonical` (only `application`, `auth`, `semantic`).
- `reporting.GetRecipeCostingTool` depends only on `semantic` + `auth` + `widget` (never `canonical`/`reconciliation`/`application`).
- `semantic.RecipeMetricsQuery` is a leaf (no in-platform deps) and its implementation `ResolvedRecipeQuery` lives in `reconciliation`.

- [ ] **Step 2: Run test to verify it fails** — FAIL (rules absent / violated).

- [ ] **Step 3: Implement** — add the rules (copy the existing `reservation` rule blocks; use the ArchUnit DSL already present).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Write the permission boundary test**

`RecipePermissionBoundaryTest` (Testcontainers, mirroring `PermissionBoundaryIntegrationTest`): a user with `ALL×OWNER` reads `GET /api/recipes` (or `RecipeReportingService.costing`) successfully; a user with no `recipes.cost` grant gets a `403`/denied.

- [ ] **Step 6: Run test to verify it passes** — PASS.

- [ ] **Step 7: Commit** — `git commit -m "test(recipe): architecture + permission boundary coverage"`.

---

### Task 11: Frontend — Recipes page + Api contract

**Files:**
- Modify: `frontend/lib/api/types.ts` (add `RecipeCosting` type + `getRecipes` to the `Api` interface)
- Modify: `frontend/lib/api/index.ts` (re-export `RecipeCosting`)
- Modify: `frontend/lib/api/live.ts` (add `getRecipes`)
- Modify: `frontend/lib/api/demo.ts` (add `getRecipes` fixture)
- Modify: `frontend/app/(app)/recipes/page.tsx` (replace the `AwaitingData` placeholder)
- Test: `frontend/app/(app)/recipes/recipes-page.test.tsx`

**Interfaces:**
- Consumes: `GET /api/recipes` (Task 8).
- Produces: a live Recipes page; `getRecipes(): Promise<RecipeCosting[]>` on the `Api` contract.

- [ ] **Step 1: Write the failing page test**

`recipes-page.test.tsx` (Vitest + the existing page-test harness, e.g. `sales-page.test.tsx`): renders with a mocked `Api.getRecipes` returning one `RecipeCosting` (cost 6.00, price 18.50, margin 12.50, gpPercent 0.6757, costGap 1.20) and asserts the dish name, "margin" and "GP%" cells render; renders the empty state when `getRecipes` returns `[]` (no error).

- [ ] **Step 2: Run test to verify it fails** — `cd frontend && bun run test -- recipes-page` → FAIL.

- [ ] **Step 3: Implement the contract + page**

Add `RecipeCosting` to `types.ts` (mirror the backend record's JSON shape) and `getRecipes` to the `Api` interface. `live.ts`: `getRecipes: () => fetchApi<RecipeCosting[]>("/api/recipes")`. `demo.ts`: return 4–6 fixture rows. `page.tsx`: a `DataTable` (columns: name, folder, cost/portion, actual cost, cost gap, menu price, margin $, GP%) + two `StatCard` KPIs (average GP%, highest/lowest margin), following `reservations/page.tsx`; `null` price/cost renders an empty `—` (not `0`); empty list renders `EmptyState` "No recipes yet".

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Run the frontend checks** — `bun run typecheck && bun run lint && bun run test` → all pass.

- [ ] **Step 6: Commit** — `git commit -m "feat(recipe): live Recipes page with costing table"`.

---

### Task 12: Whole-slice verification

- [ ] **Step 1: Backend suite**

Run: `cd backend && ./gradlew test spotlessCheck`
Expected: all tests green, spotless clean.

- [ ] **Step 2: Integration sanity**

Confirm the Testcontainers integration tests (`*IntegrationTest`, if any were added in Task 10) apply `V44`/`V45` and `ddl-auto=validate` passes.

- [ ] **Step 3: Final commit** (only if lint/format fixes were needed)

```bash
git commit -am "chore(recipe): final checks and formatting"
```

---

## Execution notes (not steps)

- The `ctb-detail-backfill` completion (raw `ctb-recipe-cost` present) is **owned by another agent**. Task 5's runner and Task 6's seeder are safe to build and test against synthetic fixtures before the raw data exists; do not block on it.
- `resolved_recipe.menu_price`/`actual_cost` are nullable `numeric(14,4)`; the frontend must render nulls as `—`, never `0`.
- `GetRecipeCostingTool.toMetricQueries()` returns empty by design (recipe master data is not a time-series metric); the widget is not re-runnable from a saved dashboard. This is documented in the spec.
