# Invoice PDF viewer (design)

Status: proposal — awaiting approval before implementation.
Findings addressed: F20 (no PDF↔canonical FK, so calculated numbers can't navigate to their source document); the broader gap that owner-facing invoice PDFs are stored but not viewable.

## Problem

The platform stores the original CTB supplier invoice PDFs byte-faithfully in the raw ledger
(`raw_record`, `content_type = application/pdf`, `fetcher_identity = ctb-invoice-pdf`), and enriches
them onto `canonical_invoice_line`. But there is no way for an owner to *open* the actual document
from the web app:

1. There is **no persisted link** from an invoice to its PDF raw record. The filename→invoice join
   is made only transiently in memory during enrichment (`InvoicePdfEnrichmentService.enrich` →
   `CanonicalInvoiceQuery.invoiceNumberForPdfFilename`); the PDF raw record is written with
   `source_record_ref = null` (`CtbSftpPull.process` → `IngestionService.ingestPush`), so the
   filename is dropped.
2. Every read endpoint returns the bytes as **JSON + base64** (`RawRecordDetail`), never as
   `application/pdf`, so even the Data Explorer can only show a base64 dump, not a renderable PDF.
3. There is **no invoice-facing screen**: the Kitchen screen shows the supplier→invoice→line graph
   and ingestion flags, but not a browsable invoice list with the source document.

## Facts to design around

- The raw ledger is **append-only** (a DB trigger rejects UPDATE/DELETE). Historical PDF rows
  (~2.6k at the last audit) therefore cannot be retroactively tagged with a filename; linking them
  requires a separate backfill that re-derives each PDF's invoice number from its stored bytes.
- `InvoicePdfEnrichmentService.enrich(byte[] pdf, String pdfFilename, UUID pdfRawRecordId)` already
  holds all three pieces needed to record the link (resolved invoice number, PDF raw id, filename).
- `RawLedgerQuery.rawPayloadsForSource("CTB", "application/pdf")` and `payloadBytes(id)` already
  expose the stored PDF bytes for replay/serving.
- Invoice number is **not globally unique** — the in-flight identity proposal
  (`docs/superpowers/specs/2026-10-12-invoice-identity-design.md`) documents 2 cross-supplier
  collisions and moves headers to supplier-scoped identity. This design must not assume
  `invoice_number` is a stable primary key (see §1 and Open questions).
- Authorization is cookie/session-based with CSRF; a same-origin `<iframe>` to a PDF endpoint
  carries the session automatically.
- Invoice data is already gated on the `inventory.cost` READ resource (`InvoiceGraphService`,
  `InvoiceIngestFlagQueryService`); the Data Explorer raw browse is gated on `connectors` READ.
- `hibernate.ddl-auto: validate` — the new table needs a Flyway migration (next version V51).
- Package rules: `api` never reaches `canonical`/`RawLedgerQuery`; `application` composes and
  authorizes and may reach `canonical`/`ingestion`; `connectors` may reach `canonical`/`ingestion`.

## Design

### 1. Data model — `invoice_pdf_link` (Flyway `V51__invoice_pdf_link.sql`)

A plain idempotent evidence table (like `invoice_ingest_flag`), **not** bitemporal and **not** a
column on `canonical_invoice` (a provenance pointer must not force a supersession of the invoice
fact).

```
invoice_pdf_link(
  pdf_raw_record_id  uuid          PRIMARY KEY,   -- the anchor: one document, one row
  invoice_number     varchar(255)  NOT NULL,       -- current invoice key (lookup direction)
  pdf_filename       varchar(512),                 -- SFTP filename when known (live path)
  link_source        varchar(32)   NOT NULL,       -- 'ENRICHMENT' | 'BACKFILL'
  linked_at          timestamptz   NOT NULL
)
CREATE INDEX idx_invoice_pdf_link_invoice ON invoice_pdf_link(invoice_number);
```

- **PK on `pdf_raw_record_id`** (the stable, unique document), not `invoice_number` — because the
  identity proposal shows invoice numbers can collide across suppliers. The viewer looks up
  `WHERE invoice_number = ?`; today that returns at most one row.
- No DB-level FK to `raw_record` (consistent with existing `raw_record_id` columns, which store
  UUIDs without a FK).
- Idempotent upsert: re-linking the same document is a no-op.

### 2. Writing the link — forward path

`InvoicePdfEnrichmentService.enrich(...)`, after it resolves a known invoice number, records the
link via a new `InvoicePdfLinkService.link(invoiceNumber, pdfRawRecordId, pdfFilename,
ENRICHMENT)`. Unresolved PDFs write nothing (they are already flagged `AMBIGUOUS_PDF`). This covers
both the live SFTP path and the replay path (filename may be null).

**Placement:** the `InvoicePdfLink` entity, its JPA repository and `InvoicePdfLinkService` live in
the `canonical` package (next to `CanonicalInvoiceQuery`). `connectors/ctb` (enrichment + the
backfill runner) writes through it; `application` (the invoices screen / document service) reads
through it. Both directions are within the existing dependency allowances
(`connectors → canonical`, `application → canonical`).

### 3. Backfill — one-off runner

`InvoicePdfLinkBackfillRunner` (mirrors `CtbInvoicePdfReenrichRunner`), gated by
`app.ctb-invoice-pdf-link-backfill` (default **off**):

- Iterate `RawLedgerQuery.rawPayloadsForSource("CTB", "application/pdf")`.
- Skip records already present in `invoice_pdf_link`.
- For each: `DocumentTextExtractor` → `InvoicePdfExtractor` to recover the invoice number →
  resolve via `CanonicalInvoiceQuery.isKnownInvoice` → `link(..., BACKFILL)`.
- Log a summary (linked / already-linked / unresolved). Re-runnable (upsert).
- The unresolved tail is the set whose text invoice number is unparseable or unknown — they remain
  flagged, nothing lost.

### 4. Serving the PDF — endpoint

New `InvoiceController` under `/api/invoices`:

- `GET /api/invoices/{invoiceNumber}/pdf?disposition=inline|attachment` (default `inline`).
- Application service (`InvoiceDocumentService`) authorizes (`inventory.cost` READ), resolves the
  link → `RawLedgerQuery.payloadBytes(id)`, returns bytes + a filename for the header.
- Controller returns `ResponseEntity<byte[]>` with `Content-Type: application/pdf`,
  `Content-Disposition: inline|attachment; filename="<pdf_filename or invoice>.pdf"`,
  `Content-Length`.
- **Errors:** no link → 404; denied → 403 via the existing `AccessDeniedException` handler.

### 5. Invoices screen

- `GET /api/invoices?supplier=&from=&to=&page=&size=` → paged summaries
  (`supplier, invoiceNumber, invoiceDate, dueDate, totalAmount, purchaseNumber, hasPdf`).
- `GET /api/invoices/{invoiceNumber}` → header + lines for drill-in.
- Both backed by a new `application.InvoiceReportingService` (reads `CanonicalInvoiceQuery` + the
  link table; `hasPdf` = a link row exists for the invoice).
- Frontend: new `app/(app)/invoices/page.tsx` — a `DataTable` of invoices (reuse existing table
  components), row drill-in showing lines, **Preview** (`<iframe>` to the inline PDF endpoint) and
  **Download** (`<a>` to the attachment URL). New `Api` methods with live + demo implementations
  (`frontend-api-method` pattern), and an "Invoices" item in the app-shell nav.

### 6. Authorization

Reuse the **`inventory.cost`** READ resource (the same resource already gating the Kitchen purchase
lineage and invoice flags). No new resource or seed migration. A dedicated `invoices` resource is
possible later if document access should be separately grantable; not now.

### 7. Documentation and test plan

- Update `docs/connectors/matching-and-identity.md` (invoice metadata section) to record the
  `invoice_pdf_link` evidence table and how invoice→PDF resolution works.
- Backend tests: link-service idempotency; enrichment writes the link; backfill runner
  (resolvable / already-linked / unresolved); endpoint returns correct bytes + headers, 404 on
  no-link, 403 when denied; `ArchitectureBoundariesTest` stays green (the new controller reaches no
  `canonical`/`ingestion` type).
- Frontend tests: invoices list, drill-in, and preview/download affordances render; demo fixtures.
- Full gate: `./gradlew test spotlessCheck`; `bun run typecheck && bun run lint && bun run test &&
  bun run build`.

## Open questions / risks

- **Interaction with supplier-scoped identity** (`2026-10-12-invoice-identity-design.md`): once
  invoice headers are supplier-scoped, the link's `invoice_number` lookup column becomes ambiguous
  for the (currently 2) cross-supplier collisions. If that spec lands first, widen the link to carry
  `supplier_name` (or a supplier-scoped ref) and update the invoices screen's key accordingly. The
  link's `pdf_raw_record_id` PK is already robust to this; only the lookup direction needs widening.
- **Backfill tail:** the re-derivation approach leaves a small set of PDFs unresolved (unparseable
  text invoice number). If that tail matters, the SFTP `/processed` directory still holds the
  original files (the pull *moves*, not deletes), enabling a filename-based re-pull fallback — not
  built now.
- **Memory vs streaming:** `ResponseEntity<byte[]>` loads the whole PDF into memory, consistent with
  the existing `RawRecordDetail` base64 path; switch to `StreamingResponseBody`/`Resource` only if
  PDFs prove large.
