# Next data-correctness increments

Date: 16 October 2026. Status: implementation authorised; invoice increments implemented on `codex/invoice-read-correctness`, verification recorded in the [implementation report](../../audits/data-foundation/2026-10-16-invoice-read-implementation.md).
Baseline: `codex/data-foundation-repairs` at `0f88ebd`.
Design: [invoice read correctness](../specs/2026-10-16-invoice-read-correctness-design.md).
Previous review: [foundation repairs](../../audits/data-foundation/2026-10-16-repairs.md).

## Priority and readiness

| Order | Work | Why now | Gate |
|---|---|---|---|
| 1A | UUID-qualified invoice detail, list selection and supplier/line joins | Confirmed identity loss in current views; one collision can block unrelated results. | Ready for scoped implementation; no new source policy needed. |
| 1B | Consistent supplier purchase basis and honest missing WET/line coverage | Confirmed gross/header fallback and null-to-zero behaviour in existing results. | Ready; retain known line subtotals and qualify omissions. |
| 1C | Qualified PDF relationships and raw-access enforcement | Existing source evidence must belong to the selected invoice and remain restricted. | Permission check is ready; schema/link work needs additive migration review and attributable link evidence. No automatic historical assignment. |
| 2 | More specific coverage explanations in existing summaries | Current repairs propagate notices, but successful imports do not prove complete periods. | Plan evidence mapping first; do not invent a trading calendar or missing-amount bounds. |
| 3 | Stable product identity in daily reporting | Source hub tables exist; existing daily facts/projections remain name-keyed. | Discovery/design only until grain, source-code coverage and mapping semantics are verified. |

Orders 2 and 3 are a bounded backlog, not permission to rewrite those subsystems or adopt all proposed
SRS capabilities. Complete order 1 before starting another implementation track.

## 1. Invoice read correctness: ordered implementation

### 1A. UUID read path and compatibility

1. Add characterization cases before changing contracts: suppliers A/B share `INV-42`, C has a unique
   invoice, and an ambiguous linked document lies outside the selected date range. Verify the whole
   list still works, rows remain separate and lines cannot cross parents.
2. Extend `canonical/InvoiceMetadataView`, `EnrichedInvoiceLine`, their query mappers and qualified
   query methods to carry existing invoice and supplier logical IDs. Query lines by parent ID. Legacy null-parent lines
   remain unresolved; never attach them by display name/number in the read layer.
3. Update `application/InvoiceReportingService`, `reconciliation/InvoiceGraphServiceImpl` and
   `InvoiceLineMetricsServiceImpl` to join on UUID. Remove global number-map dependencies from
   these paths. Group supplier nodes by the stored supplier ID rather than label; adapt existing
   name routes only when unambiguous. Preserve number-only facades as guarded compatibility adapters.
4. Extend `semantic/InvoiceGraphQuery`, `InvoiceGraphNode`, the existing application facade,
   `api/InvoiceController` and inventory graph controller with explicit UUID reads. Require existing
   permissions before resolving identity. Keep existing response/error envelopes.
5. Update `frontend/lib/api/{types,live,demo}.ts`, `invoice-graph.ts`, invoice list/detail/sheet and graph
   builders/views. Use `invoiceId` for selection, row order, node IDs, requests and shared URLs.
   Preserve old number links with an explicit ambiguity/error state. Include duplicate-number demos.
6. Keep PDF ambiguity local to the relevant invoice; list/detail display a document qualification
   while the original guarded retrieval remains until 1C. Never mark unresolved evidence as absent. Show ambiguous number-only ingestion flags as unscoped
   warnings, not evidence attributed to each same-number invoice.

Migration: none expected for identity propagation; required IDs already exist in storage. Size:
moderate-to-wide API contract change across backend/frontend; keep this commit independently reviewable.

Checks: `SupplierInvoiceIdentityIntegrationTest`, `InvoiceReportingServiceTest`, `InvoiceGraphServiceImplTest`,
`InvoiceLineMetricsServiceImplTest`, application/controller authorization tests, invoice page/sheet and
invoice graph tests. Cover legacy unique links, malformed UUIDs, unknown IDs, denied roles, sorting,
Back/forward, copying links and identical invoice numbers across suppliers. Add an integration seam
case exercising stored parent IDs through application response, rather than mocks alone.

### 1B. Purchase basis and partial evidence

1. Characterize mixed line/header availability and tax: A has $100 ex-tax lines/$110 gross header;
   B has $220 gross header and no lines. Add all-null, mixed-null and explicit-zero WET groups.
2. Remove header fallback from graph purchase subtotals. Share an existing invoice-line aggregation
   helper between graph and breakdown only where their contracts match; preserve per-invoice detail.
   Expose separate header ex-tax evidence/difference and counts of known missing-line/unresolved
   invoices. Mark coverage unverified even when known rows reconcile; matching sums do not prove
   a complete export. Check whether existing PDF-recovery flags already identify the gap.
3. Propagate qualifications through existing application DTOs, frontend API/demo and graph/breakdown
   rendering. Check all visible COGS labels describe invoice purchases. Do not redesign unrelated UIs.
4. Compare supplier subtotals plus unresolved bucket with canonical line purchases for identical
   date/source scope. Compare resolved inventory only where no explicit override applies; display
   override differences as such. Never force equality by changing canonical facts.

Migration: none expected. No ingestion, tax guessing, return reversal or historical projection repair.
Checks: mixed GST bases, partial exports, header-only/line-only invoices, duplicate issuer numbers,
unknown supplier, signed adjustments, WET null/zero, and parity of known subtotals across graph,
breakdown and unoverridden inventory metric. Existing tolerances are not new acceptance policy.

### 1C. Original documents and permissions

1. Add custom-role tests: cost-only role may read qualified invoice detail but may not download raw
   PDF; role with both invoice and raw grants may download; role with neither is denied before raw
   lookup. Reuse `connectors` raw permission; do not add department grants or owner bypasses.
2. Add a nullable logical invoice ID and index to `invoice_pdf_link` in the next unused migration
   (V95 at this baseline). Preserve existing links and payloads. Write no number-only bulk backfill.
3. Extend `InvoicePdfLink`, repository/service and `InvoiceDocumentService` with scoped links and
   document retrieval. Extend the API/demo for availability, qualifications and document selection.
   Multiple documents require explicit selection; do not infer revision precedence from receipt time.
4. Keep ingestion/enrichment ambiguity guards. Only qualify a new link when its existing evidence
   establishes the parent. Before changing any source matcher, inspect the already-approved sample
   evidence/exact schema; stop that dependent change if attribution cannot be proved.
5. Test empty-database migration and upgrade from the repaired V94 baseline in disposable PostgreSQL.
   Test two suppliers/same number, unqualified old links, duplicate raw delivery, multiple documents,
   revoked permissions, correct byte/filename pairing, and denied cross-invoice document IDs.

Deploying a Flyway-enabled build would apply migrations; deployment and production migration remain
separate explicit approvals. Historical link qualification is a separately reviewed repair action.

## 2. Coverage explanations: next planning increment

Trace existing `PeriodMetricReader`/`MetricProvenance`, ingestion stage outcomes, resolved projections
and displayed `DataQualifications`. Determine which business date/scope is actually evidenced by each
receipt. First target current sales and invoice-purchase summaries; reuse existing notices.

Propose structured requested/observed/excluded scope and reason codes only where facts support them.
Do not count every absent calendar date as missing trading, turn failed-fetch counts into missing
revenue, or use receipt time as covered business time. Surface known incomplete subtotals and make
ratios unavailable where bounds are unknown. Historical age alone must not expire evidence.

Before implementation, produce an evidence-to-qualification matrix and one end-to-end test plan for
API, saved widget and existing Ask Goldy's output. Separate failed processing from unknown source
coverage. A complete trading calendar/expected population and validated uncertainty model remain
business/evidence dependencies; the agreed five-percentage-point limit does not choose that model.
No schema expansion is committed by this backlog item.

## 3. Product identity: bounded discovery

Inspect `CanonicalProductSalesService`, CTB per-day aggregation, `ProductMappingIngest`,
`SourceProductSaleFact`, `ProductSalesProjector` and category queries together. Trace two source codes
sharing a name, a renamed code, a manual hub correction and an unmapped source item. Determine
whether aggregation has already collapsed distinct products before the mapping layer runs.

Produce a grain/identity proposal and upgrade plan before changing keys. Use existing source contracts
and retained authorised evidence; new production sample access must be separately scoped. Do not
reuse invoice CSV samples as proof of the product-sales contract. Keep financial measures and unit
conversions source-qualified. No automatic fuzzy cross-source matches, retroactive aliases or
selected-product forecasts are included. Historical merges may be irreversible without raw replay.

## Deferred and decision-dependent

- CTB complete/partial/unexported behaviour and editable export revision ordering: source confirmation.
- Return/credit signs, physical units and packs: representative source meaning before recognition rules.
- Historical re-key/replay and old V74–V80 environments: separately approved migration/repair plan.
- Statistical missing-input bounds: method/confidence/calibration decision before displaying a bound.
- Prospective booking freshness/grace period: further clarification; forecasts remain available qualified.
- Loaded labour, stock valuation, new reports/exports, selected-product forecasts and unrelated UX:
  separate capability selection and evidence; outside these corrections.

## Validation and completion boundary

Run focused backend PostgreSQL tests and frontend Vitest cases during each slice. Before implementation
completion run backend `./gradlew test spotlessCheck` and sequential frontend typecheck, lint, tests and
build; retain architecture boundary tests. Run Ripwire edit/quality checks and explain remaining findings.
Do not report the name-based graph's missing edges as absence of runtime callers. Its `linesForInvoice`
impact query found four definitions but could bind no callers; manually inspected interfaces/controllers
and executable seam tests are the evidence for this API change.

The subsequent implementation includes 1A/1B/1C. V95 is prepared and tested only in disposable
PostgreSQL. Items 2/3 have the evidence matrix and bounded discovery in
[follow-up evidence](../../audits/data-foundation/2026-10-16-follow-up-evidence.md); they are not claimed
as completed product-identity or complete-business-coverage implementations. No production payloads
were accessed, dependencies installed, production migrations run, or connected systems changed.
