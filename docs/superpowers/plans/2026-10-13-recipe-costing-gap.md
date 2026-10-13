# Recipe Costing Gap Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover missing menu price / actual cost on the Recipes screen (widen the price join, label off-menu recipes, add a last-sold fallback) with explicit provenance on every displayed figure.

**Architecture:** Add five provenance columns to the disposable `resolved_recipe` read model, extend the `RecipeProjector` to match recipes by name as well as link description and to derive a `menu_state` + `price_source`, extend the semantic `RecipeCosting` record so derived margin/GP%/cost-gap use the effective (current-or-last-sold) price, and render the provenance in the frontend. No canonical-table change; the resolved-only read direction is preserved.

**Tech Stack:** Java 25 / Spring Boot 3.5 / JPA + Flyway (`hibernate.ddl-auto=validate`) / PostgreSQL 16 (Testcontainers); frontend Next.js + React + TypeScript + Vitest.

**Spec:** `docs/superpowers/specs/2026-10-13-recipe-costing-gap-design.md`

## Global Constraints

- `hibernate.ddl-auto=validate`: every entity column must exist in a Flyway migration; never edit an applied migration.
- Resolved projections are disposable and rebuilt by projectors — never store information *only* in a resolved row.
- Business consumers read resolved data through `semantic` query interfaces, never `canonical` or the raw ledger.
- Nulls are honest "unknown", never a fabricated zero.
- `menu_state` values: `ON_MENU` | `OFF_MENU` | `UNKNOWN` (from CTB links only).
- `price_source` values: `CURRENT` | `HISTORICAL` | `NONE`.
- Name key is `ProductNameKey.normalize` (unchanged); this spec widens *which* CTB fields are matched, not the key.
- No alias/synonym map; no cost/yield changes (both already 100% present).

## Review Focus

The failure modes below are implied by the spec but not exercised by any task's happy-path tests. Each is pinned by a test in the owning task.

1. **Active + inactive link on one recipe** — must resolve `CURRENT` + `ON_MENU`, never `HISTORICAL` (Task 2).
2. **Active link whose description does not match, but the recipe name does** (the alias-adjacent "Dolin Dry Vermouth - 45ml" case) — the recipe-name fallback must still resolve a real price, never leave it null when a match exists (Task 2).
3. **Sale item with a null `trading_date`** — last-sold derivation must ignore null dates, never NPE (Task 2).
4. **No link and no name match** — `price_source = NONE`, `menu_state = UNKNOWN`, and margin/GP%/cost-gap null (Tasks 2 and 3).
5. **Zero effective price with a present last-sold** — GP% must be null (divide-by-zero guard), not `Infinity` (Task 3).

---

### Task 1: Schema and read-model surface (plumbing)

Add the five provenance fields end-to-end as neutral values, so the code compiles and all existing tests stay green. No behaviour change yet.

**Files:**
- Create: `backend/src/main/resources/db/migration/V51__recipe_cost_price_provenance.sql`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/SaleItemPriceView.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalSaleItemQuery.java:36-40`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedRecipe.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjector.java:76-91`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/RecipeCosting.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedRecipeQuery.java:50-66`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/RecipeProjectorTest.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ResolvedRecipeQueryTest.java`
- Test: `backend/src/test/java/com/goldys/platform/reporting/GetRecipeCostingToolTest.java`
- Test: `backend/src/test/java/com/goldys/platform/application/RecipeReportingServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/api/RecipeControllerTest.java`

**Interfaces:**
- Produces (used by later tasks): `SaleItemPriceView(String itemName, BigDecimal soldPriceIncTax, BigDecimal costIncTax, LocalDate tradingDate)`; `ResolvedRecipe` constructor gains `String menuState, String priceSource, BigDecimal lastSoldPrice, BigDecimal lastSoldCost, LocalDate lastSoldDate` inserted after `actualCost` and before `resolutionType`, with getters `menuState()`, `priceSource()`, `lastSoldPrice()`, `lastSoldCost()`, `lastSoldDate()`; `RecipeCosting` gains the trailing fields `String menuState, String priceSource, BigDecimal lastSoldPrice, BigDecimal lastSoldCost, LocalDate lastSoldDate`.

- [ ] **Step 1: Write the migration**

`V51__recipe_cost_price_provenance.sql`:

```sql
-- Recipe costing provenance: menu state, price source, and a last-sold fallback on the
-- disposable resolved read model. No canonical change; rebuilt by the projector.
ALTER TABLE resolved_recipe
    ADD COLUMN menu_state varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN price_source varchar(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN last_sold_price numeric(14,4),
    ADD COLUMN last_sold_cost numeric(14,4),
    ADD COLUMN last_sold_date date;
```

- [ ] **Step 2: Add `tradingDate` to `SaleItemPriceView`**

Record fields become `(String itemName, BigDecimal soldPriceIncTax, BigDecimal costIncTax, LocalDate tradingDate)`, importing `java.time.LocalDate`.

- [ ] **Step 3: Populate `tradingDate` in `CanonicalSaleItemQuery.currentSaleItemPrices()`**

`.map(s -> new SaleItemPriceView(s.itemName(), s.soldPriceIncTax(), s.costIncTax(), s.tradingDate()))`.

- [ ] **Step 4: Add fields to `ResolvedRecipe`**

Columns `menu_state varchar(16) NOT NULL`, `price_source varchar(16) NOT NULL`, `last_sold_price numeric(14,4)`, `last_sold_cost numeric(14,4)`, `last_sold_date date`; constructor params + getters as in the Interfaces block. `menuState`/`priceSource` map to `@Column(nullable = false)`.

- [ ] **Step 5: Extend `RecipeCosting`**

Append the five fields from the Interfaces block; keep existing fields and order unchanged.

- [ ] **Step 6: Thread neutral values through `RecipeProjector`**

In `recomputeAll()`'s `new ResolvedRecipe(...)` call, pass `"UNKNOWN"`, `"NONE"`, `null`, `null`, `null` for the five new args (keep the existing menu-price/cost enrichment logic unchanged).

- [ ] **Step 7: Thread pass-through values through `ResolvedRecipeQuery.toCosting`**

Populate `r.menuState()`, `r.priceSource()`, `r.lastSoldPrice()`, `r.lastSoldCost()`, `r.lastSoldDate()` into the new `RecipeCosting` fields; leave `margin`/`gpPercent`/`costGap` computed exactly as today (off `r.menuPrice()` / `r.actualCost()`).

- [ ] **Step 8: Update the five test files' constructors**

- `RecipeProjectorTest`: add a `LocalDate` arg to every `new SaleItemPriceView(...)` (e.g. `LocalDate.of(2026, 10, 1)`).
- `ResolvedRecipeQueryTest`: extend the `recipe(...)` helper to pass `"ON_MENU"`, price-derived `"CURRENT"`/`"NONE"`, `null`, `null`, `null`.
- `GetRecipeCostingToolTest`, `RecipeReportingServiceTest`, `RecipeControllerTest`: add the five new args to their `new RecipeCosting(...)` calls (use `"ON_MENU"`, `"CURRENT"`, `null`, `null`, `null`).

- [ ] **Step 9: Run the full backend gate**

Run: `./gradlew test spotlessCheck`
Expected: all green (behaviour unchanged).

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(recipes): add price provenance columns to resolved read model"
```

---

### Task 2: Projector — menu state, price source, and last-sold (TDD)

Implement the matching and provenance logic in `RecipeProjector`.

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjector.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/RecipeProjectorTest.java`

**Interfaces:**
- Consumes: `SaleItemPriceView` (with `tradingDate`), `ResolvedRecipe` (with the five new fields) from Task 1.
- Produces: correctly-populated `menu_state`, `price_source`, `last_sold_price`, `last_sold_cost`, `last_sold_date` on each `ResolvedRecipe`.

**Algorithm (the one body the signature and tests don't determine):**

```java
// Index sale items by normalized name. Per name keep: modal price/cost (across all items)
// and the most-recent trading date plus the modal price/cost among items on that date.
private record PriceInfo(BigDecimal price, BigDecimal cost,
                         LocalDate lastSoldDate, BigDecimal lastSoldPrice, BigDecimal lastSoldCost) {}

// For each recipe cost:
//   links = ALL current links for the recipe (active and inactive), grouped by recipeId.
//   active   = links with isRecipeActive != FALSE
//   inactive = links with isRecipeActive == FALSE
//   menuState = !active.isEmpty() ? ON_MENU : (!links.isEmpty() ? OFF_MENU : UNKNOWN)
//   folder    = active.isEmpty() ? null : active.get(0).recipeFolderName()   // unchanged
//
//   currentCandidates  = normalize(active[].saleItemStockDescription) + normalize(recipeName)
//   (menuPrice, actualCost) = mode across byName.get(c) for c in currentCandidates
//
//   historicalCandidates = normalize(inactive[].saleItemStockDescription) + normalize(recipeName)
//   lastSoldDate  = max over historicalCandidates of byName.get(c).lastSoldDate (skip null)
//   lastSoldPrice / lastSoldCost = mode among candidates whose lastSoldDate == max date
//
//   if menuPrice != null      -> priceSource = CURRENT,  menuPrice/actualCost set, lastSold* null
//   else if lastSoldPrice != null -> priceSource = HISTORICAL, menuPrice/actualCost null,
//                                    lastSoldPrice/lastSoldCost/lastSoldDate set
//   else -> priceSource = NONE, all four price columns null
```

`mode(List<BigDecimal>)` already exists and returns first-seen on ties; reuse it. `ProductNameKey.normalize` is the matching key. Use literal strings `"ON_MENU"`/`"OFF_MENU"`/`"UNKNOWN"` and `"CURRENT"`/`"HISTORICAL"`/`"NONE"`.

- [ ] **Step 1: Write failing tests**

Add to `RecipeProjectorTest` (with a shared `LocalDate d = LocalDate.of(2026, 10, 1)`):

```java
@Test void recipeNameMatchWithoutLinkSetsCurrentPriceAndUnknownState() {
  // cost(1, "Parma", "6.00"); links empty; sale item ("Parma", 18.50, 6.00, d)
  // assert menuPrice 18.50, actualCost 6.00, priceSource "CURRENT", menuState "UNKNOWN",
  //        lastSoldPrice null
}

@Test void recipeNameRecoversWhenLinkDescriptionDoesNotMatch() {   // Review Focus #2
  // cost(1, "Dolin Dry Vermouth", "6.00");
  // link(1,"Dolin Dry Vermouth","Mains","Dolin Dry Vermouth - 45ml", true);
  // sale item ("Dolin Dry Vermouth", 9.00, 3.00, d)
  // assert priceSource "CURRENT", menuState "ON_MENU", menuPrice 9.00, lastSoldPrice null
}

@Test void noLinkNoNameMatchYieldsNoneAndUnknown() {               // Review Focus #4
  // cost(1, "Pickle Brine", "6.00"); links empty; sale item ("Something Else", 5.00, 2.00, d)
  // assert priceSource "NONE", menuState "UNKNOWN", menuPrice null, actualCost null,
  //        lastSoldPrice null
}

@Test void inactiveLinkYieldsHistoricalPriceAndOffMenuState() {
  // cost(1, "Parma", "6.00"); link(1,"Parma","Mains","Parma", false); sale item ("Parma",18.50,6.00,d)
  // assert menuPrice null, priceSource "HISTORICAL", menuState "OFF_MENU",
  //        lastSoldPrice 18.50, lastSoldCost 6.00, lastSoldDate == d
}

@Test void aliasGapStaysNoneAndOnMenu() {
  // cost(1,"Baba Ganoush","6.00"); link(1,"Baba Ganoush","Mains","Eggplant Dip", true);
  // sale item ("Something Else", 5.00, 2.00, d)
  // assert priceSource "NONE", menuState "ON_MENU", menuPrice null, lastSoldPrice null
}

@Test void activeAndInactiveLinksPreferCurrent() {           // Review Focus #1
  // active link "Parma" + inactive link "Parma"; sale item ("Parma", 18.50, 6.00, d)
  // assert priceSource "CURRENT", menuState "ON_MENU", menuPrice 18.50, lastSoldPrice null
}

@Test void nullTradingDateIgnoredInLastSold() {              // Review Focus #3
  // inactive link "Parma"; sale items ("Parma",18.50,6.00,null) and ("Parma",20.00,7.00,d)
  // assert lastSoldDate == d, lastSoldPrice 20.00
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests 'com.goldys.platform.reconciliation.RecipeProjectorTest'`
Expected: the five new tests FAIL (assertion mismatch / null where a value is expected).

- [ ] **Step 3: Implement the algorithm in `RecipeProjector`**

Replace the `priceModes()`/`mode()` construction with the `PriceInfo` index, and rewrite the per-recipe loop per the algorithm block above. Keep `recomputeAllRebuildsCleanly` semantics (delete-then-insert).

- [ ] **Step 4: Run the projector test class**

Run: `./gradlew test --tests 'com.goldys.platform.reconciliation.RecipeProjectorTest'`
Expected: PASS, including the pre-existing tests.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(recipes): derive menu state, price source and last-sold in projector"
```

---

### Task 3: Semantic derivation on the effective price (TDD)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedRecipeQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ResolvedRecipeQueryTest.java`

**Interfaces:**
- Consumes: `ResolvedRecipe` with `menuPrice`/`actualCost` (current) and `lastSoldPrice`/`lastSoldCost`/`lastSoldDate` (historical).
- Produces: `RecipeCosting` where `margin = (menuPrice ?? lastSoldPrice) − costPerPortion`, `gpPercent = margin / (menuPrice ?? lastSoldPrice)`, `costGap = (actualCost ?? lastSoldCost) − costPerPortion`.

- [ ] **Step 1: Write failing tests**

Add to `ResolvedRecipeQueryTest` (extend the `recipe(...)` helper with an overload that sets `menuState`/`priceSource`/`lastSold*`):

```java
@Test void costingUsesLastSoldPriceWhenNoCurrent() {
  // recipe: menuPrice null, actualCost null, lastSoldPrice 18.50, lastSoldCost 7.20,
  //         lastSoldDate 2026-09-14, cost 6.00
  // assert margin 12.50, gpPercent 0.6757, costGap 1.20
}

@Test void costingNullWhenNoEffectivePrice() {               // Review Focus #4
  // recipe: menuPrice null, lastSoldPrice null, cost null
  // assert margin null, gpPercent null, costGap null
}

@Test void costingNullGpWhenEffectivePriceZero() {           // Review Focus #5
  // recipe: menuPrice null, lastSoldPrice 0.00, cost 6.00
  // assert margin -6.00, gpPercent null, costGap null (actualCost also null)
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests 'com.goldys.platform.reconciliation.ResolvedRecipeQueryTest'`
Expected: the new tests FAIL (today margin/GP%/costGap ignore `lastSold*`).

- [ ] **Step 3: Implement in `ResolvedRecipeQuery.toCosting`**

Compute `effectivePrice = menuPrice != null ? menuPrice : lastSoldPrice`, `effectiveCost = actualCost != null ? actualCost : lastSoldCost`, then `margin = subtract(effectivePrice, cost)`, `gpPercent = divide(margin, effectivePrice)`, `costGap = subtract(effectiveCost, cost)` — reusing the existing `blank`/`subtract`/`divide` helpers unchanged.

- [ ] **Step 4: Run the query test class**

Run: `./gradlew test --tests 'com.goldys.platform.reconciliation.ResolvedRecipeQueryTest'`
Expected: PASS.

- [ ] **Step 5: Run the full backend gate**

Run: `./gradlew test spotlessCheck`
Expected: green.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(recipes): derive margin and GP% from the effective price"
```

---

### Task 4: Frontend — types, demo, and rendering

**Files:**
- Modify: `frontend/lib/api/types.ts` (the `RecipeCosting` interface)
- Modify: `frontend/lib/api/demo.ts` (`getRecipes`)
- Modify: `frontend/app/(app)/recipes/page.tsx`
- Test: `frontend/app/(app)/recipes/recipes-page.test.tsx`

**Interfaces:**
- Consumes: the extended `RecipeCosting` shape from the backend: `menuState: string; priceSource: string; lastSoldPrice: number | null; lastSoldCost: number | null; lastSoldDate: string | null`.

- [ ] **Step 1: Extend the `RecipeCosting` type**

Add `menuState: string; priceSource: string; lastSoldPrice: number | null; lastSoldCost: number | null; lastSoldDate: string | null;` to the interface in `types.ts`.

- [ ] **Step 2: Update demo fixtures**

Update the four existing `getRecipes()` fixtures with the new fields (`menuState: "ON_MENU", priceSource: "CURRENT", lastSoldPrice: null, lastSoldCost: null, lastSoldDate: null`), and add two more:
- `{ recipeId: 5, name: "Sticky Date Pudding", folder: "Dessert", portionSize: "6", costPerPortion: 1.9, actualCost: null, costGap: null, menuPrice: null, margin: null, gpPercent: null, menuState: "OFF_MENU", priceSource: "HISTORICAL", lastSoldPrice: 11.0, lastSoldCost: 2.1, lastSoldDate: "2026-09-14" }`
- `{ recipeId: 6, name: "Pickle Brine", folder: "Prep", portionSize: "1", costPerPortion: 0.8, actualCost: null, costGap: null, menuPrice: null, margin: null, gpPercent: null, menuState: "UNKNOWN", priceSource: "NONE", lastSoldPrice: null, lastSoldCost: null, lastSoldDate: null }`

- [ ] **Step 3: Render provenance in `page.tsx`**

Add a helper `menuStateLabel(menuState: string): string` returning `"Off menu"` for `OFF_MENU`, `"Not on menu"` for `UNKNOWN`, `"No price matched"` otherwise. In the costing table's "Menu price" cell: when `priceSource === "HISTORICAL"` render `formatCurrency(lastSoldPrice)` plus a dimmed ` · Last sold {lastSoldDate}`; when `priceSource === "NONE"` render `menuStateLabel(menuState)`; otherwise `formatCurrency(menuPrice)`. Do the same effective-value treatment for "Actual cost" (`actualCost ?? lastSoldCost`), and add one footnote line under the table: "Last-sold figures are historical, not current prices." The margin/GP%/cost-gap columns render the values already computed by the backend.

- [ ] **Step 4: Update and extend the page test**

Update the `parma` fixture with `menuState: "ON_MENU", priceSource: "CURRENT", lastSoldPrice: null, lastSoldCost: null, lastSoldDate: null`. Add a test that sets `state.recipes` to the off-menu fixture and asserts `screen.getByText(/Last sold/)` and `screen.getByText("Off menu")` render.

- [ ] **Step 5: Run the frontend gate**

Run: `bun run typecheck && bun run lint && bun run test && bun run build`
Expected: green (run `test` before `build`; they race on `.next/types`).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(recipes): show menu state and last-sold price on the Recipes page"
```
