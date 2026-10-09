# 04 — Ingestion reliability, accounting and recovery

## What is reliable today

**Production:** C06 finds zero run `persisted_count` versus linked raw-ledger count discrepancies.
No dangling RUNNING runs were observed. Raw/run/canonical foreign keys have no observed orphans.
**Code:** `RawPayloadService.java:31–64` clones and hashes stored bytes and commits raw persistence with
the run's persisted counter. `ConnectorRunner.java:71–103` catches classified/unexpected exceptions;
already stored pages survive and `IngestionRunService.java:59–76` derives PARTIAL/FAILED statuses.
Startup recovery marks interrupted pulls (`IngestionRunService.java:85–98`); it does not replay transformations.

This is meaningful delivery reliability. It is **not end-to-end transformation accounting**.
The audit independently checked SHA-256 and byte length for **145 selected non-PDF payloads**:
zero mismatches (`payload-analysis.json:sampled_raw_integrity`). This is a bounded integrity sample,
not an all-PDF hash verification.

## Confirmed boundaries and failure modes

### F02 — CTB expanded pull fails before recipes can be persisted

I01: all five `CONNECTOR_SCHEMA_MISMATCH` failures have stack frame `CtbClient.getAllRecipes`
and detail `Non-JSON CTB response`. Latest successful earlier phases retain daily/product, six invoice pages,
four link pages; no recipe or downstream dataset bytes exist.

`CtbClient.java:227–239,300–305` parses/validates response before handing a page to the connector.
`CtbConnector.java:75–88` invokes datasets sequentially; any exception aborts remaining datasets.
**Known cause at platform boundary:** non-JSON response from recipe endpoint + fail-fast shared connector sequence.
**Unknown external cause:** route mismatch, session/auth redirect, source-side error or changed endpoint shape.
The database stack establishes the boundary, not the remote cause. No source request was attempted.

**Recommendation:** separately account and isolate dataset failures, preserve sanitised response status/content-type
and request action/window, then have an authorised operator verify the recipe contract. Raw capture must not store
credentials or login HTML indiscriminately. Existing revenue success should not conceal missing inventory phases.

### F06 — Push runs are closed before parsing/projecting

`IngestionService.java:111–124` ends the raw-write run before the caller parses. Follow-up work:

| Caller | Post-completion work | Consequence |
|---|---|---|
| LightspeedIngestService:45–55,65–86 | parse CSV, daily canonical writes | Parser/projector exception cannot alter already SUCCESS run |
| LightspeedProductIngestService:41–75 | parse/group/canonicalise products | Same gap; partial canonical writes possible |
| CtInvoiceCsvIngestService:41–93 | parse all CSV, then headers and per-line writes | Failed later invoice/line does not mark whole run partial |
| OpenTableCsvIngestService:29–51 | parse CSV + per-reservation writes | Same gap |
| CtbSftpPull:66–78 | PDF parse/enrich, then mark file processed | Raw SUCCESS persists even if extraction/enrichment fails |

Lightspeed historical silent-parser-success mode therefore **remains possible**, despite current pull exception handling.
Current evidence: 24 raw SUCCESS reports, 8 header-only reports, no parse/canonical counters. Empty report logs
WARN (`LightspeedIngestService:69–74`) but no terminal `NO_NEW_DATA` or report-stage outcome.
Six recent one-line CTB CSVs have no canonical FK reference; they are parseable at the audited basic
date/money/quantity grain. **Unknown** whether their no-reference outcome is idempotence, later rejection or other
post-storage failure; the ledger cannot resolve it. Never classify these six as proven data loss from counts alone.

### F09 — Dataset freshness is masked by another CTB path

`ConnectorHealthService.java:19–22` takes latest run **per source**, not per connector/dataset.
Latest CTB CSV SUCCESS (Oct 9 00:24) masks latest web PARTIAL (Oct 8 17:00).
`TrustService.java:242–271` uses max last-run time and treats only FAILED as SOURCE_FAILURE, not PARTIAL.
This means receipt of a small CSV can refresh sales/product freshness even though those datasets did not arrive.
Documented “last successful domain ingestion” intention in `application.yml:100–125` differs from implementation.

### F10 — Reported completeness is run cleanliness

`IngestionService.java:167–212`: completeness = clean terminal runs / all terminal runs, rounded to integer.
At inspection: **2,698/2,719 = 99.23% → 99%**. Most runs are individual PDFs (2,654), not comparable datasets.
This does not measure expected records, parsed fields or domain coverage. Rename it or change its denominator;
show dataset-stage completeness separately. Source expected populations remain unknown.

## Pagination, watermarks and drift

- CTB pages: 200/page, max 250; at cap the loop exits without recording truncation (`CtbConnector:33–34,100–118,205–214`).
  No current evidence of cap truncation: latest invoice pages 1,174 rows; links 639; revenue 374; products <200/day.
- `runConnector` always passes null watermark (`IngestionService:82–85`); runner has no output advancement
  (`ConnectorRunner:108–110`). Revenue re-fetches history; product pull re-fetches a rolling **91 inclusive days**.
  Not a verified incremental watermark system.
- Revenue and product pages both use `fetcherIdentity=ctb-revenue` (`CtbConnector:102,147`), losing dataset type
  in the ledger metadata. JSON field shape distinguishes them, but daily product request windows are not persisted.
- Latest product `saleDate` is null on all 12,951 rows. Replay cannot confidently recover each date from payload
  alone; request window, page index and dataset ID should be stored. Sequential request ordering is an inference,
  not durable provenance.
- CTB parsers return empty for missing/non-array `data` (`CtbRevenueParser:36–39`, `CtbSaleItemParser:23–25`).
  Explicit missing required row values are not uniformly validated; `.path(...).decimalValue()` can default.
  Valid empty results and schema drift therefore need distinct outcomes.
- Lightspeed required headers are validated, but absent optional money is defaulted to zero when aggregated.
  There is no structured missing-field/validation summary.
- HTTP CTB client lacks an explicit request timeout/status validation (`CtbClient:249–280`); no measured current hang.

## Idempotency and recovery

Raw storage is append-only and **does not deduplicate** on hash. CTB 3,691 payloads / 1,389 hashes;
Lightspeed 24 / 11 (C05). Repeated evidence is legitimate; do not add a blanket unique raw digest.
Canonical `sameFact` prevents unchanged inserts, partial unique indexes prevent duplicate current keys.
That is idempotency **at the chosen key**, not proof the chosen key is correct.

CSV sequence identity means reordered rows update identities and shrinkage leaves trailing current lines;
one such tail candidate is present (payload-analysis). Cross-supplier invoice collisions exacerbate this.
PDF match returns AMBIGUOUS without modifying rows; the caller flags only NO_MATCH
(`CanonicalInvoiceLineEnrichment:58–60`, `InvoicePdfEnrichmentService:61–68`). Ambiguity can be silent.
Parsed PDF output and parser version are not stored, and enrichment may use OCR/LLM fallback;
re-running raw PDFs is not demonstrated to deterministically reproduce present enriched fields.
Existing Lightspeed backfill class reparses raw daily reports; no general stage-aware replay API was found.

## Proposed accounting contract (additive recommendation)

For each **dataset attempt**, retain parent run, source dataset ID, request/business window,
page ordinal/expected total, raw ID/hash, parser version, and stage outcomes:

`Fetched → Persisted → Parsed → Validated → Canonicalised → Projected → Queryable`

At each stage record success/empty/partial/rejected/not-applicable, timestamps, safe reason codes,
grain, input/output counts, control sums and contributing-record mappings. PDF augmentation and raw-only
reference datasets must explicitly mark intended canonical outcome, not fail naive equality checks.

Reconciliation rules should be grain aware:

- Daily sales: source row money/tax sums and distinct trading dates, not pages==daily rows.
- Products: sum quantity/amount within source/window/product identity; account for variants/grouping.
- CSV: header/source IDs, eligible lines, skipped blank descriptions, per-invoice controls and removals.
- PDF: extracted lines → matched/ambiguous/unmatched/unchanged/enriched; field-level CSV/PDF provenance.
- Reservations/timesheets: stable event/entity identities and status/time/cost validations.

Keep delivery receipt and dataset freshness separate from business-date completeness. Add an operator replay
plan with immutable inputs and versioned parser outputs; actual replay/backfill requires separate approval.
