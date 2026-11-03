# CTB stock-count recovery and source-cost reporting — 3–4 November 2026

Base `b2377f97`, isolated worktree `/tmp/goldys-stock-cogs`, branch
`codex/ctb-stock-cogs`. Production checkout and environment files were not changed.

## Discovery

Read-only CTB requests used the backend container's configured credentials internally;
no credential or session value was printed or saved with the evidence. Source requests
use the existing session login, AJAX headers and date-only/searchType=-1 contract.
Database observations used the existing SELECT-only production probe with read-only
transaction/session settings, statement/lock timeouts and rollback.

- `Stocktake/SearchStocktakes`: 30/30 headers. All 30 parse using the existing Java
  parser, including the English weekday dates. Latest stocktake 40 is dated 26 October.
- Stocktake 40 detail: three pages, 200 + 200 + 43 = 443 advertised/returned rows;
  443/443 parsed, no repeated line identities. Sanitised headers remove staff names,
  free-text descriptions and nested templates. Line fixture retains only parsed
  fields and the observed custom count quantities.
- Source line `subTotal` sum is 29,722.104716666666546; header total is
  29,722.104907936508. The small residual is retained, not allocated or used to
  rewrite source values. Four-decimal canonical values have their own rounding.
- The live sale-report sample covers all seven dates, 26 October–1 November:
  1,034 advertised/returned/parsed rows. Each matches an independently retained
  source observation on date, code, quantity, amount and tax in the PostgreSQL
  regression (1,034/1,034). No missing page is assumed to be empty.
- Every sampled `totalFoodCost` agrees with `quantitySold * costPerUnit` within
  retained precision. Only 569/1,034 rows have positive unit costs; many have zero
  costs and no recipe link. Agreement does not establish cost completeness or
  historical recipe effectiveness. The original JSON is retained compressed as
  `fixtures/ctb/reporting-cost-week-2026-10-26.json.gz`.
- `Sale/GetVarianceReportData` still returns unsuccessful `message.IsSuccess=false`,
  null data and zero total for the week. It is not an empty successful variance report.

## Recovery

Before the user-triggered CTB run, production had V111 applied but **zero current
stocktake headers and count lines**. The latest completed CTB run preceded deployment
of the stock-count refresh. This was not evidence of a broken live CTB endpoint.
The user clicked the existing CTB **Run now** action; the run started at
2026-11-03 12:22:58 UTC (23:22:58 Melbourne). It uses the existing source lock,
raw-first ingestion, bitemporal facades and complete-page checks. No direct SQL
writes, source-system writes, replay flags or deployment operations were performed.

The run completed **PARTIAL** at 12:52:21 UTC (23:52:21 Melbourne on 3 November),
with 361 retained payloads. Thirty headers and **23 stocktakes / 8,575 count lines**
are now live, covering 25 May–26 October. Latest stocktake 40 has 443/443 valued
lines totalling **29,722.1046** in the resolved reporting view. No 2 November
stocktake exists in the source header sample. None of this establishes an expected
book balance, stock variance or inventory-period COGS.

Aggregate checks over authoritative raw bytes inside the read-only database found
78 detail pages, **12,279 advertised/returned rows**, and **63 exact repetitions**
(nine each for stocktakes **8, 10, 12, 15, 18, 20, 27**), all across pages. There
were zero conflicting observations for those repeated identities. The deployed
refresh rejects those seven stocktakes with `Stocktake pages repeat a count identity`;
its failure is observable in the PARTIAL run, not an empty success.

A live aggregate-only follow-up inside the backend container requested `limit=1000`
for each held stocktake. Their response sizes were respectively:

| Stocktake | Advertised | Returned / unique in large response |
|---|---:|---:|
| 8 | 528 | 519 |
| 10 | 526 | 517 |
| 12 | 534 | 525 |
| 15 | 522 | 513 |
| 18 | 522 | 513 |
| 20 | 549 | 540 |
| 27 | 523 | 514 |

Every large-response population is nine short of its advertised count. No conflicting
identities were observed within these responses. Agreement of the unique counts does
not prove why CTB overstates the total or whether nine records are omitted. Consequently,
the existing unique-population guard is retained. The only refresh-code change clarifies
that repeated identities leave the unique source population unverified. A regression
checks that exact repeated rows cannot stand in for a complete unique population and
that raw pages survive the rejection. No canonical identity or source-count rule changes.

The captured pages contain 12,216 unique identities, including **3,641 held observations**;
these are not a certified complete population and are not published. The supported 23
stocktakes are restored, while the other seven require CTB source reconciliation.
The existing 308 reporting mapping revisions remain unchanged.

Automatic approval review rejected a proposed bulk raw-payload export before it ran,
because it would transfer unnecessary production operational data locally. Final
reconciliation used only aggregate results computed inside the database; no approval
for that export was requested because it was unnecessary.

## Reporting contract

V112 adds only the `resolved_reporting_cost` read projection. CTB source-native
cost observations are grouped against the same selected product/day source as the
sales report, with exact gross/quantity equality, non-null single site and existing
matched-tax basis. A selected Lightspeed/aggregate source cannot borrow CTB costs.
Current recipe costs, similarly named recipes and invoice purchases are not substitutes.

Supported rows need known quantity, nonnegative unit cost, supplied total cost,
matching cost/revenue/quantity signs, and quantity × unit cost within one cent of the
reported total (four-decimal source-measure rounding, not entity matching). Nonzero
quantity with zero unit cost remains unsupported, including free modifiers with
unverified consumption. A source observation with zero quantity, zero revenue and
zero total cost may supply an observed zero.

Known food, beverage and combined food/beverage cost subtotals and observation-based
cost coverage are separate from complete theoretical COGS. Unknown classification
is never allocated. Unmatched product/day aliases count as one unsupported observation
in coverage; matched aliases count their recorded source rows. Coverage is not a
percentage of physical stock or spend, nor proof of source completeness.

Daily complete estimates require all selected category rows costed, no unknown
classification and matching net-sales evidence. Weekly complete estimates require
all seven dates; missing dates are not zero. Ratios use matching category sales,
exclude OTHER and require positive revenue. The existing owner recipe-cost ex-GST
assumption (`CTB_RECIPE_COSTS_EXCLUDE_GST`) is explicit; withdrawing it withholds rates.
These are CTB-reported theoretical estimates as observed, not historical actual COGS
or a certification of recipe versions, yields or tax. Wastage is not deducted again.

For the retained week, reviewed food known cost is **14,143.34**, beverage **4,771.40**,
combined **18,914.74**. Food cost coverage is 76.095618%, beverage 53.571429%, combined
59.888268% of reviewed-category observations. The raw CTB cost sum is 19,060.18;
145.44 remains outside the reviewed food/beverage subtotal (54.00 reviewed OTHER and 91.44 UNKNOWN). Full weekly COGS and
rates remain unavailable, as do YTD (history/year boundary unestablished) and stock
variance (expected movements, valuation and reviewed stock buckets unestablished).
All 308 existing reporting mapping revisions had UNKNOWN stock buckets at discovery.

The existing weekly endpoint, generic table and Excel export carry the same 85 rows.
Demo shows known partial costs/coverage and withholds complete COGS. Ordinary sales
metrics retain their original cost-free query. Cost-bearing reads occur only after
the weekly application's existing `inventory.cost` and other permission checks.

## Validation and release

- Final `./gradlew test spotlessApply spotlessCheck`: passed, **1,869 tests, zero
  failures/errors, five skipped**, across 404 suites with PostgreSQL 16 Testcontainers.
  This includes the conservative held-population regression and completed in 5m 47s.
  Earlier full gates also passed after the cost-query plan fix.
- `bun install --frozen-lockfile` and `bun run typecheck && bun run lint && bun run test && bun run build`:
  passed; 137 Vitest files / 743 tests.
- `GOLDYS_REAL_WEEK_REPORT=... bunx playwright test tests/browser/weekly-report.spec.ts`:
  four passed (desktop 1280px, phone 390px, denial and real-week report/download).
- Frozen-snapshot real-week browser rerun: one passed. The existing independent
  openpyxl verifier passed all 85 rows, daily values, totals, nulls, status, notes,
  generation timestamp and summary, with no formulas. Freezing avoids comparing
  different generation timestamps while another backend test regenerates its artifact.
- SELECT-only candidate CTE on live production reproduced food 14,143.34 and beverage
  4,771.40, including 191/251 and 345/644 costed observations. The maximum 13-week
  candidate query also completed under the audit probe's eight-second statement bound.
  Neither query installed V112 or wrote data.
- Ripwire contract/blast-radius, quality-delta and test-gate scans ran. The added
  demo branches were moved into the existing per-section helper pattern. Remaining
  static findings concern Spring/default-method reachability and structural clones
  of JDBC row mappers, constructors, assertions and private nullable-sum idioms over
  different domain types. They are retained without a cross-domain abstraction or
  blanket suppression; the static gates are **not** claimed to pass. Compilation,
  boundary tests and runtime tests provide the separate contract evidence.
- `git diff --check`: passed. Main remained `b2377f97` when refreshed before commit;
  V112 was still available.

Temporary CTB probe files were removed from the backend container after discovery.

No push, PR/CI, merge or deployment is authorized or performed. Production verification
covers the existing ingestion/report projections, not a deployment of the new COGS
code. The expired owner API session was not reused for writes; the user triggered
Run now. The seven held stocktakes require source reconciliation, not an unverified
population workaround. A production browser smoke test with a fresh owner session was not performed.

The first full backend run found one timeout in the newly joined cost query (the
existing JDBC eight-second bound canceled it). EXPLAIN estimated one selected row
for the injected 1,034-row fixture and chose nested loops over both grouped views.
The final query explicitly bounds and materializes sales and cost bases independently,
then joins their results once. The focused real-week and source/sign/scope regressions
pass after that change; no timeout limit was increased.
