# Sales and POS payment comparison

Implementation follow-up to the [12 November investigation](../audits/sales-payment-2026-11-12/README.md). This does not deploy, change Lightspeed reports/schedules, or repair historical financial facts.

## Application behavior

Sales detail compares resolved merchandise totals by **sale-closed date** with resolved POS tender amounts by **payment-created date**. It does not establish processor or bank settlement, an unpaid customer balance, or a gift-card liability balance. The graph and section are labelled accordingly. Readable totals above the zoomable graph keep the cent-level comparison visible at desktop and mobile widths.

Aggregation preserves the source's four decimal places using safe integer units. Displayed period totals and their comparison use AUD cents, rounding half cents away from zero. Rounding happens after aggregation; individual sub-cent observations are not rounded to cents first. This is presentation/comparison precision, not a rewrite of source observations or a new tax calculation. Values beyond safe integer aggregation are unavailable rather than approximate.

Missing or invalid amounts propagate as missing. An empty source does not imply a zero total. Actual zero observations remain zero. The graph displays cents and preserves genuine one-cent differences. Source conflicts and different observed date populations remain visible even when aggregate amounts agree; equal populations alone do not certify completeness. Later payments and refunds can legitimately cross the selected period boundary.

Gift-card and manual tenders, prepaid functions and signed refunds remain included once in payment amount. Merchandise totals are already adjusted; discounts are not applied again. Deleted orders remain separate observations and are never deducted automatically. Payment tips, tendered and surcharge fields are not added to payment amount without an independently verified measure contract.

## Existing merchandise endpoint

There is no new merchandise webhook. Continue using `POST /api/ingest/lightspeed-sale-items` with the verified financial report. Its financial CSV must exclude payment joins, including `Saleline Payments Sale Number`, every `Payment Data` field and `Product Salelines Product ID`. Those headers can multiply or drop merchandise measures even when line IDs are unique; they remain rejected with `UNSAFE_REPORT_GRAIN` before canonical publication.

Receipt-number mapping is independent and uses the existing `POST /api/ingest/lightspeed-receipt-mapping` endpoint. See [separate financial and receipt evidence](lightspeed-separated-feeds.md) for headers, site scope, identity and qualifications. Do not add a payment field to the financial report to recover receipt numbers.

## Payment report validation

The payment webhook retains its original envelope before validation. Duplicate CSV headers are rejected rather than silently choosing one column. Before writing any payment, the complete parsed report is checked for missing required amounts and repeated canonical keys:

`(sale number, payment type code, register code, payment-created date)`

Null type/register codes normalize to empty strings, exactly as in canonical identity. Two rows with that key are ambiguous even if other fields match. The entire delivery fails with `UNSAFE_PAYMENT_GRAIN`; original bytes and a failed PARSED stage remain available in data health. This also prevents unsafe retained reports from being replayed through the payment backfill path.

One source row can legitimately contain several payments: its amount is already the grouped total and payment_count is metadata. Different tender types, registers and dates remain independent. Valid repeated deliveries are still idempotent. The new guard does not sum ambiguous rows, change existing keys, overwrite previous facts to force agreement, or correct old payment history. Unexpected database/publication failures retain the existing failed-stage behavior; this is not a new whole-import transaction guarantee.

A read-only preflight checked 32 retained payment CSVs below 5 MB, containing 49,910 rows: no repeated canonical keys were observed. This covers recent deliveries, not every historical export. The existing sanitized payment fixture and PostgreSQL integration tests verify grouped split tenders, tips, resolved totals, idempotence and retained failure evidence.

## Local verification

The full backend suite completed with 1,900 tests, zero failures/errors and five skipped tests, including the three new PostgreSQL payment-validation cases. After test-only cleanup, the focused payment tests and Spotless passed again. Frontend typecheck, lint, all 771 tests and production build passed.

The original in-memory probe was repeated against all 311 observed 2026 days: all 243 exact-decimal balanced days now have zero false warnings (previously 146). The screenshot's genuine $378 discrepancy remains visible. The minimized retained-fixture reproduction passes with the corrected graph. All three isolated browser checks passed, covering desktop/mobile balanced totals and a real one-cent difference.

## Release and remaining work

Before release, complete frontend typecheck, lint, unit/component tests and production build, and backend tests/Spotless including PostgreSQL 16 integration tests. Browser checks use only intercepted fixtures, covering a real balanced-day aggregate at desktop/mobile widths and a genuine one-cent discrepancy. No test needs production writes or source credentials.

Deployment requires explicit approval. After deployment, check the actual active Lightspeed definitions and retained deliveries against these contracts. Recent retained safe deliveries do not prove an unchanged future schedule. No new webhook configuration is necessary if the current financial and mapping deliveries already match the contracts.

The four inflated screenshot receipt groups and broader affected history still need clean no-payment financial exports, stable line IDs, independent control totals and reviewed bitemporal corrections. The existing five-day historical repair tool does **not** support arbitrary monetary corrections; do not apply it as a general financial repair. Future repair implementation waits for real candidate evidence and an explicit correction scope. Do not divide amounts by payment_count as a repair rule.

Not established by this change: complete historical/source coverage, the remaining mixed-tender residual correction formula, processor/bank settlement, gift-card funding/liability reconciliation, a full historical payment-key collision audit, or unattended daily reconciliation alerts. Existing period comparisons and explicit failure states remain the operational checks; no background monitor or new financial metric is claimed.
