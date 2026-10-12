# Recipe costing gap recovery — design

- **Date:** 2026-10-13
- **Status:** design — approved 2026-10-13; awaiting implementation plan.
- **Related:** `docs/superpowers/specs/2026-10-12-recipe-composition-design.md` (the domain this
  extends), `docs/superpowers/specs/2026-10-12-recipe-ingredients-design.md` (the ingredient slice),
  `docs/connectors/line-item-matching.md` (the CTB↔Lightspeed name key).

## Context

The Recipes page shows a costing table per recipe: cost/portion, actual cost, cost gap, menu price,
margin, and GP%. Cost/portion and yield are complete (CTB supplies them for all 663 recipes), but
menu price and actual cost — and the margin/GP%/cost-gap that cascade from them — are missing for
**252 of 663 recipes (38%)**. Those recipes render a bare `—`, which reads as broken data rather than
"no price on record".

Menu price and actual cost are already derived in `RecipeProjector`: each recipe is matched to its
Lightspeed sale items via the CTB sale-recipe link (`canonical_sale_recipe_link`) and a normalized
name key (`ProductNameKey.normalize`), then the modal `sold_price_inc_tax` / `cost_inc_tax` is taken.
The gap is a **join failure**, not absent source data in every case: some recipes genuinely have no
price (they are off-menu or were never sold under a matchable name), but some are recoverable from
data we already hold. This spec closes the recoverable part, labels the genuinely-priceless part
instead of dashing it, and surfaces a last-known price where one exists.

## Evidence (production read-only, 2026-10-13)

Aggregates only, via `docs/audits/production-data/checks/probe.py` (read-only session).

| Figure | Populated | Missing |
|---|---|---|
| Cost per portion | 663/663 (100%) | 0 |
| Cost incl. labour | 663/663 | 0 |
| Yield (`portion_size`) | 663/663 | 0 |
| Menu price | 411/663 (62%) | 252 |
| Actual cost | 411/663 (62%) | 252 |

Sale-recipe links are fully ingested (`ctb-sale-recipe-links` raw: 44 records → 645 canonical rows,
445 distinct recipes; 474 active, 167 inactive). The 252 recipes without a price break down as:

| Bucket | Count | Meaning |
|---|---|---|
| No link at all | 218 | never mapped to a sellable item → off-menu / sub-recipe / never sold |
| Linked, `is_recipe_active = false` | 26 | explicitly off the menu |
| Linked and active, name mismatch | 8 | alias / suffix / punctuation gap (see below) |

Cross-checks:

- **24** of the no-price recipes have a recipe *name* that matches a Lightspeed sale item name — the
  projector only matches on the link description, so these are recoverable by also matching the recipe
  name.
- The 8 active-but-mismatched links are all alias/suffix cases: `Baba Ganoush` ↔ `Eggplant Dip`,
  `Dolin Dry Vermouth - 45ml` vs `Dolin Dry Vermouth`, `Goldy's! 'An Old One' … Shandy Jug` vs the
  Lightspeed spelling, `Charred Corn Salad` ↔ `Corn Estiques`. Pure normalization does not bridge
  aliases (`Baba Ganoush` → `Eggplant Dip` is a synonym, not a typo).
- Historical fallback is viable: **160 of 167 inactive-link descriptions match a Lightspeed item
  name**, so a last-known price is derivable for the off-menu recipes.

## Goal

Recover the menu price / actual cost where the data already exists (match on recipe name as well as
the link description), replace the silent `—` for the rest with an explicit menu-state label, and
show a clearly-marked last-sold price for off-menu dishes. Preserve the platform's "never fabricate"
rule: every displayed price carries an explicit provenance, and a derived/historical figure is
distinguished from a current one.

## Non-goals

- **No cost/yield imputation** — cost per portion and yield are already 100% present; nothing to do.
- **No alias/synonym map** (`Baba Ganoush` = `Eggplant Dip`). Auto-guessing synonyms is unsafe; a
  human-maintained alias table is recorded as future work, not this slice.
- **No ingredient-level roll-up** of cost — CTB does not report sub-recipe usage quantities, so a
  roll-up is incomplete; the CTB `cost_per_portion` is authoritative and complete.
- **No change to canonical tables.** This is read-model + projection + semantic/API + UI only. The
  bitemporal canonical layer is untouched.

## Design

### 1. Migration (`V51__recipe_cost_price_provenance.sql`)

Add columns to the disposable `resolved_recipe` read model (rebuilt by the projector; no canonical
change, no backfill):

- `menu_state varchar(16) NOT NULL DEFAULT 'UNKNOWN'` — `ON_MENU` | `OFF_MENU` | `UNKNOWN`.
- `price_source varchar(16) NOT NULL DEFAULT 'NONE'` — `CURRENT` | `HISTORICAL` | `NONE`.
- `last_sold_price numeric(14,4)` (nullable).
- `last_sold_cost numeric(14,4)` (nullable).
- `last_sold_date date` (nullable).

The `DEFAULT` satisfies existing rows during the `ALTER` (the table may hold 663 rows when the
migration runs); the projector overwrites both columns with the real value on the next rebuild.
The `menu_price` / `actual_cost` columns keep their existing meaning — the **current** price/cost —
and are never overwritten with historical values.

### 2. Projector changes (`RecipeProjector`)

The projector already loads current sale-item prices by normalized name and computes a modal price
and cost. Extend it without changing the single-source resolution shape:

**a. Sale-item index carries dates.** Extend `SaleItemPriceView` (and `CanonicalSaleItemQuery`) to
also expose `trading_date`, so the projector can compute both a modal price (across all history) and
the most-recent trading date for the "last sold" fallback. This is a read-facade change; the
`canonical_sale_item` entity is unchanged.

**b. Match on recipe name as well as the link description.** For each recipe, build the candidate
name set = `{normalized link.saleItemStockDescription for ACTIVE links} ∪ {normalized recipe.recipeName}`,
then resolve the modal price/cost across every matching sale item (same `ProductNameKey.normalize`,
including the leading-`"New "` alias). This recovers the ~24 name-match recipes without changing the
existing link-based behaviour.

**c. Derive `menu_state` from links only** (CTB is the authority on menu membership):

- ≥1 active link → `ON_MENU`.
- links present but none active → `OFF_MENU`.
- no link → `UNKNOWN`.

**d. Derive `price_source` and the last-sold figures:**

- If a current price/cost resolves (active-link description *or* recipe-name match) → `CURRENT`,
  `menu_price`/`actual_cost` set, last-sold columns null.
- Else if a price resolves from an **inactive** link's description (or recipe name) → `HISTORICAL`,
  `last_sold_price`/`last_sold_cost`/`last_sold_date` = the modal price/cost among the sale items on
  the most recent `trading_date`, `menu_price`/`actual_cost` null.
- Else → `NONE`, all four price columns null.

`resolution_type` stays `SINGLE_SOURCE`, `authoritative_source` stays `CTB` (menu price/actual cost
are still Lightspeed enrichment joined in the projector; the provenance of *which* price is shown is
now explicit via `price_source`).

### 3. Semantic layer (`RecipeCosting`)

Extend the `RecipeCosting` record (semantic) with the new fields and derive the display figures on
the effective price (current if present, else last-sold):

```
recipeId, name, folder, portionSize, costPerPortion,
menuPrice, actualCost,            // current (unchanged meaning)
lastSoldPrice, lastSoldCost, lastSoldDate,
menuState, priceSource,
costGap,                          // (actualCost ?? lastSoldCost) - costPerPortion
margin,                           // (menuPrice ?? lastSoldPrice) - costPerPortion
gpPercent                         // margin / (menuPrice ?? lastSoldPrice)
```

`margin`/`gpPercent`/`costGap` remain `null` when there is no effective price (`price_source = NONE`)
— never a fabricated zero. When `price_source = HISTORICAL` they are computed off the last-sold price
and the UI flags the row accordingly. `RecipeSummary` (raw facts) is unchanged: it still exposes the
current `menuPrice`/`actualCost` only.

### 4. API + frontend

- `RecipeController` / `RecipeReportingService` are unchanged in shape (`GET /api/recipes` still
  returns `RecipeCosting[]`); the record simply carries the new fields. No new permission resource
  (`recipes.cost` already covers the read) and no new AI tool.
- `GetRecipeCostingTool` may optionally note off-menu/last-sold rows; not required for this slice.
- Frontend `RecipeCosting` type + demo fixtures gain the new fields; the Recipes page renders:
  - `price_source = CURRENT` → normal cells.
  - `price_source = HISTORICAL` → price/cost dimmed with a badge **"Last sold <date>"**; margin/GP%
    stay visible but the row carries the same "last sold" treatment.
  - `price_source = NONE` → a menu-state label instead of `—`: "Off menu" (`OFF_MENU`), "Not on
    menu" (`UNKNOWN`), or "No price matched" (`ON_MENU`).
  - A small footnote under the table states that "last sold" figures are historical, not current.

## Testing

Per the existing recipe-domain tests, extended:

- Projector: recipe-name match recovers a price (no link); active-link match unchanged; inactive-link
  → `HISTORICAL` with last-sold price/date; alias gap stays `NONE`/`ON_MENU`; `menu_state` derivation
  for the three cases.
- Semantic: `margin`/`gpPercent`/`costGap` on current vs historical vs no effective price (never a
  fabricated zero).
- Frontend: the three render states (current / last-sold / no-price label) in the page test.
- Integration via Testcontainers; never against production.

## Rollout

1. Deploy `V51` (Flyway owns the schema; `hibernate.ddl-auto=validate`).
2. Projector rebuilds `resolved_recipe` at startup with the new columns (disposable projection; the
   existing `StartupProjectionSeeder` / projection listeners run it).
3. Frontend Recipes page reads the extended `RecipeCosting`; demo mode uses fixtures.

## Decisions & dependencies

- **`menu_state` is derived from CTB links only**, while `price_source` reflects where the displayed
  price came from. A recipe with no link but a name-matched current sale item shows a real price
  (`CURRENT`) but `UNKNOWN` menu state — the two axes answer different questions ("is it on the
  menu?" vs "where did the price come from?").
- **Current and historical prices are stored separately.** `menu_price`/`actual_cost` are never
  overwritten with a last-sold value, so an "as-of" read can still distinguish "on sale now at X"
  from "last sold at Y".
- **Alias matching is out of scope** and recorded as future work (a human-maintained
  `recipe_alias`/synonym mapping), not auto-guessed in the projector.
- **The Lightspeed sale-item name key** continues to be `ProductNameKey.normalize` (including the
  `"New "` alias), unchanged from the recipe-composition slice; this spec widens which CTB fields are
  matched (recipe name in addition to link description), not the key itself.
