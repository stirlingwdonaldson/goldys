# Recipe composition (canonical) — design

- **Date:** 2026-10-12
- **Status:** design — approved 2026-10-12; awaiting implementation plan.
- **Related:** `docs/superpowers/specs/2026-10-12-ctb-detail-backfill-design.md` (Slice A — the raw
  source this builds on), `docs/connectors/matching-and-identity.md` (recipe-composition identity),
  `docs/connectors/line-item-matching.md` (CTB↔Lightspeed name key),
  `docs/architecture/adding-a-domain.md` (the domain recipe this follows).

## Context

The CTB web pull ingests the inventory/purchasing master data **raw-only** (recipes, stocks,
suppliers, stocktakes, wastage, stock orders, statements, reference data). The line detail behind the
header-only datasets was captured by Slice A (`CtbDetailBackfillConnector`, merged): stocktake
detail, wastage detail, recipe ingredients, and CTB's batched derived recipe cost — all raw ledger
only, no canonicalization.

This spec is **Slice D** of the food-cost goal defined in the Slice-A spec:

| Slice | What | Status |
|---|---|---|
| A | Ingest the line detail (raw only) | merged |
| B | Canonical stock count | deferred |
| C | Canonical wastage | deferred |
| **D** | **Canonical recipe composition (new domain)** | **this spec** |
| E | Usage / food-cost analysis (theoretical vs actual) | not started |

The business goal: surface per-dish cost (recipe-level, not ingredient lines), gross margin, and the
theoretical-vs-actual cost gap on the Recipes page (currently an `AwaitingData` placeholder). CTB is
the single authority for the *theoretical* cost of a dish;
Lightspeed is the single authority for the *actual* sale price. They are complementary, not
conflicting, so this domain is **single-source** — no reconciliation engine, no override table — but
it still follows the canonical → resolved → semantic → application read direction.

## Evidence (decoded from the production raw ledger, read-only, 2026-10-12)

| Raw fetcher | Source endpoint | Shape |
|---|---|---|
| `ctb-recipes` | `RecipeBook/GetAllRecipes` | `{data:[{recipeId, recipeName}]}` — 663 rows, **no cost** |
| `ctb-sale-recipe-links` | `searchDistinctSaleItemsForLinking` | `{data:[{saleItemRecipeId, saleItemStockCode, saleItemStockDescription, recipeId, recipeName, recipeFolderName, isRecipeActive}]}` — 639 links / 445 recipes |
| `ctb-recipe-cost` | `RecipeBook/GetSelectedRecipeFullRecords` (batched `recipeIds` JSON array + `recipeFolderIds=[]`) | 663 records in one call; fields `costPerPortion`, `costPerPortionIncludingLabour`, `totalCost`, `portionSize` (verified in the Slice-A spec) |
| `ctb-recipe-ingredients` | `RecipeBook/GetIngredientsIncludingSubRecipeIngredients` (one per recipe) | per-ingredient `costPerQty`, `costPerUnit`, `portionWeight`, `isSubRecipeIngredient`, `isBrokenLink` — **out of scope this slice** |

Price side (already canonical): `canonical_sale_item` carries `item_name`, `sold_price_inc_tax`,
`cost_inc_tax`, `total_tax`. The CTB↔Lightspeed product key is the normalized name
(`ProductNameKey.normalize` + a leading-`"New "` alias), which `line-item-matching.md` measured at
285/303 products matching to the cent.

Data-quality note: CTB JSON payloads contain literal `\u0000` escape sequences (observed in
`ctb-stocks`; Postgres `jsonb` rejects `\u0000`). Parsers must neutralise these before storing into
`text`/`varchar` columns.

**Prerequisite:** the production `ctb-detail-backfill` run closed `PARTIAL` (2026-10-12 07:02,
fetched=1/persisted=1, `1 failure(s) recorded`) — the ledger has `ctb-stocktake-detail` but **no
`ctb-recipe-cost` / `ctb-recipe-ingredients` yet**. Completing that backfill (and diagnosing its
failure) is owned by another agent; this slice assumes `ctb-recipe-cost` is present in the raw
ledger before canonicalization runs.

## Goal

Canonicalize CTB recipe composition (recipe-level cost + sale↔recipe links) as a single-source
domain, project it to a resolved read model, expose it through a semantic query and a REST/AI-tool
surface, and render a live Recipes page showing cost-per-portion, gross margin, and the
theoretical-vs-actual cost gap per dish.

## Non-goals

- Ingredient-level lines (`ctb-recipe-ingredients`) and sub-recipe recursion — deferred to a later
  ingredient slice.
- Stock-count (Slice B) and wastage (Slice C) canonicalization.
- Daily-incremental scheduling of the detail pull (Slice-A future work).
- Full "usage over time" analysis (margin trends, weekly food cost) — Slice E.
- Cross-source reconciliation or manual overrides (single-source domain).

## Design

### 1. Migration (`V44__recipe_composition.sql`)

Three tables, mirroring `V22__inventory.sql` / `V20__reservation_read_model.sql`.

**`canonical_recipe_cost`** — one bitemporal row per recipe per observed cost:

- `id uuid PK`, `logical_entity_id uuid NOT NULL`, `source_system varchar(255) NOT NULL`,
  `source_record_ref varchar(512) NOT NULL`, `raw_record_id uuid NOT NULL REFERENCES raw_record(id)`,
  `recipe_id integer NOT NULL`, `recipe_name varchar(512)`,
  `cost_per_portion numeric(14,4)`, `cost_per_portion_incl_labour numeric(14,4)`,
  `total_cost numeric(14,4)`, `portion_size varchar(255)`, `portion_size_value numeric(14,4)`,
  `valid_from` / `valid_to` / `recorded_at` / `superseded_at` (the standard bitemporal columns).
- `source_record_ref = 'recipe-cost:' || recipe_id`.
- Unique index `(source_system, source_record_ref) WHERE superseded_at IS NULL`.

**`canonical_sale_recipe_link`** — one row per menu-item→recipe link:

- `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`,
  `sale_item_recipe_id integer`, `sale_item_stock_code varchar(255)`,
  `sale_item_stock_description varchar(512)`, `recipe_id integer NOT NULL`,
  `recipe_name varchar(512)`, `recipe_folder_name varchar(512)`, `is_recipe_active boolean`,
  bitemporal columns.
- `source_record_ref = 'sale-recipe-link:' || sale_item_recipe_id`.
- Unique index as above.

**`resolved_recipe`** — disposable, recomputable, one row per recipe:

- `recipe_id integer PRIMARY KEY`, `recipe_name varchar(512)`, `recipe_folder_name varchar(512)`,
  `cost_per_portion numeric(14,4)`, `cost_per_portion_incl_labour numeric(14,4)`,
  `total_cost numeric(14,4)`, `portion_size varchar(255)`, `portion_size_value numeric(14,4)`,
  `menu_price numeric(14,4)`, `actual_cost numeric(14,4)`,
  `resolution_type text NOT NULL`, `authoritative_source text NOT NULL`,
  `resolved_at timestamptz NOT NULL`.

### 2. Canonical entities + ingest facade + trigger

Mirror `CanonicalInvoice*`:

- `RecipeCostInput` / `RecipeCostRecorded` (record types), `CanonicalRecipeCost` (extends
  `BitemporalEntity`), `CanonicalRecipeCostRepository` (extends `BitemporalRepository`),
  `CanonicalRecipeCostService` (record + lock + publish), `RecipeCostIngest` (public facade),
  `CanonicalRecipeCostQuery` / `View` (read facade).
- The same set for `CanonicalSaleRecipeLink`.
- Identity: `logical_entity_id = UUID.nameUUIDFromBytes("recipe:" + recipeId)` for the cost entity;
  `"sale-recipe-link:" + saleItemRecipeId` for the link. Source identity is
  `(source_system=CTB, source_record_ref)` as above.
- The ingest facades read the already-ingested raw records (by `fetcher_identity` =
  `ctb-recipe-cost` / `ctb-sale-recipe-links`), parse with `\u0000` neutralisation, and record via
  the standard close-and-reinsert supersession. Re-running the backfill (which appends duplicate raw
  records) is safe: canonicalization dedupes on `source_record_ref`.

**Canonicalization trigger:** `canonical_sale_recipe_link` is recorded during the scheduled
`CtbConnector` fetch — its `ctb-sale-recipe-links` pull already runs every cycle, so links stay
fresh. `canonical_recipe_cost` has no scheduled source (the `ctb-recipe-cost` raw is backfill-only),
so a flag-gated one-off replay runner (mirroring `CtbDetailBackfillRunner`) replays the
`ctb-recipe-cost` raw records into canonical after the backfill completes. Re-run is idempotent
(`source_record_ref` dedupe).

### 3. Resolved projection + projector

- `ResolvedRecipe` + `ResolvedRecipeRepository` (disposable).
- `RecipeProjector` (`recomputeAll`/`recompute`) + `RecipeProjectionListener` (on write events),
  mirroring `ReservationProjector`/`InventoryProjector`. `resolved_recipe` = latest current
  `canonical_recipe_cost` joined to the active `canonical_sale_recipe_link` (folder). The projector
  also enriches each recipe with its Lightspeed price and actual cost: it aggregates
  `canonical_sale_item` by `ProductNameKey.normalize(item_name)` → the most common
  `sold_price_inc_tax` (→ `menu_price`) and `cost_inc_tax` (→ `actual_cost`), then maps each recipe
  to its menu item via `canonical_sale_recipe_link.sale_item_stock_description` (same key, `"New "`
  alias). Doing the cross-source join here keeps the read path resolved-only. Single-source
  (`resolution_type='SINGLE_SOURCE'`, `authoritative_source='CTB'`). Register in
  `StartupProjectionSeeder`.

### 4. Semantic query

- `RecipeMetricsQuery` (leaf interface) — `List<RecipeSummary> recipes()` and
  `List<RecipeCosting> costing()`. `RecipeSummary(recipeId, name, folder, costPerPortion,
  costPerPortionInclLabour, totalCost, portionSize, portionSizeValue, menuPrice, actualCost)`;
  `RecipeCosting` adds the derived `margin` (`menuPrice − costPerPortion`), `gpPercent`
  (`margin / menuPrice`), and `costGap` (`actualCost − costPerPortion`), computed in the semantic
  layer (all inputs are single-domain resolved facts after projector enrichment). Implemented by
  `ResolvedRecipeQuery` in `reconciliation`.

### 5. Application service + margin

- `RecipeReportingService` (application) — authorizes `recipes.cost` READ and returns
  `List<RecipeCosting>` from `RecipeMetricsQuery.costing()`. No request-time cross-domain join: the
  price/cost enrichment lives in the projector, and the margin/cost-gap derivation in the semantic
  layer.
- **Price/cost source (decision):** Lightspeed *actual* sold price and *actual* item cost, joined by
  normalized name (`ProductNameKey.normalize` + `"New "` alias), using the **mode** (not the mean —
  avoids skew from voids/discounts). Stored as `menu_price` / `actual_cost` on `resolved_recipe`.
- A recipe with no matched price/cost renders `null` margin/cost-gap with a "no price" notice
  (never a fabricated zero). The per-portion grain assumes one sold item equals one recipe portion
  (the standard venue assumption); recorded as an approximation, not silently asserted.

### 6. API, tool, permissions

- `RecipeController` — `GET /api/recipes` (the costing report). Thin adapter; reads only the
  application service.
- `GetRecipeCostingTool` (`ToolId.GET_RECIPE_COSTING`, resource `recipes.cost`) + `ToolInput` — Ask
  Goldy's answers "what's my margin per dish?". The tool injects `RecipeMetricsQuery` (semantic),
  reads `costing()`, and renders a table widget directly; `toMetricQueries` returns empty (recipe
  master data is not a time-series metric, so the widget is not re-runnable from a saved dashboard).
- Migration `V45__seed_recipe_permissions.sql` seeds `recipes.cost` for `ALL × OWNER` only
  (BOH/FOH grants wait on the field-to-role matrix). Reserve `recipes.ingredients` for the later
  ingredient slice.
- Extend `ArchitectureBoundariesTest` with the `recipe*` package rules (controller not canonical;
  tool semantic-only; `RecipeMetricsQuery` implemented in `reconciliation`).

### 7. Frontend

Replace `frontend/app/(app)/recipes/page.tsx` (the `AwaitingData` placeholder) with a live page:

- A table (recipe, folder, cost/portion, actual cost, cost gap, menu price, margin $, GP%) and two
  KPI tiles (average GP%, highest/lowest margin dish), following the `Reservations`/`Staff` page
  patterns.
- Add `getRecipes` to the typed `Api` contract (`frontend/lib/api/index.ts`, `live.ts`, `demo.ts`,
  `types.ts`) with live + demo implementations, and a Vitest page test.

## Testing

Per `adding-a-domain.md` §4, adapted to a single-source domain:

- Canonical ingest: single-source record, supersession on a changed cost (bitemporal history),
  missing `cost_per_portion` (nullable), `\u0000` neutralisation.
- Resolved query: latest-cost-wins, folder join, missing link (null folder).
- Margin + cost gap: name-match join (incl. the `"New "` alias), most-common price/cost,
  unmatched→null.
- Permission boundary (`recipes.cost` READ).
- Integration via Testcontainers; never against production.

## Rollout

1. (External) the `ctb-detail-backfill` completes — owned by another agent — so `ctb-recipe-cost`
   and `ctb-sale-recipe-links` are present in the raw ledger.
2. Deploy the migration (Flyway owns the schema; `hibernate.ddl-auto=validate`).
3. Canonicalization runs via the scheduled `CtbConnector` (links) and the one-off replay runner
   (cost); the projector seeds `resolved_recipe` at startup.
4. Frontend Recipes page reads `GET /api/recipes`; demo mode uses fixtures.

## Future work (recorded intent — not this slice)

- Ingredient-level lines + sub-recipe recursion (`ctb-recipe-ingredients`) as a
  `recipes.ingredients` slice.
- Margin/cost-gap *trends over time* and usage analysis (Slice E) — this slice shows the
  point-in-time gap only.
- Daily-incremental detail scheduling (Slice-A future work).

## Decisions & dependencies

- **`portion_size` is stored faithfully as `varchar(255)` plus a nullable parsed
  `portion_size_value numeric(14,4)`** — higher fidelity (the raw value survives whether it is a
  count, a weight, or a UOM string) and extensible (a later slice can add UOM columns without a
  backfill).
- **Cost gap is in scope:** the report surfaces both gross margin (vs sold price) and the
  theoretical-vs-actual cost gap (CTB `cost_per_portion` vs Lightspeed `cost_inc_tax` per portion).
- **The `ctb-detail-backfill` completion is an external dependency**, owned by another agent. This
  slice assumes `ctb-recipe-cost` is present in the raw ledger before canonicalization runs; the
  PARTIAL-run diagnosis is out of scope here.
