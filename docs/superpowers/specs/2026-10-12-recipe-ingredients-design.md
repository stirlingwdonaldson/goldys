# Recipe ingredients (canonical → resolved → API → tool → UI) — design

- **Date:** 2026-10-12
- **Status:** design — implemented this slice.
- **Related:** `docs/superpowers/specs/2026-10-12-recipe-composition-design.md` (Slice D — this is
  its direct sequel), `docs/connectors/matching-and-identity.md` (recipe-ingredient identity),
  `docs/connectors/source-access.md` (ingestion + scope limitations),
  `docs/architecture/adding-a-domain.md` (the domain recipe this follows).

## Context

Slice D canonicalized CTB **recipe-level cost** (`canonical_recipe_cost`, `canonical_sale_recipe_link`,
`resolved_recipe`) and rendered the Recipes page. This slice makes the deferred **ingredient lines**
first-class: CTB's `ctb-recipe-ingredients` raw payloads (663, one per recipe, backfilled
2026-10-12) become a canonical entity, a resolved read model, a REST endpoint, an Ask Goldy's tool,
and an ingredient breakdown on the Recipes screen.

CTB is the single authority for per-recipe ingredient composition; there is no second source, so
this is a **single-source** domain (no reconciliation engine, no override table).

## Verified source facts (production ledger, read-only, 2026-10-12)

- Fetcher `ctb-recipe-ingredients`, 663 payloads; `source_record_ref = "recipe:{parentRecipeId}"`.
- Envelope `{"data":[lines],"message":{"IsSuccess","Info"},"total":n}`.
- Each line: `recipeDetailId` (OWNER-scoped line id), `recipeId`+`recipeName` (the recipe the line
  *belongs to* — main or sub-recipe), `stockId`, `stockCode`, `stockDesc`, `ingredientDescription`,
  `supplierName`, `supplierStockCode`, `portionWeight`, `unitOfMeasurementName`,
  `stockUomShortname`, `costPerQty`, `costPerUnit`, `stockUsable`, `stockWastage`,
  `isSubRecipeIngredient`, `isBrokenLink`, plus nutrition/method fields we do not store.
- Volume: 2,634 lines across 611 recipes (52 empty payloads); 1,247 main, 1,387 sub-recipe.

Data quirks handled:

1. **Exact duplicate lines** — recipe 617 repeats 4 lines verbatim (same `recipeDetailId` AND
   `stockId`). The parser collapses exact duplicates; current-fact uniqueness lives on
   `(source_system, source_record_ref)`, never on `(parent_recipe_id, recipe_detail_id)`.
2. **Literal `\u0000` escapes** in CTB strings — stripped before any `text`/`varchar` write.
3. **`PDF_<hex>` placeholder stock codes** (~30% of lines) — descriptions are real; the code is
   stored faithfully, the description is the display identity.
4. **16 lines with `portionWeight <= 0`** — stored faithfully (an observation), never clamped.
5. **Sub-recipe lines carry the sub-recipe's own quantities**, not scaled by the parent, and there
   is **no field for how much sub-recipe the parent uses**.

## Scope (v1)

**In:** per-recipe ingredient composition — main + sub-recipe lines, each line's raw qty/unit, CTB
cost figures, supplier, and `is_sub_recipe` — via REST (`GET /api/recipes/{id}/ingredients`), Ask
Goldy's (`get_recipe_ingredients`), and the Recipes screen.

**Out (explicit, not silently dropped):**

1. **Sub-recipe cost roll-up** — needs the missing parent usage quantity. Composition is stored;
   any true ingredient-level cost is left out. A short spike may probe
   `RecipeBook/GetJITRecipeTreeNodes` (no captured schema) and record findings in
   `source-access.md`; no production writes.
2. **Ongoing/incremental freshness** — v1 backfills from the *existing* raw ledger only; the
   one-off `ctb-detail-backfill` is not scheduled (Slice-A future work).

## Design

Follows `adding-a-domain.md` and mirrors Slice D shapes.

- **Migration** `V49__recipe_ingredients.sql`: `canonical_recipe_ingredient` (bitemporal, unique
  current fact on `(source_system, source_record_ref)`, index on `parent_recipe_id`) and
  `resolved_recipe_ingredient` (disposable projection). The resolved row identity is a **surrogate
  `id uuid`** — the natural key `(parent_recipe_id, line_recipe_id, recipe_detail_id, stock_id)` is
  impossible because `line_recipe_id` and `stock_id` are nullable. `V50__seed_recipe_ingredients_permission.sql`
  seeds `recipes.ingredients` for `ALL × OWNER` only.
- **Canonical** `RecipeIngredientInput` → `CanonicalRecipeIngredient` (logical id
  `"recipe-ingredient:{parent}:{detail}:{stock}"`) → `RecipeIngredientIngest` /
  `CanonicalRecipeIngredientQuery` / `RecipeIngredientRecorded`.
- **Parser** `CtbRecipeIngredientsParser`: envelope-aware, strips `\u0000`, defensive
  `BigDecimal` parse, dedupes exact duplicates, non-JSON throws `CONNECTOR_SCHEMA_MISMATCH`.
- **Replay** `RecipeIngredientCanonicalizationRunner` (`app.recipe-ingredients-canonicalize`,
  `@Order(HIGHEST_PRECEDENCE)`) reads the existing `ctb-recipe-ingredients` raw records (deriving
  `parentRecipeId` from `source_record_ref`) and records once; idempotent via `source_record_ref`.
- **Projection** `RecipeIngredientProjector.recomputeAll()` + `RecipeIngredientProjectionListener`,
  registered in `StartupProjectionSeeder`. Resolved tables are disposable.
- **Semantic** `RecipeIngredientMetricsQuery.ingredientsFor(recipeId)` (leaf) → `RecipeIngredient`,
  implemented by `ResolvedRecipeIngredientQuery`.
- **Application/API** `RecipeIngredientReportingService` (authorizes `recipes.ingredients` READ) +
  `RecipeIngredientController`.
- **Tool** `GetRecipeIngredientsTool` (`ToolId.GET_RECIPE_INGREDIENTS`, `get_recipe_ingredients`,
  input `{recipeId, limit?}`), table columns recipe/ingredient/qty/unit/cost/supplier +
  `is_sub_recipe`; `toMetricQueries()` empty (master data, not a metric).
- **Frontend** `RecipeIngredient` + `getRecipeIngredients` on the typed `Api` (live + demo), and an
  ingredient breakdown (detail panel) on the Recipes page with an empty state for the 52 no-line
  recipes.

## Non-goals

- Sub-recipe cost roll-up and ongoing freshness (see Scope, above).
- Canonicalizing stocktake/wastage detail (slices B–C).
- Cross-source reconciliation or manual overrides (single-source domain).

## Testing

Per `adding-a-domain.md` §4, adapted to a single-source domain: canonical ingest (new fact,
supersession, same-fact no-op, deterministic id, batch single event), parser (fields, parent-vs-line
recipe, `\u0000`, exact-duplicate dedupe, garbage numeric → null, sentinel stock id → null, non-JSON),
runner (replay + unparseable-ref skip), projector (pass-through + clean rebuild), resolved query,
application permission boundary, controller, tool (columns, limit, resource, empty `toMetricQueries`),
`ArchitectureBoundariesTest` rules, and the `recipes.ingredients` permission-boundary case.
