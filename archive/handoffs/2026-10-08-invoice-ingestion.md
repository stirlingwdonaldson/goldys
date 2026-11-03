# Invoice Ingestion — Handoff / Context (2026-10-08)

> Purpose: brief a fresh agent on the project, what was already done in this session, and where the
> invoice-ingestion design conversation stands, so it can pick up without re-deriving anything.

## 1. What this project is

Goldy's Unified Data Platform — data integration + analytics for a pub (Goldy's). Consolidates
disconnected systems into one source of truth: **Lightspeed** (POS), **CTB / "Cooking the Books"**
(inventory + accounting + a second sales source), **OpenTable** (reservations), **Deputy** (labour).

- **Backend:** Java 25, Spring Boot 3.5, PostgreSQL 16 (Flyway), Spring AI (OpenAI-compatible).
- **Frontend:** Next.js (App Router) + TypeScript + Tailwind + shadcn/ui (Bun).
- **Three-layer data model (the core invariant):**
  1. **Raw ledger** — append-only, byte-faithful (`raw_record`), every ingested thing stored exactly
     as received (CSV, JSON, PDF, webhook).
  2. **Bitemporal canonical entities** — vendor-agnostic facts (`canonical_invoice`,
     `canonical_invoice_line`, `canonical_daily_sales`, …), closed-not-edited (superseded rows kept).
  3. **Recomposable resolved views** — derived from canonical by reconciliation
     (`resolved_daily_sales`, `resolved_inventory_day`, …).
- **Reconciliation:** field-level, with manual **overrides** and standing **rules**, all append-only
  with audit (`RuleAuditService`). Low-confidence/mismatched data has a manual-resolution path.
- **Invariants:** connectors are one-way (never write back to source systems); business consumers
  read resolved views only; the AI ("Ask Goldy's") is tool-mediated (no SQL/free-text).

Read `docs/system-context.md` (architecture invariants) and `docs/prd.md` (scope) first.

## 2. Where `main` is right now

Both feature branches from this session are **already merged**:

- **PR #59 — "Ask Goldy's analytical expansion"** (multi-step reasoning, 10-tool catalogue,
  per-metric authorization, conversation persistence, provenance envelope, eval suite).
- **PR #60 — "Provenance, trust, and data-quality UX (PASS 9)"** (derived `TrustState`/`FreshnessState`
  model, `TrustQuery`, provenance drill-down, trust UI, Ask Goldy's trust metadata).

`main` also contains, merged after those: PR #61 (data-source research + a data explorer), PR #62
(fix daily-sales reconciliation), PR #63 (CTB invoice CSV ingestion + scheduled CTB pull). Local
`main` == `origin/main` (`cbe6db4`). Migrations on main go **V1–V28**.

## 3. The raw-data investigation (done this session)

Found raw records sitting unprocessed in the production DB. Root causes and status:

- **Lightspeed sales — FIXED.** A Lightspeed (Looker) report schema change broke the old CSV parser
  (fixed in commits `6ad9661` + `2c92a76` on Oct 6). 19 raw records (Sep 25–27) were stuck. Ran the
  one-off backfill (`APP_LIGHTSPEED_BACKFILL=true` + backend restart) → recovered Sep 26/27, Oct 3/4
  sales and corrected Oct 5 (the old parser was dropping refunds/adjustments). The failure was
  invisible because `LightspeedIngestService.ingest()` marks the run SUCCESS *before* the parse step.
- **CTB `ctb-invoices-ajax` (12 raw records) + `ctb-sale-recipe-links` (8)** — deliberately
  raw-only (invoice AJAX path is not canonicalized to avoid double-counting with the CSV path;
  recipe links have no canonical entity). Not bugs.
- **"578 unprocessed CTB revenue" + "15 Lightspeed"** — red herring: paged/multiple raw records per
  date where only the current (non-superseded) canonical row references one raw id.

## 4. The invoice-ingestion design topic (current focus)

The user wants to ingest **thousands of CTB supplier invoices**. The conversation so far:

1. **Protocol: SFTP** (not SMB). `docs/system-context.md` already records CTB's production delivery
   as "Custom Invoice Export (CSV/XLSX → scheduled SFTP/email)". Recommendation: a lightweight SFTP
   drop + a poller that POSTs files to the existing `/api/ingest/ctb-invoices` (CSV) and
   `/api/ingest/ctb-invoices/pdf` (PDF) endpoints — keeps ingestion HTTP-bounded.

2. **The CTB Custom Invoice Export CSV is a *single* file with header AND line columns.**
   The user shared the actual column list:
   - Header columns: `OutletName`, `SupplierGLCode`, `InvoiceDueDate`, `InvoiceCreatedDate`,
     `InvoiceUpdatedDate`, `InvoiceTotalExTax`, `InvoiceTaxFlag`, `InvoiceFreight`,
     `InvoiceFreightExTax`, `DocumentType`, `CreatedBy`, `OutletValue1..4`, `OutletCode`,
     `SupplierCode`, `Supplier`, `Date`, `Invoice`, `Total`, `GST`, `PONumber`, `PDF`.
   - Line columns: `StockCode`, `StockDescription`, `StockGLCode`, `LineQuantity`, `LineUnitCost`,
     `LineUnitCostExTax`, `LineTotal`, `LineTotalExTax`, `LineTax`, `LineTaxFlag`, `LineDiscount`.
   - The `PDF` column is only the **filename**; the actual PDFs are **not** bundled with the CSV.

3. **The existing CSV parser is stale.** `CtInvoiceCsvParser` validates the OLD column names
   (`Co./Last Name`, `Supplier Invoice #`, `Purchase#`, `Amount`, `Tax Code`, `GST Amount`,
   `Freight Amount`, `Freight GST Amount`, `Inc-Tax Amount`) and will throw on the new headers.
   It also only parses invoice *headers* — the old design assumed line items arrive via PDF.

4. **The PDF has richer detail than the CSV.** The user shared an example PDF text extraction
   (Bruno 1956 invoice) showing the CSV collapses `LineQuantity` to `"4 CTN / 48 EACH"` while the PDF
   keeps `Ship Quantity`, `Pack Size`, `Unit Quantity`, `UOM`, `Category`, `WET`, delivery, and
   banking as separate fields. So CSV = reliable-but-coarse; PDF = rich-but-fragile.

5. **The user's stated vision:** flexible OCR/PDF extraction → JSON, with a stable set of invoice
   "invariants" (fields every invoice must have), and matching driven by algorithms/ML (fuzzy match,
   edit distance, confidence scores) to map extracted fields onto existing entities (products,
   suppliers).

## 5. My architecture response (agreed direction, not yet specced)

- **Invariants = a canonical JSON contract** every extraction must satisfy, regardless of layout:
  `invoice_number` (join/de-dup key), `supplier` (name + optional code), `invoice_date`, `due_date`,
  `currency`, `line_items[]` (`code`, `description`, `quantity`, `uom`, `unit_cost_ex_tax`,
  `line_total_ex_tax`, `tax_flag`), totals (`subtotal_ex_tax`, `tax`, `total`), plus an `extra`
  bucket for everything layout-specific (category, WET, delivery, banking, ABN…).
- **Pipeline:** raw PDF → extract → JSON → normalize → match → canonical (bitemporal); low-confidence
  matches route to the existing reconciliation/override UI for human review.
- **Extraction:** `PdfBoxTextExtractor` for text-based PDFs (OCR only for scanned PDFs); the
  text→JSON step is the one place an LLM earns its keep (Spring AI/OpenAI already wired).
- **Matching, layered + confidence-scored:** exact keys (`invoice_number`, `StockCode`, creditor code)
  → normalized edit distance (Levenshtein/Jaro-Winkler) → token-based similarity → a 0–1 confidence
  score with thresholds (auto ≥0.9, review 0.5–0.9, reject <0.5). ML (embeddings) only if
  deterministic matching proves insufficient at scale. `ProductNameKey.normalize(...)` is the
  existing seed of this and should grow into a real `MatchingService`.
- **CSV and PDF should emit the same JSON contract** — CSV becomes just another "extractor", so the
  normalize/match/canonical stages are shared.

## 6. Key files to read

- `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvParser.java` (STALE — old
  column names).
- `.../ctb/CtInvoiceCsvIngestService.java` (CSV → `CanonicalInvoice` headers only).
- `.../ctb/CtInvoicePdfIngestService.java`, `.../ctb/PdfBoxTextExtractor.java`,
  `.../ctb/InvoiceLineTextParser.java` (PDF → text → line items).
- `.../ctb/CtInvoice.java` (old field set).
- `.../canonical/CanonicalInvoice.java`, `CanonicalInvoiceLine.java`, `CanonicalInvoiceIngest.java`,
  `CanonicalInvoiceLineIngest.java` (canonical entities + write facades).
- `.../reconciliation/InventoryProjector.java`, `InventoryProjectionListener.java` (resolved
  inventory from invoice lines).
- `.../canonical/ProductNameKey.java` (product-name normalization — the matching seed).
- `docs/connectors/matching-and-identity.md` (entity identity + the invoice_number join).
- `docs/system-context.md` (per-source ingestion reality, CTB row).

## 7. Open decisions (the next agent must resolve with the user)

1. **Priority:** ship COGS-from-CSV first (reliable, thousands-scale), or build the full flexible
   PDF-extraction + matching pipeline now?
2. **Which PDF-only fields actually drive a metric** (pack size / unit quantity / UOM / category /
   WET / banking), vs. which are "keep in `extra` for later"?
3. **`SupplierCode` is mislabeled** in the export (the user noted `GOLD306600` is a *customer*
   account code, not the supplier creditor code) — supplier identity must not rely on that column.
4. **Extraction mechanism** — LLM text→JSON (schema-prompted) vs. deterministic per-supplier parsers
   vs. hybrid.
5. **Matching confidence thresholds** and how low-confidence items flow into reconciliation.

## 8. Recommended next step

Run the invoice-ingestion work through the full **brainstorm → spec → plan** cycle (it's a feature,
not a small change). The spec must pin the canonical JSON contract, the extraction approach, the
matching service + thresholds, the CSV/PDF-shared-contract decision, and the SFTP-drop poller.
