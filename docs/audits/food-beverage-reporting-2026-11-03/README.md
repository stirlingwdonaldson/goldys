# Food/beverage reporting and curation — 3 November 2026

Base `6c01de71` (PR #230), isolated branch `codex/food-beverage-reporting`, worktree
`/tmp/goldys-food-beverage`. The production deployment checkout was not switched or reset.
Production investigation used the existing SELECT-only probe: transaction/session read-only,
8-second statement timeout. Curation used owner-authenticated, CSRF-protected application endpoints;
no direct SQL writes, source-system writes, replay flags or deployment operations.

## Changes actually applied to the application

- Beer regrouped from Food to Beverage. This changes browsing only.
- Descriptions with inclusions, exclusions and counterexamples added to all 14 existing categories.
  Added Mains (Food), Ingredients (Food), Modifiers (Food), and Other charges (flat). Mixed functions
  explicitly do not supply a financial allocation. Spreadsheet storage/format buckets are not the tree.
- Independently reviewed `porterhouse` and `porterhouse 250g` moved manually to Mains. Recipe 461
  and FOOD MENU evidence support these corrections; no evidence establishes the latter as a weekly
  special. No café/lifecycle assertion was made. Their ambiguous financial aliases remain unmapped.
- Ran the context-aware **review-only** ASSIGN preview
  `9d9d44cf-a7f7-42ac-8b26-632367b65995`: 2,659 candidates, 1,769 NEEDS_REVIEW,
  669 UNCLASSIFIED, 219 identity/recipe holds, **two MANUAL_PRESERVED** porterhouse corrections.
  No model output was bulk applied. Independently reviewed source evidence is retained in
  `evidence/reviewed-context.json`; the complete snapshot remains available through its authenticated
  preview endpoint. Downloaded full-snapshot SHA-256:
  `fae3e98997dc1a9351f6bdade50ec8d267501532073554087913a04bb90a0a65`.
- Applied **89 reviewed reporting revisions**: 38 unambiguous PRODUCT_NAME aliases (13 food,
  24 beverage, one raffle OTHER), and 51 exact CTB recipe identities (one food, 50 beverage).
  Every revision explicitly takes effect **26 October 2026**, restating reporting from that date.
  Actor and reason are stored by the application. `mapping-preview.json` and `mappings-applied.json`
  retain the decisions. Unknown stock bucket, role, business area and lifecycle stay UNKNOWN.
  Neither model agreement nor existing browsing assignments were approval criteria.

The initial audit reconfirmed 580 current wastage lines, 484 recipe IDs, 266 missing reasons,
63 direct name-assignment matches, 14 blank category descriptions and both porterhouse problems.
Those are a dated measurement, not ingestion-completeness guarantees. Final category/mapping checks
are in `post-curation.csv`. Historical café products were retained; neither coffee names nor lack of
recent sales was used to declare café membership or retirement.

## Implemented reporting contract

`resolved_reporting_sales` combines a selected resolved product/day with that **same source's**
observed gross, quantity and tax. The source observations must match the selected gross and quantity
exactly, have all monetary observations and a single non-null observed site namespace. CTB amount and
Lightspeed line Total Inc Tax are inclusive of tax. Net is their observed amount minus observed tax;
no division by 1.1 or additional refund/discount/surcharge adjustment. Agreed-source rows, legacy
unqualified tax, absent tax and mismatched scope/amount/quantity stay unavailable. The application
reads this through `ReportingSalesQuery`; catalogue food/beverage metrics and weekly reporting share
`ClassifiedSalesTotals`, including the same coverage policy.

Financial nature comes only from the latest reviewed PRODUCT_NAME revision effective on the sale
date. Browsing categories do not allocate money. Paid modifiers need their own reviewed nature;
uncoded free CTB modifiers already remain raw-only, with explicitly zero monetary measures. Mixed
packages, ambiguous identities and unreviewed merchandise/donations/surcharges stay unknown. Reviewed
OTHER is outside the eligible food/beverage denominator. Refund signs are retained; discount fields
are observations, not a second subtraction. Sample agreement does not establish every adjustment type. The retained week has 117 zero-amount rows and no negative gross rows; real refund/surcharge/donation treatment outside that sample remains unproven. Refund-sign boundaries are tested synthetically.

The **95% daily absolute eligible ex-GST value** coverage threshold is retained. Missing tax or
unresolved revenue withholds that date; a week can contain a qualified subtotal of usable dates,
with withheld dates listed. Separate reviewed known food/beverage subtotals, other, unclassified,
known product net/gross, missing-tax/unresolved counts, coverage and headline-minus-product residual
remain visible even below threshold. The residual is never allocated. Sales versus target and
percent comparisons use the existing effective weekly targets; no daily target allocation was added.

Wastage aggregates the non-duplicating `resolved_classified_wastage` view. Exact waste-line decisions
(including UNKNOWN withdrawals) override recipe decisions. Sale-product name matches never identify
inventory. Every recorded reason, including `KO` and absent reasons, remains included. Quantity ×
recorded cost is retained without ml/each/recipe conversions or substitution of today's recipe cost.
The recipe UOM label is not evidence that quantity is measured in ml: a Carlton pint observation is
3 × $4.13 = $12.39 even though its recipe UOM says ml. Food, beverage, other and unknown known values
reconcile; coverage by recorded lines/value is distinct from reason coverage. Missing observations
stay null. Food/beverage rates use matching category sales, with weekly rates requiring all seven
sales dates and known included costs. They remain explicitly source-cost/ex-GST comparisons with
unverified cost tax basis and completeness, not certified loss rates or inferred reason exclusions.

All new rows pass through the same typed weekly API snapshot, browser table and generic Excel
export. Demo includes partial dates, reviewed subtotals, other/unknown values and withheld rates.
The report has 79 rows. No labour, bookkeeping, stock variance or daily-target work was added.

## Ongoing curation

Products → **Reporting mappings** provides a bounded item review workflow: select exact identity,
independent dimensions, effective date, reason and an impact interval of at most one year; preview
source evidence/holds, affected recorded observations, known source amount and date implications;
then explicitly approve the revision. Later revisions remain in history. Exact-line overrides and
later-effective decisions still win. Stock mappings have no current consumer. Loading wastage
shows 50 lines per page, including missing reasons/costs and recipe IDs. Revision history shows up to
50 matching decisions. Category drill-down edits the persisted criteria. API errors and denials are
visible; server permission checks precede evidence/impact reads and all writes reauthorize.

The preview is a current-observation impact estimate, not a future completeness forecast or a
production-wide accuracy claim. A held alias requires source equivalence/identity evidence or
remains unknown. The current mechanism deliberately does not guess that legacy site -1 and site 0
are the same identity. The owner still needs to establish those namespace transitions, reliable café
identifiers/operating dates, lifecycle decisions, ambiguous packages and approved reason exclusions.

## Real completed-week reconciliation: 26 October–1 November

| Observation | Known value |
|---|---:|
| CTB resolved product gross (1,034 product/day rows) | $155,630.2663 |
| Matched source-tax product net (all 1,034 rows) | $141,527.4119 |
| Reviewed food net subtotal | $18,711.5742 |
| Reviewed beverage net subtotal | $17,269.3986 |
| Reviewed raffle OTHER net | $340.0000 |
| Unclassified net | $105,206.4391 |
| Currently resolved headline net (mixed CTB/Lightspeed selection) | $142,132.1971 |
| Headline minus product net, not allocated | $604.7852 |
| Independent retained Lightspeed report net | $142,516.6300 |
| Independent Lightspeed minus CTB product net | $989.2181 |
| Recorded waste (107 lines, all valued) | $560.7053 |
| Beverage waste (96 lines) | $452.9860 |
| Food waste (one line) | $5.0196 |
| Unknown waste (10 lines) | $102.6997 |

Food/beverage sales coverage remains below 95% on **every sampled date** because substantial sales
aliases remain held. Qualified actuals, target differences and matching wastage rates are therefore
unavailable for this sample. The separately labelled reviewed subtotals are available. This is an
intentional accuracy gate, not an unimplemented switch. Lowering the threshold or approving held
aliases to fill those cells would misrepresent the evidence.

The independent Lightspeed export is the previously retained original seven-row source report
`fixtures/lightspeed/daily-sales-closed-date.csv`, hash
`5231266f5aefadcdd95e8e3aafea191c270ae3d1e9eaa2f3708caa094ef7d1bf`.
Its net/GST/gross were independently verified against merchandise and Sales Feed exports in the
headline-sales audit. This session compared the retained report with current resolved observations;
it did not obtain a fresh live source report or resolve the discrepancy. Product source priority
and headline source priority remain independent. No differences were forced into food/beverage.

CTB header checks cover all 11 completed-week wastage records: 10 agree exactly with retained
quantity × cost; record **161** differs by **-$0.0019666667** (canonical known value 12.1343 versus source
header 12.1362666667). This is consistent with four-decimal retained observations but precision is
not independently certified. Record 149's older each/kg sample also agrees exactly at 154.325.
The each/ml/stock-unit examples and current recipe costs show why recipe cost must not replace the
recorded observation. No cost tax basis, missing/retracted source lines or source completeness is
certified by header agreement. No reason abbreviation was interpreted as a staff meal/knock-off.

## Validation and release

See `validation.md` for actual commands/results. Non-personal real CTB week, wastage and reviewed
mapping fixtures are retained under `backend/src/test/resources/fixtures/ctb/reporting-*2026-10-26*`.
The PostgreSQL regression checks the complete real-week accounting and safety holds, alongside
synthetic boundary cases (tax mismatch, effective dates, exact-line withdrawals, missing amounts,
refund signs, merchandise exclusion, zero denominator and permission-before-read).

Production taxonomy/mapping curation above **has happened**. Report/editor code and additive V109
migration **have not been deployed**. No source reports, schedules, production configuration,
reconciliation rules or canonical/raw facts were changed by this work. Push, PR creation, merge
and deployment retain the repository's explicit approval boundary.
