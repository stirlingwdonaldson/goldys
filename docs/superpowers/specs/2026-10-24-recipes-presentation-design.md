# Recipes presentation cleanup

Status: implemented locally on `codex/recipes-presentation`. Reviewed 24 October 2026 (Australia/Melbourne).
Planning baseline: origin/main `45534db6`; implementation baseline `bf3ddd43`. Review used an isolated source snapshot;
the shared production checkout was not switched or edited.

## Problem and outcome

Recipes currently exposes source structure rather than a recipe workflow. The list
has too many financial columns, a three-line coverage cell, and a separate Ingredients
button that appends overlapping details below the table. The detail page renders
flattened source paths, repeated method/cost information, every refresh attempt and
unchecked lookup options. A user should instead find a recipe, understand its yield,
read its method and ingredients, follow its relationships, and inspect provenance
when needed.

The scope includes the list, recipe details, ingredient/sub-recipe presentation,
relationships, optional sections, evidence and demo parity. The table requirement is
application-wide: no wrapping in any table column, including headers and raw HTML tables.

## Evidence inspected

Read architecture/system context, PRD, current architecture, backend invariants,
source-access and matching rules, UI direction, current recipe routes/components,
typed API contracts, resolved queries, tests and retained real CTB response fixtures.
Production checks used the existing `docs/audits/production-data/checks/probe.py`
wrapper: BEGIN READ ONLY, default_transaction_read_only=on, statement timeout 8s,
lock timeout 1s. No environment files or credentials were read.

- 670 complete resolved definitions; 139 have explicit sub-recipes; 55 have neither
  direct-stock nor sub-recipe lines. An empty captured composition is not a failed fetch.
- 218 explicit usage lines; five negative quantities; none marked broken in this sample.
- 663 older resolved costing rows: 435 CURRENT, 25 HISTORICAL, 203 NONE. All carry
  UNKNOWN menu availability. These are underlying row counts, not final API totals:
  the existing definition overlay includes seven additional definitions (IDs 722–728).
- All 14 per-recipe section families have 670 successful resolved observations.
  Successful capture does not establish useful content, current availability or safe food advice.
- 24 descriptions, including 13 stock-export descriptions; only 11 other descriptions.
  Examples include Bloody Oath Syrup #241 and Espresso Martini Batch #219.
- Two recipes have dietary selections; no recipe has selected allergens. All recorded
  nutrition numbers are zero. Equipment and ingredient-method sections have no populated
  rows. Global equipment options are available choices, not assigned recipe equipment.
- Checked current resolved data for #6, #314, #377, #401, #485, #497 and #498, and the
  retained populated raw usage response for #401. Also inspected captured raw header,
  direct-stock, usage and optional-section formats.

Representative cases:

| Recipe | Presentation needed |
| --- | --- |
| Belvedere Martini #314 | Three direct ingredients: 10 g lemons, 10 ml vermouth, 60 ml vodka; reported batch cost $5.56. Supplier/pack facts belong behind ingredient detail. |
| Fairy Bread Pudding #401 | Two sub-recipe rows: 200 g pudding mix #498 ($2.397142…), 50 ml custard #497 ($0.44875); displayed reported total $2.85. |
| Vanilla custard #497 | Batch yield 1 × 1200 ml, reported batch cost $10.77, direct ingredients and reverse parent links. |
| Eggplant Parma special #485 | No direct stock lines, one explicit child recipe #389. It is not missing its ingredients. |
| Free Time Keg 50lt #6 | One stock-derived recipe, 50 litre yield and $200 batch cost; stock-export text must not masquerade as cooking instructions. |
| July roast – Vegan loaf #377 | Two direct ingredients plus two child recipes. CTB selects Dairy Friendly and Gluten Friendly, but not Vegan; never infer suitability from its name. |
| no salad #652 | Negative Mustard Dressing quantity recorded in litres; show as a source adjustment without silently correcting the questionable unit. |

## Proposed list

- Retain `/recipes`, shared DataTable sorting/search/pagination/column visibility,
  and existing `/recipes/[recipeId]` deep links.
- Default columns: Recipe, Folder, Yield, CTB cost/portion, POS price observation,
  Details status. Align numbers, format currency to two decimals and pair quantities
  with units. Preserve accessible full names when truncating.
- Use one short coverage badge (e.g. Details received / Legacy only / Details missing),
  with a short freshness indicator if needed. Dates, source-selection explanation and
  detailed coverage belong in a keyboard/touch-accessible popover or recipe details.
  Complete structure must not appear as a green assertion of correct costs or food safety.
- Rename misleading POS labels. CURRENT is a price-source classification, not proof
  of a current menu listing. Historical values retain their date; unavailable stays absent.
- Move POS cost and cost-gap/margin/GP diagnostics out of default columns. Current
  recipe query intentionally returns null for incomparable derived figures; do not
  recreate these calculations in the browser. Replace empty Avg GP/Best margin tiles
  with factual counts supported by the list response, or remove them.
- Add folder and details-status filters from existing list fields. Do not issue one
  detail request per row. Folder breadcrumb actions filter the list, using an exact
  folder label available in the current contract; they do not invent a hierarchy.
- Recipe links provide one consistent entry to details. Remove the appended duplicate
  Ingredients panel/button flow. Preserve list filter/page state across navigation.

## Proposed recipe detail

Keep the existing full detail route; do not introduce another drawer/navigation model.
Use one composed recipe view with:

1. **Header:** recipe name, folder breadcrumb, yield, source identity and compact
   freshness/coverage status. Keep internal IDs in secondary metadata, except where
   needed to distinguish duplicate names. Do not treat `active` as POS availability.
2. **Ingredients and method:** readable direct ingredient rows and explicit sub-recipe
   rows, with name, quantity/unit and reported contribution. Child names link by recipe
   ID. Clearly identify negative adjustments. Render method paragraphs/list text once,
   plus menu description and hints where supplied. Long prose wraps outside tables.
   Keep parsed plain text; do not render arbitrary source HTML.
3. **Costing:** group reported batch/per-portion costs and labour, separately from
   recomputed estimates and POS observations. Keep GST basis visible with the price.
   Explain unavailable calculations and source discrepancies using existing issue codes.
   Display rounding never changes calculation inputs. Existing backend calculations
   remain authoritative; no new portion scaling or financial arithmetic.
4. **Related recipes:** show “Uses” through ingredient rows and “Used in” as linked
   parent names. Use explicit IDs, never fuzzy name matching. Do not render the same
   child list twice. Missing child details remain an explained state. Preserve source
   line order and distinct repeated usages even when names are identical.
5. **Dietary and other details:** selected dietary/allergen labels as compact chips;
   all-unselected means “None selected in CTB”, never “Allergen free”. Distinguish no
   observation, an empty result, all-zero values, stale values and failed refresh.
   Nutrition gets a compact all-zero/unverified-basis explanation and expandable values.
   Selected cooking facts become names, not IDs/order/boolean triplets. Empty optional
   sections collapse to a concise summary; lookup options remain in evidence.
6. **Source details:** one secondary disclosure containing per-section captures,
   refresh attempts/failures, original fields/values, field paths, source selection,
   calculation inputs and authorized raw links. Preserve all existing business fields;
   progressive disclosure changes their placement, not their availability. Important
   failures/staleness remain visible in the primary view with a link to the explanation.

Supplier names and stock codes remain useful ingredient details. There is no existing
stock/supplier detail route: `/products` is a classification workspace, not a CTB stock
identity page. Do not create misleading links or a new stock-management feature.
Existing POS associations stay qualified; no new name-based association is introduced.

## Implementation boundary

Use existing getRecipes/getRecipeComposition/getRecipeDetails contracts. Typed header,
stock and usage records drive the primary view. A small frontend presentation mapper
groups known flattened optional fields by exact section/path and source array index;
preserve false, zero, empty and null. Unknown fields remain in source details.
Use existing format helpers and UI components. Do not duplicate financial calculation,
reconciliation, connector parsing or permission logic. No migration or connector work
is expected. Do not add new calls/contracts unless implementation proves a concrete gap.

## Global table policy

Apply the default to shared table headers/cells and cover raw HTML tables as well.
Audit existing wrapping overrides and intentionally stacked cell content: inherited
`white-space: nowrap` alone does not remove block elements or explicit line breaks.
Turn stacked metadata into inline content or move it to a disclosure outside the row.
Retain readable typography, bounded name truncation and accessible full values.
Wide tables scroll within their own container; the page itself must not overflow.
Narratives and explanatory detail stay outside tables and can wrap normally.

This affects shared DataTable consumers, generic Data Explorer tables, sales and invoice
details, query results, reports/widgets and hand-written tables. Ripwire could not
resolve the React.forwardRef TableCell declaration; the caller inventory came from
static imports/markup search and requires visual checks during implementation.

## Acceptance

- No table header/cell text wraps at desktop or 390px viewport; no explicit stacked
  data cells remain. Horizontal scrolling is local to the table, not the document.
- #401 → #497/#498 → parent navigation works with browser Back, keyboard and direct URLs.
- #314 displays three direct ingredient rows; #485 displays its child despite no stocks;
  #6 displays a stock-derived recipe without fabricated cooking instructions.
- No primary-view payload paths, repeated technical lookup lists, duplicate method text
  or repeated ingredient tables. All existing source business fields remain inspectable.
- #377 reflects its stored selections; unchecked allergens and zero nutrition never
  imply safety/suitability. Negative source quantities and questionable units stay visible.
- Missing/legacy/capture-empty, stale and refresh-failed states remain distinguishable;
  fresh catalogue metadata never makes old ingredient costs look fresh.
- Live/demo and authorized/denied/loading/error states remain supported.

## Limits of the original planning pass

Verified source and production data through read-only SQL, not an authenticated browser
session or live REST responses. No current-layout screenshots or rendered measurements
were taken. No code changes, test runs, commits, pushes or deployments occurred.
Primary risk is application-wide table width; secondary risk is accidentally removing
meaningful absence/provenance states while simplifying the UI.

## Implementation

The list and detail routes now use existing resolved recipe APIs with explicit sub-recipe
links, legacy cost fallback, optional-data summaries and collapsed source evidence.
List filters live in the URL and propagate through child links. The opted-in shared table
retains client sort/visibility/page state and clamps restored pages for smaller result sets.
Native table call sites now use the shared scroll container. Global headers/cells do not
wrap; narrative text outside tables can wrap. No backend or permission changes were needed.

Browser coverage uses seven real, redacted resolved recipe observations (rendering fixtures,
not raw evidence), plus demo data. All browser API calls are intercepted locally.
Verification results are recorded in the companion implementation plan.
