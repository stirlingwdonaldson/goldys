# Lightspeed Menu Source (product catalog) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ingest the Lightspeed product export CSV as a snapshot-versioned source of truth for menu membership, match CTB recipe links to those products by name (+ alias table, + `ProductID` fast-path), and derive `resolved_recipe.menu_state` from product presence instead of CTB's `isRecipeActive`.

**Architecture:** A new `canonical_product` bitemporal table (one row per product per snapshot version) plus a `product_alias` name-matching table. A drop endpoint mirrors the OpenTable menu CSV upload. A matcher resolves each current `canonical_sale_recipe_link` to a `lightspeed_product_id` (name + alias, with ID fast-path). `RecipeProjector` derives `menu_state` from "is the linked product present and sellable in the current snapshot".

**Tech Stack:** Java 25 / Spring Boot 3.5 / JPA + Flyway (`hibernate.ddl-auto=validate`) / PostgreSQL 16 (Testcontainers).

**Spec:** `docs/superpowers/specs/2026-10-13-lightspeed-menu-source-design.md` (the plan argues from it; read it first).

## Global Constraints

- `hibernate.ddl-auto=validate`: every entity column must exist in a Flyway migration; never edit an applied migration.
- Resolved projections are disposable and rebuilt by projectors; never store information *only* in a resolved row.
- Canonical entities are immutable bitemporal facts (`BitemporalEntity`); a change supersedes (closes) the current row and inserts a successor sharing `logical_entity_id`; `source_record_ref` + `source_system` uniquely identify the current fact.
- Business consumers read resolved data through `semantic` query interfaces; `api` and `application` never touch `canonical` directly (ArchUnit-enforced).
- Name key is `ProductNameKey.normalize` (case/punctuation/whitespace/leading `"New "`); the alias table handles everything normalize does *not* collapse.
- `menu_state` values are `ON_MENU` / `OFF_MENU` / `UNKNOWN`; an unmatched link yields `UNKNOWN`, never a guessed `OFF_MENU`.
- `ProductID` is a fast-path, never the sole key (see spec "ProductID — future risks").
- No sales-recently proxy; no stock state. Presence (filtered to sellable) is the whole model.

## Review Focus

The failure modes below are implied by the spec but not exercised by any task's happy-path tests. Each is pinned by a test in the owning task.

1. **A link's `lightspeed_product_id` points at a product absent from the *current* snapshot** (retired since last ingest) — must read `OFF_MENU`, not `ON_MENU`; the ID fast-path must verify presence against the current snapshot (Task 6).
2. **A link with a null `lightspeed_product_id`** (unmatched) — must read `UNKNOWN`, never `OFF_MENU` (Task 6).
3. **A product present but `is_modifier = true` or zero `sell_price_inc_tax`** — not sellable, so a recipe linked to it must *not* read `ON_MENU` (Task 2/6).
4. **Snapshot versioning — "close absent":** a product present in snapshot 1 but absent from snapshot 2 must be superseded (closed), and its recipe reads `OFF_MENU` after the rebuild; history is retained, nothing deleted (Task 2).
5. **Idempotent re-ingest of the *same* CSV** — no spurious supersession or duplicate current rows (Task 2/5).

---

### Task 1: Migration — `canonical_product`, `product_alias`, link column

**Files:**
- Create: `backend/src/main/resources/db/migration/V59__lightspeed_product_catalog.sql`

**Interfaces:**
- Produces: tables `canonical_product`, `product_alias`, and column `lightspeed_product_id` on `canonical_sale_recipe_link` (consumed by Tasks 2, 3, 4).

- [ ] **Step 1: Write the migration**

`V59__lightspeed_product_catalog.sql` (mirror `V47__recipe_composition.sql` DDL style; standard bitemporal columns verbatim from `V47`):

```sql
CREATE TABLE canonical_product (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    product_id integer NOT NULL,
    product_name varchar(512),
    category varchar(255),
    sell_price_inc_tax numeric(14,4),
    is_modifier boolean,
    reporting_group varchar(255),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_product_current_source_fact
    ON canonical_product (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE product_alias (
    id uuid PRIMARY KEY,
    canonical_name varchar(512) NOT NULL,
    alias varchar(512) NOT NULL,
    source varchar(32),
    created_at timestamp(6) with time zone NOT NULL
);
CREATE UNIQUE INDEX uq_product_alias_pair ON product_alias (canonical_name, alias);

ALTER TABLE canonical_sale_recipe_link ADD COLUMN lightspeed_product_id integer;
```

- [ ] **Step 2: Run the migration against the local stack**

Run: `docker compose up -d` then `cd backend && ./gradlew compileJava`
Expected: `schema validate` passes once Task 2 adds the entity (compile alone is fine here); migration applies with no error.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V59__lightspeed_product_catalog.sql
git commit -m "feat(menu-source): add canonical_product, product_alias and link product id"
```

---

### Task 2: Canonical product stack (entity, service, ingest, query)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProduct.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductInput.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductRecorded.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductIngest.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductView.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalProductServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalProductQueryTest.java` (or fold into service test)

**Interfaces:**
- Produces (used by Tasks 5/6): `ProductInput(String sourceSystem, int productId, String productName, String category, BigDecimal sellPriceIncTax, boolean isModifier, String reportingGroup, UUID rawRecordId)`; `ProductIngest.recordAll(List<ProductInput>)`; `CanonicalProductQuery.currentProducts() -> List<ProductView>`; `ProductView(int productId, String productName, String category, BigDecimal sellPriceIncTax, boolean isModifier, String reportingGroup)`; `ProductRecorded(int productId)`; `ProductIngest.closeAbsent(Set<Integer> presentProductIds)`.
- Consumes: `BitemporalEntity` / `BitemporalRepository` (existing), `ProductNameKey` (existing).

- [ ] **Step 1: Write failing tests** (mirror `CanonicalRecipeCostServiceTest` style — Mockito, no DB)

  - `recordAllSupersedesOnChange`: record `ProductInput("LIGHTSPEED", 1, "Keeper Pilsner", …, 10.00, false, "Goldy's Bev", raw)` twice with a changed `sellPriceIncTax`; assert two rows, first superseded, second current, same `logical_entity_id`.
  - `recordAllIsIdempotentForUnchanged`: record the same input twice; assert a single current row (no spurious supersession) — Review Focus #5.
  - `closeAbsentSupersedesMissingProducts`: after recording products {1,2,3}, call `closeAbsent(Set.of(1,3))`; assert product 2 is superseded, 1 and 3 remain current — Review Focus #4.
  - `recordPublishesEvent`: assert a `ProductRecorded` is published per `recordAll` batch.

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.CanonicalProductServiceTest'`
Expected: FAIL (classes do not exist).

- [ ] **Step 3: Implement the entity and stack**

Mirror `CanonicalRecipeCost`/`CanonicalRecipeCostService`/`RecipeCostIngest`/`CanonicalRecipeCostQuery` exactly, substituting product fields. Key specifics:

- `CanonicalProduct` extends `BitemporalEntity`; `source_record_ref = "lightspeed-product:" + productId`; `logical_entity_id = UUID.nameUUIDFromBytes(("lightspeed-product:" + productId).getBytes(UTF_8))`. `sameFact(ProductInput)` compares all fact fields.
- `CanonicalProductService.recordAll(List<ProductInput>)` records in one transaction, publishes one `ProductRecorded` (any productId).
- `CanonicalProductService.closeAbsent(Set<Integer> presentProductIds)` — a `@Transactional` method that locks all current products, and for each current product whose `product_id` is *not* in `presentProductIds`, supersedes it (sets `supersededAt`), then publishes `ProductRecorded`. Repository method `lockAllCurrent()` and `findAllCurrent()` (or reuse a `findAllCurrent()` and lock each).
- `CanonicalProductQuery.currentProducts()` returns all current products mapped to `ProductView`.

- [ ] **Step 4: Run the service test class**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.CanonicalProductServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalProduct*.java backend/src/main/java/com/goldys/platform/canonical/Product*.java backend/src/test/java/com/goldys/platform/canonical/CanonicalProductServiceTest.java
git commit -m "feat(menu-source): canonical product entity and ingest"
```

---

### Task 3: Product alias stack + seeder

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductAlias.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductAliasRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductAliasQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductAliasSeeder.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/ProductAliasSeederTest.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/ProductAliasQueryTest.java`

**Interfaces:**
- Produces (used by Task 5 matcher): `ProductAliasQuery.lookupCanonical(String normalizedAlias) -> Optional<String>`; `ProductAliasQuery.all() -> List<ProductAliasView>`; `ProductAliasView(String canonicalName, String alias)`.
- Consumes: `ProductNameKey` (existing), `ProductAliasRepository`.

- [ ] **Step 1: Write failing tests**

  - `seederNormalizesBothSides`: given a raw pair `("Baba Ganoush", "Eggplant Dip")`, the seeder inserts `canonical_name = ProductNameKey.normalize("Baba Ganoush")` and `alias = ProductNameKey.normalize("Eggplant Dip")`.
  - `seederIsIdempotent`: running the seeder twice inserts each pair once (unique `(canonical_name, alias)`).
  - `lookupCanonicalResolvesAlias`: after seeding, `lookupCanonical("eggplant dip")` returns `"baba ganoush"`.

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.ProductAliasSeederTest'`
Expected: FAIL.

- [ ] **Step 3: Implement**

  - `ProductAlias` is a plain (non-bitemporal) `@Entity` on `product_alias` (`id`, `canonicalName`, `alias`, `source`, `createdAt`).
  - `ProductAliasSeeder` is a `@Component` `ApplicationRunner` (or invoked by `StartupProjectionSeeder`) that inserts each hardcoded raw pair if absent. The pair list is the "still active" mapping from `docs/connectors/pos-menu-reconciliation-2026-10-13.md`; normalize each side with `ProductNameKey.normalize`. Seed these pairs (raw `canonical` ← `alias`):

```
Baba Ganoush ← Eggplant Dip
Charred Corn Salad ← Corn Estiques
Kids slider ← Kiddies slider
Mustard Gravy - Side ← Mustard Gravy
Half Turkish bread - side ← Turkish bread
B - Havilah Gewurztraminer ← G - Havilah Gewurztraminer
B - Spinifex Rose 2025 ← G - Spinifex Rose 2025
G - Deep Woods Estate Chardonnay ← G - Deepwoods Estate Chardonnay
B - Lighthand, Pinot Noir ← G - Lighthand, Pinot Noir
B - Denton Shed Chardonnay (2023) ← G - Denton Shed Chardonnay
Aperol Spritz (NV Three Pillars) ← Aperol Spritz (NV Continental)
Campari Spritz (NV Three Pillars) ← Campari Spritz (NV Continental)
Limoncello Spritz (NV Three Pillars) ← Limoncello Spritz (NV Continental)
Montenegro Spritz (NV Three Pillars) ← Montenegro Spritz (NV Continental)
B - NV Three Pillars Sparkling Brut Cuvee ← B - NV Continental Platter Prosecco
G - NV Three Pillars Sparkling Brut Cuvee ← G - NV Continental Platter Prosecco
.5L - Jamsheed Mataro (Tap) ← .5L - Jamsheed Mataro (Chilled)
G - Jamsheed Mataro (Tap) ← G - Jamsheed Mataro (Chilled)
Gnocchi - Pumpkin Butter Sage ← Gnocchi - Pumpkin
Ouzo - 30ml ← Katsaros Brothers Ouzo - 30ml
F - Ruben Croquette ← Ruben Croquettes
+1 Croquette ← + 1 Ruben Croquette
Kids Gnocchi Bolognese ← Kids Gnocchi
Kids Gnocchi Napoli ← Kids Gnocchi
Jug - Keeper Pilsner ← Keeper Pilsner - Jug
Pint - Keeper Pilsner ← Keeper Pilsner - Pint
Pot - Keeper Pilsner ← Keeper Pilsner - Pot
Jug - Goldy's 'An Old One' Creaming Old Ale Shandy ← Goldy's 'An Old One' Creaming Old Ale Shandy - Jug
Pint - Goldy's 'An Old One' Creaming Old Ale Shandy ← Goldy's 'An Old One' Creaming Old Ale Shandy - Pint
Pot - Goldy's 'An Old One' Creaming Old Ale Shandy ← Goldy's 'An Old One' Creaming Old Ale Shandy - Pot
Jug - Philter XPA ← Philter XPA - Jug
Pint - Philter XPA ← Philter XPA - Pint
Pot - Philter XPA ← Philter XPA - Pot
Jug - Loveshack Mid Red-Ale ← Loveshack Mid Red-Ale - Jug
Pint - Loveshack Mid Red-Ale ← Loveshack Mid Red-Ale - Pint
Pot - Loveshack Mid Red-Ale ← Loveshack Mid Red-Ale - Pot
Can - Bridge Road Brewers 'Beechworth' Pale Ale ← Bridge Road Brewers 'Beechworth' Pale Ale
Dolin Dry Vermouth - 30ml ← Dolin Dry Vermouth - 45ml
```

  - `ProductAliasQuery` exposes `lookupCanonical(normalizedAlias)` (returns `canonical_name` for a normalized `alias`) and `all()`.

- [ ] **Step 4: Run the test classes**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.ProductAlias*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/ProductAlias*.java backend/src/test/java/com/goldys/platform/canonical/ProductAlias*Test.java
git commit -m "feat(menu-source): product alias table and seed"
```

---

### Task 4: Link product-id plumbing

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/SaleRecipeLinkInput.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleRecipeLink.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/SaleRecipeLinkView.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleRecipeLinkQuery.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleRecipeLinkRepository.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleRecipeLinkService.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/SaleRecipeLinkIngest.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalSaleRecipeLinkServiceTest.java`

**Interfaces:**
- Consumes: `SaleRecipeLinkInput` (existing + new trailing field).
- Produces (used by Task 5 matcher): `SaleRecipeLinkIngest.setProductId(int saleItemRecipeId, Integer lightspeedProductId)`; `SaleRecipeLinkView` gains `int saleItemRecipeId()` and `Integer lightspeedProductId()`; `CanonicalSaleRecipeLinkQuery.currentLinks()` carries the new fields.

- [ ] **Step 1: Write failing tests**

  - `setProductIdSupersedesWhenChanged`: record a link via `record(...)` with `lightspeedProductId = null`, then `setProductId(saleItemRecipeId, 123)`; assert the null row is superseded and a current row with `lightspeedProductId = 123` exists, sharing `logical_entity_id` and `raw_record_id`.
  - `setProductIdIsIdempotent`: `setProductId(id, 123)` twice; assert one supersession, not two.
  - `setProductIdClearsWhenUnmatched`: `setProductId(id, null)` after a prior `setProductId(id, 123)`; assert the current row has `lightspeedProductId = null`.

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.CanonicalSaleRecipeLinkServiceTest'`
Expected: FAIL.

- [ ] **Step 3: Implement**

  - `SaleRecipeLinkInput` gains trailing `Integer lightspeedProductId` (before `rawRecordId`, keep existing field order otherwise).
  - `CanonicalSaleRecipeLink` gains `lightspeed_product_id` column/field, in `create(...)`, `sameFact(...)`, and a `lightspeedProductId()` getter.
  - `SaleRecipeLinkView` gains `int saleItemRecipeId` and `Integer lightspeedProductId` (and `CanonicalSaleRecipeLinkQuery.toView` populates them).
  - `CanonicalSaleRecipeLinkRepository` gains `lockCurrentBySaleItemRecipeId(int saleItemRecipeId)` (pessimistic write on `sale_item_recipe_id` + `sourceSystem` + `supersededAt is null`).
  - `CanonicalSaleRecipeLinkService.setProductId(int saleItemRecipeId, Integer productId)`: `@Transactional`; lock the current link; if absent, no-op; if `lightspeedProductId` already equals `productId`, no-op (idempotent); else `supersede(now)`, save, then insert a successor copying *all* fact fields and `logical_entity_id`/`raw_record_id` from the existing row but with the new `lightspeedProductId`; publish `SaleRecipeLinkRecorded`.
  - `SaleRecipeLinkIngest.setProductId(...)` delegates to the service (public facade).

- [ ] **Step 4: Run the test class + full fast lane**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.CanonicalSaleRecipeLinkServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/SaleRecipeLink*.java backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleRecipeLink*.java backend/src/test/java/com/goldys/platform/canonical/CanonicalSaleRecipeLinkServiceTest.java
git commit -m "feat(menu-source): lightspeed_product_id on sale-recipe link"
```

---

### Task 5: Parser + matcher + ingest service + drop endpoint

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedProduct.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedProductCatalogParser.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedProductCatalogIngestService.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/lightspeed/RecipeProductMatcher.java`
- Modify: `backend/src/main/java/com/goldys/platform/application/ConnectorApplicationService.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/lightspeed/LightspeedProductCatalogParserTest.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/lightspeed/RecipeProductMatcherTest.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/lightspeed/LightspeedProductCatalogIngestServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ConnectorStatusControllerTest.java` (extend)

**Interfaces:**
- Consumes: `IngestionService.ingestPush(...)` (existing), `ProductIngest` (Task 2), `ProductAliasQuery` (Task 3), `SaleRecipeLinkIngest.setProductId` (Task 4), `CanonicalSaleRecipeLinkQuery` + `CanonicalProductQuery`.
- Produces: `LightspeedProduct(int productId, String productName, String category, BigDecimal sellPriceIncTax, boolean isModifier, String reportingGroup)`; `LightspeedProductCatalogParser.parse(byte[] csv) -> List<LightspeedProduct>`; `LightspeedProductCatalogIngestService.ingest(byte[] csv)`; `RecipeProductMatcher.match()`; `ConnectorApplicationService.uploadLightspeedProductsCsv(UserRole, byte[])`.

- [ ] **Step 1: Write failing parser tests**

`LightspeedProductCatalogParserTest` (use a fixture CSV with the header from the product export): required columns `ProductID`, `ProductName`; parse a row into `LightspeedProduct(22101650, "$20 Eggplant Parma Special", "Weekly Food Specials", 20.00, false, "Goldy's Food")`; missing column → `CONNECTOR_SCHEMA_MISMATCH`; empty CSV → `CONNECTOR_SCHEMA_MISMATCH`.

- [ ] **Step 2: Implement `LightspeedProduct` + `LightspeedProductCatalogParser`**

Mirror `OpenTableMenuItemsCsvParser` (header-driven, `CSVFormat.DEFAULT`, `stripBom`, `columnIndex`, `require`, `get`). `IsModifier` parsed as `"1".equals(value)`; price via the existing `money(...)` pattern (`replace("$","").replace(",","")`).

- [ ] **Step 3: Write failing matcher tests**

`RecipeProductMatcherTest` (Mockito):
  - `matchesByNameAndSetsProductId`: a link with `saleItemStockDescription = "Keeper Pilsner - Jug"` and product `("Jug - Keeper Pilsner", id=1)` seeded as an alias → `match()` calls `setProductId(saleItemRecipeId, 1)`.
  - `aliasResolves`: link `"Eggplant Dip"`, alias maps to `"Baba Ganoush"`, product `"Baba Ganoush"` id=2 → `setProductId(..., 2)`.
  - `idFastPathPreservesExisting`: a link already `lightspeedProductId = 1` and product 1 current → `setProductId` NOT called (no change).
  - `unmatchedSetsNull`: a link `"Satay Schnitty"` with no product/alias → `setProductId(..., null)`.

- [ ] **Step 4: Implement `RecipeProductMatcher`**

`match()`: load `currentLinks()` (with `saleItemRecipeId`, `saleItemStockDescription`, `lightspeedProductId`) and `currentProducts()` + `ProductAliasQuery.all()`. Build a normalized-name→`productId` map (and an alias→canonical map). For each link, resolve `targetId` = existing `lightspeedProductId` if that id is present in the current products (fast-path), else direct normalized-name match, else alias lookup, else `null`. If `targetId` differs from the link's current `lightspeedProductId`, call `SaleRecipeLinkIngest.setProductId(saleItemRecipeId, targetId)`. The fast-path must verify the id exists in the *current* snapshot — Review Focus #1.

- [ ] **Step 5: Write failing ingest tests**

`LightspeedProductCatalogIngestServiceTest` (Mockito): `ingest` persists a raw record with `fetcher_identity = "lightspeed-product-catalog"` and `source_system = "LIGHTSPEED"` (verify `IngestionService.ingestPush` args), records products via `ProductIngest.recordAll`, calls `ProductIngest.closeAbsent(parsedIds)`, then calls `RecipeProductMatcher.match()`.

- [ ] **Step 6: Implement `LightspeedProductCatalogIngestService`**

`ingest(byte[] csv)`: `ingestion.ingestPush("LIGHTSPEED", "lightspeed-product-catalog", FetchMethod.FILE_EXPORT, "text/csv", csv, UTF_8, "lightspeed-product-catalog")`; `parser.parse(csv)`; `productIngest.recordAll(inputs)`; `productIngest.closeAbsent(parsedProductIds)`; `matcher.match()`. Wire into `ConnectorApplicationService.uploadLightspeedProductsCsv(UserRole, byte[])` (requires `connectors` WRITE, mirrors `uploadOpenTableMenuItemsCsv`) and a `POST /api/connectors/lightspeed-products/upload` endpoint in `ConnectorStatusController`. Add a `KNOWN_SOURCES` entry `("LIGHTSPEED", "lightspeed-product-catalog", …)`.

- [ ] **Step 7: Run the connector + controller test classes**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.connectors.lightspeed.*' --tests 'com.goldys.platform.api.ConnectorStatusControllerTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedProduct*.java backend/src/main/java/com/goldys/platform/connectors/lightspeed/RecipeProductMatcher.java backend/src/main/java/com/goldys/platform/application/ConnectorApplicationService.java backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java backend/src/test/java/com/goldys/platform/connectors/lightspeed/* backend/src/test/java/com/goldys/platform/api/ConnectorStatusControllerTest.java
git commit -m "feat(menu-source): product catalog parser, matcher and drop endpoint"
```

---

### Task 6: Projector — `menu_state` from product presence

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjector.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjectionListener.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/RecipeProjectorTest.java`

**Interfaces:**
- Consumes: `CanonicalProductQuery.currentProducts()` (Task 2), `SaleRecipeLinkView` with `lightspeedProductId` (Task 4).
- Produces: `resolved_recipe.menu_state` = `ON_MENU` / `OFF_MENU` / `UNKNOWN` from product presence (no longer `isRecipeActive`).

- [ ] **Step 1: Write failing tests** (extend `RecipeProjectorTest`)

  - `presentProductYieldsOnMenu`: link with `lightspeedProductId = 1`, current sellable product id 1 → `ON_MENU`.
  - `retiredProductYieldsOffMenu`: link with `lightspeedProductId = 1`, but id 1 absent from current products → `OFF_MENU` — Review Focus #1.
  - `unmatchedLinkYieldsUnknown`: link with `lightspeedProductId = null` → `UNKNOWN` (and with another link null → still `UNKNOWN`) — Review Focus #2.
  - `modifierProductNotOnMenu`: link → product id 2 where product 2 is `is_modifier = true` → not `ON_MENU` — Review Focus #3.
  - `zeroPriceProductNotOnMenu`: link → product id 3 where product 3 has `sell_price_inc_tax = 0` → not `ON_MENU` — Review Focus #3.
  - `noLinksYieldsUnknown`: recipe with no links → `UNKNOWN` (unchanged behaviour).

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.reconciliation.RecipeProjectorTest'`
Expected: the new tests FAIL (current logic uses `isRecipeActive`).

- [ ] **Step 3: Implement the projector change**

In `recomputeAll()`: build `Set<Integer> sellableProductIds` = ids of current products where `!isModifier` and `sellPriceIncTax != null && > 0`. Replace the `isRecipeActive` split with: for each recipe, `present` = any link whose `lightspeedProductId` is non-null and in `sellableProductIds`. `menuState = any present ? ON_MENU : (hasLinks ? (allLinksHaveProductId ? OFF_MENU : UNKNOWN) : UNKNOWN)`. Keep the `folder`/price/cost enrichment logic unchanged (folder from links; price/cost from the existing name-based `priceIndex()`). Add `@EventListener ProductRecorded` to `RecipeProjectionListener` → `projector.recomputeAll()`.

- [ ] **Step 4: Run the projector test class**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.reconciliation.RecipeProjectorTest'`
Expected: PASS (new + pre-existing).

- [ ] **Step 5: Run the full backend gate**

Run: `cd backend && ./gradlew test spotlessCheck`
Expected: green (no canonical/API boundary regressions; `ArchitectureBoundariesTest` unchanged — the new `CanonicalProductQuery` is canonical, read only by reconciliation/connectors).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjector.java backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjectionListener.java backend/src/test/java/com/goldys/platform/reconciliation/RecipeProjectorTest.java
git commit -m "feat(menu-source): derive recipe menu_state from product presence"
```

---

## Rollout (after all tasks)

1. Deploy the migrations (Flyway owns the schema; `hibernate.ddl-auto=validate`).
2. On startup, `ProductAliasSeeder` seeds the alias table; `StartupProjectionSeeder` rebuilds projections (including the recipe projection, now presence-based).
3. Drop the current product CSV once (`POST /api/connectors/lightspeed-products/upload`); the ingest records the snapshot, closes absent products, matches links, and the projector rebuilds.
4. Verify on the Recipes screen: the still-sold items that were wrongly `OFF_MENU` now read `ON_MENU`; the confirmed retirements read `OFF_MENU`; unmatched read `UNKNOWN`.
