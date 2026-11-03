# New identities and ongoing review — 3 November 2026

Current main/PR state refreshed: `28751470365d6011276c4fb340f0cb5dce9aabea`, PR #231 merged.
Work is isolated in `/tmp/goldys-reporting-review`, branch `codex/reporting-review-queue`.
The production checkout, real environment files and production Compose stack were untouched.

## Production curation actually applied

All 15 decisions in `decisions.json` were applied through the authenticated ALL/OWNER
application API with CSRF protection and `goldys-maintenance/1.0` User-Agent. Every identity
was previewed first, then every saved decision was read back. The 293 previous API revisions
remained equal; there are now 308. No direct database writes occurred. SQL observations used
the established audit probe with `default_transaction_read_only=on`, explicit READ ONLY
transaction, statement/lock timeouts, and rollback.

All new decisions are effective **2 November 2026**: they explicitly restate that completed
day and apply going forward. Earlier sightings are retained as identity evidence, not a reason
to backfill earlier reporting. Unsupported stock bucket, item role, business area and lifecycle
remain UNKNOWN. Coffee/café inference, category corrections and model approvals are unchanged.

| Exact CTB site 0 code (CTB_PRODUCT) | Nature | Independent corroboration |
|---|---|---|
| 20161229 | FOOD | Gnocchi Pumpkin Butter Sage; POS Weekly Food Specials; recipe unlinked |
| 21162117 | FOOD | Gnocchi Bolognese; Gnocchi Menu recipe 410; POS Weekly Food Specials |
| 23673809 | FOOD | Fish Tostada POS FOOD MENU; older Tuna Tostada link 666 is also food, not identity equivalence |
| 19989720 | BEVERAGE | Four Pillars Gin Martini; POS COCKTAILS; recipe 316 gin/vermouth |
| 22857471 | BEVERAGE | Moorak Blanc de Blanc; POS WINE BOTTLE; recipe 52 wine |
| 12027390 | BEVERAGE | Glenfiddich 30ml; POS SPIRITS; recipe 175 whisky |
| 23643649 | BEVERAGE | Stargazer Tupelo Gris 2025; POS WINE BOTTLE; recipe unlinked |
| 23562304 | BEVERAGE | Chalmers Vermentino 2025; POS WINE BOTTLE; recipe 570 wine |
| 17878130 | BEVERAGE | Manhattan; POS COCKTAILS; recipe 330 bourbon/vermouth/bitters |
| 12027397 | BEVERAGE | Makers Mark 30ml; POS SPIRITS; recipe 185 bourbon |
| 17914368 | BEVERAGE | Amaretto 30ml; POS LIQUEURS; recipe 198 Disaronno |
| 20824947 | FOOD | Red Wine Jus Side; POS FOOD MENU; recipe 632 Extras and sides |
| 22510826 | FOOD | **Owner explicitly confirmed exact identity in this chat**; POS Uncategorised and no verified recipe link |

Full entity keys are `site:0:code:8:<code>`. Name-based POS corroboration and code-only CTB
links are not asserted to establish cross-source/site identity equivalence. The owner's
confirmation supplied the missing business fact for Dill Aioli; its 9.24 initially remained
UNKNOWN until that response arrived. Separate aioli recipes were inspected without joining
inventory by name.

**CTB_RECIPE 655 → BEVERAGE:** exact line `wastage:171:914` records recipe 655, Altbier pint;
its complete definition contains stock 2897, Dusseldorf Draught Altbier 4.8% ABV 50L Keg,
supplier The Mill Brewery. **CTB_RECIPE 173 → BEVERAGE:** exact line `wastage:171:915` records
recipe 173, Four Pillars Rare Dry Gin; definition stock 737 is Four Pillars Rare Dry Gin
700 ml, supplier Paramount Liquor. No prior recipe/line policies or withdrawals existed.
Recipe nature is supported for future observations. Historical unit costs **3.41 and 2.52**
and quantity **1 each**, every recorded reason, and unverified tax basis remain unchanged;
current recipe costs (notably gin 2.76) do not replace recorded historical cost.

## Before and after, read back from the deployed report

| 2 November | Before | After |
|---|---:|---:|
| Sales classification coverage | 87.284597% | 100% |
| Unclassified sales ex GST | 2,107.5662 | 0 |
| Food sales ex GST | unavailable | 6,581.6762 |
| Beverage sales ex GST | unavailable | 9,993.23 |
| Unclassified recorded wastage | 5.93 | 0 |
| Food recorded wastage | 0 | 0 |
| Beverage recorded wastage | 35.28 | 41.21 |
| Beverage recorded wastage / beverage sales | unavailable | 0.412379% |
| Food recorded wastage / food sales | unavailable | 0% |

Wastage classification coverage increased from 7/9 lines (77.777778%) and 85.610289%
of recorded value to 9/9 lines and 100% of recorded value. Reason coverage remains 9/9.
Weekly food/beverage target comparisons now have actuals (food difference -39,018.3238;
beverage difference -64,406.77 against the existing full-week targets), qualified for the
incomplete week. They are not daily allocated targets.

Daily actuals and category wastage rates are available; incomplete week totals/rates remain
qualified/unavailable according to existing completeness rules. Total recorded waste/sales
stays 0.248409%; reason coverage is 100% for the nine recorded lines. Daily target differences
are still unavailable because daily allocation remains out of scope. The report exposes the
existing weekly comparisons; classification does not supply missing future dates or targets.

The 26 October–1 November report is unchanged: food 48,442.7826, beverage 92,564.8493,
unclassified sales 82.71, food recorded wastage 24.8493, beverage 535.8560, unclassified
wastage 0. The **604.7852** headline residual remains visible and unallocated. The separate
2 November residual is **1.9538**, also unchanged. No 3 November sales were present in the
fresh observation. The 22 zero-value unknown modifier identities remain unclassified; there is no remaining
positive-value hold on 2 November. Earlier dates' holds are not backfilled.

## Owner workflow and alert choice

In Products → Reporting mappings, select a range of up to 92 days and refresh the queue.
Sales rank by absolute eligible ex-GST exposure; a separate missing-tax/unresolved view
shows known gross and unquantified observations. Wastage ranks exact lines by known absolute
recorded value, retaining missing costs and dates. The current canonical schema requires a wastage date: source records without a parsable date
remain ingestion failures for Data health review and cannot be assigned to this date-bounded
queue. Missing report days remain missing; the queue also retains null-date guards without
inventing a date. Pages contain 50 identities, with deterministic ordering.
Evidence and applicable policies describe observations in the selected range; revisions and
UNKNOWN withdrawals remain visible. Names never select a stock identity.

Choose an identity (or recorded recipe), review explicit dimensions and effective date, enter
a reason, preview the impact, then explicitly approve. Choosing another identity invalidates
the preview; every selector locks during in-flight work. Saving reloads queue and coverage.
No model or policy is automatically approved. Below-95% dates with exposure are the minimal
alert implementation. Background monitoring/notifications would need additional delivery and
operational infrastructure and are deliberately not introduced.

Queue code is **local only, not pushed or deployed**. Applied mappings are already live in the
existing deployed reporting code. The 95% sales threshold and existing allocation guards are
unchanged. Validation results and remaining limitations are recorded in `validation.md`.
