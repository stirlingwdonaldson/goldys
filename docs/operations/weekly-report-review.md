# Weekly report: operation and outstanding accuracy reviews

Implemented 3 November 2026. The original 2025/26 KPI workbook is a field reference,
not a source to repair or an authoritative financial ledger. Labour, roster hours,
wages, staff happiness, prime cost and labour-dependent profit are excluded.

## What the feature does

Open **Performance → Weekly report** (`/weekly-report`). Select a Monday and 1–13
weeks. The default is the last four completed Monday–Sunday weeks, evaluated in
Australia/Melbourne. Explicit selection can include the current incomplete week.
Source ingestion continues on its existing schedules; opening or refreshing the
report reads current resolved data. No new connector, scheduler or source write is
introduced.

**Download Excel** produces a real `.xlsx` with a weekly summary, one dated tab per
week and field notes. It exports the exact authorised browser snapshot, including
generation time, units, missing cells and qualifications. Refresh before downloading
to include subsequent ingestion. A downloaded workbook is a snapshot, not a live
Excel connection or a scheduled email delivery. Demo files are explicitly labelled.

The dashboard and workbook carry the same 79 fields. The spreadsheet's summary
fields are covered: food/beverage/total sales and targets, stock-variance categories,
cash variance and thresholds, wastage values/rates/targets, COGS and reviews.
Gross sales, GST, recorded payments and invoice purchases are included with explicit
bases. Repeated data-entry forms, bank account details, free-text management notes,
monthly/social/function-marketing tabs and optional promotion-specific roast/steak
tracking are not copied.

## Verified implementation boundaries

- Sales, GST, payments and purchases reuse the semantic metric catalogue with DAY and WEEK grains. Recorded wastage is summed from `resolved_classified_wastage` (source line quantity × cost), because the legacy inventory-day metric does not project those observations. Food/beverage sales use the resolved source-tax path and the same allocation/coverage calculation as catalogue metrics.
- A report is assembled in one read-only repeatable-read database transaction.
- Every transitive metric permission and `reviews` READ is checked before any
  data read. `products.category` READ also gates reviewed classifications. Denial fails the whole report explicitly; the target grant is ALL × OWNER only.
- Missing values stay null. Known partial sums retain unavailable dates and
  source qualifications. A full calendar grid does not certify source completeness.
- Review ratings reuse the existing weekly actual-rating calculation by review
  date in venue time. Daily rating cells are unavailable; weekly averages are never
  summed or averaged from daily averages.
- Targets use versioned effective-week settings; edits append an actor/timestamp revision. `weekly-report.targets` READ/WRITE is seeded for ALL × OWNER only.
- Wastage reasons, reason coverage, effective targets and dated stock counts appear in both the dashboard and additional Excel detail sheets.
- Reviewed food/beverage sales use observed matching-source tax and retain the 95% daily absolute eligible-value coverage gate. Other, unclassified, known reviewed subtotals, missing-tax counts and headline residuals stay visible. Effective weekly food/beverage target comparisons populate only when actuals exist.
- Recorded food/beverage/other/unclassified wastage uses reviewed exact-line or CTB recipe mappings, with line/value coverage and reason breakdowns. Cost tax basis/completeness remain qualified. Weekly category rates require seven matching sales dates and known included costs; missing observations remain unavailable.

## Reviews needed before populating the unavailable fields

| Area | Evidence / decision needed | Present behaviour |
|---|---|---|
| Current headline sales | Reconcile a completed recent week against independent source net/GST/gross; confirm scope, adjustments and timezone/cutoff | Show resolved amounts with qualifications |
| Food / beverage | Beer and both porterhouse assignments corrected; review source-identity holds, exclusions and classification coverage | Observed ex-GST splits and target differences activate on dates meeting the unchanged 95% gate. Known reviewed subtotals, other/unclassified and reconciliation remain visible below threshold |
| Targets | Workbook baselines may no longer be appropriate; review in the target editor. Daily allocations are not provided | Dated planning-sheet D/F/H sales targets carry forward; percentage limits copied from latest dated tab, COGS limits from the latest populated summary cells. Total sales differences use the effective target; food/beverage comparisons use valid reviewed actual splits |
| Physical cash | Establish physical count/banking input and paid-in/out/refund treatment; verify tender identities and date allocation | Recorded payments separate; counted cash/banking/variance unavailable |
| Wastage | Category mapping, tax basis and source capture completeness remain unverified. Missing reasons cannot be reconstructed | All recorded wastage included, with No reason recorded bucket; coverage by lines and known value; given-reason breakdown and original labels. Category recorded splits use exact line/recipe mappings; rates use matching sales ex GST with explicit cost-basis qualification and availability guards |
| Stock variances | Verify expected stock movements, consumption, categories and valuation; a count alone is not a variance | Stocktake headers and counts now flow on every CTB run, and dated source-value summaries are shown. Variance actuals remain unavailable; targets are editable |
| Theoretical COGS | Verify recipe versions/yields, source costs, tax, classification, sold quantities and intended denominator. Actual COGS additionally needs inventory valuation | Unavailable; purchases remain separately labelled |
| Reviews | Confirm intended platforms, rating scale, review-date versus visit-date and capture completeness | Existing weekly actual-star averages with coverage qualification |

These reviews gate the affected values, not delivery of the dashboard/export.
Targets can be edited in the report without a code change. Other unverified measures still require implementation changes and tests; there is no switch that silently certifies source data.

## Audit context (not a current-data certification)

The read-only comparison on 3 November covered 56 workbook weeks from 30 June 2025
to 26 July 2026. Of 98 overlapping complete-week sales comparisons (parallel
Lightspeed and MarketMan inputs), 92 were within 1%; six substantial comparisons
spanned five weeks. Thresholds were provisional: substantial means over both $100
and 1% of reference; marginal means over $1 otherwise. Agreement does not prove
source truth or within-day completeness.

The platform's historical source basis changes from Lightspeed all-sales in 2025,
to payment-derived totals with estimated GST in January–March 2026, to CTB revenue.
Large daily differences sometimes offset across adjacent dates. Historical source
attribution must not be applied as a universal current-feed assumption.

Product sales started 8 July 2026, detailed source-cost observations 25 July,
wastage 10 September; no stock-count observations existed in the reporting projection at the audit (raw detail did exist). These are
audit observations, not permanent coverage constraints. Six absent headline dates
included Christmas and three June days; closure versus missing ingestion was not
established. No production replay, category mutation or deployment was performed during implementation; the released CTB connector refreshes source stocktakes on its existing schedule.

The old workbook also contains copied summary values, zeros replacing recorded
wastage, COGS percentages based on Sunday alone, averaged daily wastage rates and
mislabelled date ranges. Its calculations are not copied into the new report.


## Imported targets and remaining stock-count exceptions

- Sales targets: 156 dated values from `Weekly Targets & labour budgets!D/F/H` (food/beverage/total), 7 July 2025–29 June 2026. The dedicated planning sheet is used instead of copied, conflicting actual-week summary values. The latest baseline is $45,600 food + $74,400 beverage = $120,000 total. This is explicitly a carried-forward historical target, not a new forecast.
- Variance and wastage percentage limits: seven values from `20 - 26 July!AG3/AH3/AQ3/AR3/AY3/BB3/BE3`, effective 20 July 2026. Food/beverage COGS limits: 25% and 22%, from `Weekly Data 2526!BM54/BV54`, effective 22 June 2026. All source cells are retained with the values; no labour or cash targets are imported.
- Edit targets from a selected Monday. Only changed fields are saved; they carry forward until another dated setting for that field. Historical/future dated settings are preserved. Percent entry uses percent units (0.25 means 0.25%); API/storage use fractions (0.0025). Min/max pairs are validated. Edits append audit rows; no existing revision is overwritten.
- Wastage includes every received line, including missing reasons. Reasons are trimmed/case-normalized, whitespace collapsed and `Knockoff`/`Knock off` merged; original labels remain visible/exported. Other abbreviations such as `KO` remain distinct. Neither missing reasons nor staff-meal membership is inferred. Zero records means unknown, not zero; unknown-valued lines remain counted and are excluded only from known-value denominators.
- Read-only raw-ledger audit on 3 November 2026: 25 header payloads (30 distinct stocktakes), 28 identifiable detail payloads, 10,653 input lines → 10,653 parsed lines (100%) using the actual Java parser. One additional 413-line detail payload has no parent reference and cannot safely be dated; it is not guessed into a stocktake. Representative real-payload fixtures are retained in tests; the all-sample audit ran offline without production writes.
- Existing identifiable detail covered stocktakes through 12 October while headers continued through 26 October. The regular connector now refreshes all advertised stocktakes and pages, including older counts, so historical known parents can be repopulated from CTB without enabling a blanket replay flag. Source authentication and current endpoint availability must still succeed at release time.
- Counts are observations, not continuous balances. Removed source lines are not retired solely by absence. Partial/locked header state stays unknown when CTB does not supply it. Expected consumption/movements and category valuation still gate stock variances and actual COGS. Recipe-cost matching coverage and held food/beverage identities remain outstanding.

## Food/beverage curation and source basis (3 November 2026)

The [dated audit](https://github.com/stirlingwdonaldson/goldys/blob/b2377f979742c74b8504f6ddf57acd3460390025/docs/audits/food-beverage-reporting-2026-11-03/README.md) distinguishes application
curation already applied (18 described categories, two manual porterhouse corrections, 89 reviewed
mapping revisions) from report/editor code not yet deployed. Mappings explicitly restate reporting
from 26 October 2026; source facts, café history and lifecycle are not rewritten. Products → Reporting
mappings provides evidence, effective-date impact preview and explicit approval; no ad hoc SQL is
needed for normal curation. Read the audit before interpreting historical comparisons.

CTB product amounts and Lightspeed line amounts include tax. Ex-GST is observed amount minus observed
tax only when selected source gross, quantity and non-null site evidence agree. Signed observations
are retained; no extra discount/refund/surcharge adjustment or inferred GST. The sampled completed
week remains below 95% sales coverage on every date, so qualified actuals and matching rates remain
unavailable while reviewed subtotals and unclassified values are shown. Wastage includes KO and
missing reasons without inferred staff exclusions; CTB quantity × recorded cost is not a unit conversion.

## Release and validation

The reporting extension additionally requires `V109__reporting_sales_basis.sql` after V108, and a
coordinated backend/frontend release. V109 creates a read projection only; it does not seed mappings,
repair categories, reingest sources or change reconciliation policy. No new production flags.

PR #228 reserves V106 for sale-close timing and must be on main before V107 is deployed. Flyway can apply both migrations in one deployment; do not deploy V107 first and later introduce an older pending migration. This extension requires migration `V107__weekly_report_targets_and_waste_reasons.sql`. Flyway creates effective target settings and appends reason metadata to the resolved inventory observation view. No new production flags are required. Counts begin flowing after the next successful CTB scheduled/manual run; this change does not itself run a production ingestion or replay. Both backend
and frontend must be released together for the new typed GET endpoint
`/api/reports/weekly?from=YYYY-MM-DD&weeks=4`. Existing session authentication and
permission-table grants apply. Pushing, merging and deployment require separate
approval under AGENTS.md.

Run backend `./gradlew test spotlessCheck` and frontend
`bun run typecheck && bun run lint && bun run test && bun run build`.
Tests cover authorisation-before-read, bounded Monday weeks, venue time, missing
data, catalogue weekly totals, withheld fields, demo/live behaviour and literal
spreadsheet text. Also inspect the generated XLSX with an independent reader and
check the page and download in a browser before production use.

Workbook support is intentionally limited to numeric cells and literal inline
text. No formulas, macros or external links are generated; new source text cannot
become an Excel formula. Export is a bounded presentation transformation of an
already authorised report, not another backend query or a new permission path.

Ripwire's quality-delta check reports structural duplication warnings for record
factories, thin controllers, map-building loops, page wrappers and assertions
matching unrelated domains. These matches were reviewed and the local patterns
retained; no cross-domain helper or blanket suppression was introduced. The scan also flags the report constructor dependency count and expanded integration-test/report size; these follow the existing composition pattern and test the required data seams. The static
quality gate is not claimed as passing. Contract inspection found no incompatible
callers; compilation, runtime tests and independent XLSX checks are separate evidence.
