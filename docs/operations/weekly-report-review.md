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

The dashboard and workbook carry the same 59 fields. The spreadsheet's summary
fields are covered: food/beverage/total sales and targets, stock-variance categories,
cash variance and thresholds, wastage values/rates/targets, COGS and reviews.
Gross sales, GST, recorded payments and invoice purchases are included with explicit
bases. Repeated data-entry forms, bank account details, free-text management notes,
monthly/social/function-marketing tabs and optional promotion-specific roast/steak
tracking are not copied.

## Verified implementation boundaries

- Sales, GST, payments, purchases and total recorded wastage reuse the semantic
  metric catalogue with DAY and WEEK grains. No second financial formula path.
- A report is assembled in one read-only repeatable-read database transaction.
- Every transitive metric permission and `reviews` READ is checked before any
  data read. Denial fails the whole report explicitly; no new role grants.
- Missing values stay null. Known partial sums retain unavailable dates and
  source qualifications. A full calendar grid does not certify source completeness.
- Review ratings reuse the existing weekly actual-rating calculation by review
  date in venue time. Daily rating cells are unavailable; weekly averages are never
  summed or averaged from daily averages.
- Classification-dependent splits and other unverified measures are deliberately
  unavailable in both outputs until their evidence is reviewed. They are retained
  as fields, not replaced with fabricated zeros or historical targets.

## Reviews needed before populating the unavailable fields

| Area | Evidence / decision needed | Present behaviour |
|---|---|---|
| Current headline sales | Reconcile a completed recent week against independent source net/GST/gross; confirm scope, adjustments and timezone/cutoff | Show resolved amounts with qualifications |
| Food / beverage | Correct Beer → Food hierarchy and item assignments (including porterhouse → Beer); verify ex-GST allocation, merchandise/donation exclusions and coverage | Withhold both splits |
| Targets | Choose approved target versions and maintainable source. Planning, summary and dated tabs disagree; do not copy the old figures forward | Targets and target differences unavailable |
| Physical cash | Establish physical count/banking input and paid-in/out/refund treatment; verify tender identities and date allocation | Recorded payments separate; counted cash/banking/variance unavailable |
| Wastage | Verify current cost observations, category mapping, reason exclusions (staff meals, personal use, knock-offs), coverage and tax basis | Show only existing resolved total where available; splits/rates/targets unavailable |
| Stock variances | Verify opening/closing stocktakes, movements, recipe consumption, units and valuation; establish category/threshold definitions | All variance categories and rates unavailable |
| Theoretical COGS | Verify recipe versions/yields, source costs, tax, classification, sold quantities and intended denominator. Actual COGS additionally needs inventory valuation | Unavailable; purchases remain separately labelled |
| Reviews | Confirm intended platforms, rating scale, review-date versus visit-date and capture completeness | Existing weekly actual-star averages with coverage qualification |

These reviews gate the affected values, not delivery of the dashboard/export.
Activating reviewed fields requires an implementation change and tests; there is
no UI switch that silently treats these measures as verified.

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
wastage 10 September; no stock-count observations existed at the audit. These are
audit observations, not permanent coverage constraints. Six absent headline dates
included Christmas and three June days; closure versus missing ingestion was not
established. No historical repair, replay, category mutation, production write or
new source access is authorised by this feature.

The old workbook also contains copied summary values, zeros replacing recorded
wastage, COGS percentages based on Sunday alone, averaged daily wastage rates and
mislabelled date ranges. Its calculations are not copied into the new report.

## Release and validation

No schema migration or production configuration change is required. Both backend
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
retained; no cross-domain helper or blanket suppression was introduced. The static
quality gate is not claimed as passing. Contract inspection found no incompatible
callers; compilation, runtime tests and independent XLSX checks are separate evidence.
