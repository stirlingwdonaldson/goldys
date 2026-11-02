# Lightspeed headline sales: sale-closed-date contract

This is an application change only. It does not deploy, change Looks/schedules, replay raw
history, import the seven-day candidate, or change reconciliation rules/overrides.

## Problem and correction

The old `integration-platform-daily-sales` (Look 9365) delivery filtered `revenue.sale_date`
but grouped by reconciliation date. On the retained 31 October report, 41,117.54 was assigned
to reconciliation date 1 November and 755.98 to 31 October. Grouping those rows by reconciliation
date cannot produce sale-closed-date daily sales. The old importer also added signed adjustments
again: 28 October source gross 20,086.04 plus -61.46 became 20,024.58.

`POST /api/ingest/lightspeed` keeps its existing token authentication and envelope contract
(`attachment.data` contains CSV). It now accepts only the verified sale-closed-date report.
Legacy reconciliation-date reports are retained raw and rejected with
`UNSAFE_DAILY_SALES_BASIS` before canonical writes. Do not deploy without arranging the report
cutover: the existing old report will deliberately fail until replaced.

Exact CSV headers, order independent:

- Sales Data Sale Closed Date
- Site Numeric ID
- Sales Data Total Ex Tax
- Sales Data Total Inc Tax
- Sales Data Total Tax
- Sales Data Total Adjustment Inc Tax

One row per sale-closed date, site **96181** only. The existing canonical daily identity is
source plus date, without a site key; accepting another site would overwrite this venue's facts.
Supporting another venue requires a reviewed identity/schema change. No site is inferred from names.
Extra dimensions, row-number columns, totals rows, duplicate dates/headers, invalid UTF-8,
malformed CSV, missing money, amounts outside NUMERIC(14,4), and net+tax != gross are rejected.
Negative/zero totals and both formatted/unformatted numeric values are supported.

Reported net, tax and gross are canonical facts without another adjustment calculation.
The adjustment remains in the original raw CSV and parsed row; it is not added to revenue.
Dates remain source sale-closed calendar dates, without a UTC or cashup-date reinterpretation.
The selected source timezone is Australia/Melbourne; CSV alone cannot certify that selection.

## Publication and downstream behavior

The full envelope is retained before parsing. All rows are validated before canonical publication.
PARSED and CANONICALIZED outcomes are recorded, including failures. An empty report records EMPTY
stages plus DAILY_SALES_EMPTY_UNVERIFIED; it neither supplies dated zeros nor deletes prior dates.
Unexpected canonical failures may leave earlier dates committed; the failed stage records that
incomplete batch. Inspect retained evidence before retrying.

The existing canonical ingest facade supersedes changed source/date facts and reuses unchanged
facts. Normal DailySalesRecorded events refresh resolved_daily_sales. SalesMetricsQuery and its
sales metric catalogue/API/assistant consumers continue reading the resolved projection.
Rules and manual overrides still choose between Lightspeed and CTB. Correct Lightspeed input
therefore does not guarantee every dashboard selects Lightspeed. Product/category/item totals and
receipt identity use their separate pipelines; no new line-to-SaleID bridge is claimed.

## Real evidence and tests

`backend/src/test/resources/fixtures/lightspeed/daily-sales-closed-date.csv` is the unchanged,
non-personal seven-row export from query FArbOF0lmNGHJZut79RZqN, 26 October–1 November 2026.
SHA-256: `5231266f5aefadcdd95e8e3aafea191c270ae3d1e9eaa2f3708caa094ef7d1bf`.
All seven rows parse; independent merchandise and Sales Feed exports agree on each day's net,
tax and gross. Seven-day totals: net 142,516.63, tax 14,178.94, gross 156,695.57.
The recovery ZIP was `ef149a4030b9a7f9c593f07034e326dd8125e5a5c67d0e958535f2e5b8f5ccaf`.
No customer/staff recovery files are added to this feature.

Parser tests cover the real export, legacy rejection and malformed/ambiguous inputs. PostgreSQL
integration tests cover byte-faithful retention, canonical supersession/idempotence, resolved
headline metrics, whole-report validation, empty observations and failed-stage evidence.
The source test covers seven days, not every adjustment type or complete historical coverage.

## Cutover and historical limits

Before activation, save/review an unscheduled daily candidate with exactly the six exported headers,
site 96181, Australia/Melbourne, CSV, All Results and visualization options off. Formatted values on
matches the tested source. Confirm actual field identifiers and complete query definition rather
than assuming UI labels identify join grain. A raw one-off envelope after deployment must prove
the report settings and compare a completed day's net/tax/gross against independent evidence.

Review the actual old daily-sales schedule (separate from sale-item Looks 9519/9520), its override,
frequency and destination. Obtain approval to stop that schedule, deploy the application and
activate the reviewed replacement. Do not point the new report at the old deployed parser before
deployment. Do not change any source/report automatically from this code.

Keep APP_LIGHTSPEED_BACKFILL and other daily-sales replay/derivation switches off. Existing raw
reconciliation reports cannot be replayed into the corrected contract. Historical daily-sales
repair needs an approved date scope, current-source preview and bitemporal corrections. Omitting
a wrongly assigned date does not remove its old fact. This change has no automatic historical
cleanup or date reassignment.

The separate all-sales/payment-derived daily paths remain outside this contract; inspect their
configuration before cutover so an unreviewed writer cannot replace the same source/date fact.
Existing CTB discrepancies and source-priority decisions require their own review.
