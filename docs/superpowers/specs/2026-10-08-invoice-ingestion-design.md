# Invoice Ingestion — Design

**Status:** In review
**Date:** 2026-10-08
**Scope:** Design for ingesting thousands of CTB supplier invoices (headers + line items) as a
full target-state pipeline, delivered **CSV-first**. Design only — no implementation or
migrations. Builds on `docs/handoffs/2026-10-08-invoice-ingestion.md` and stays inside the
invariants in `docs/system-context.md`, `docs/connectors/matching-and-identity.md`, and
`docs/architecture/current-state.md`.

---

## 1. Objective

Ingest thousands of CTB supplier invoices reliably, so the venue gets a trustworthy **COGS**
(purchases) figure quickly, and can grow toward richer food-cost analytics (per-unit cost,
COGS-by-category) as PDF extraction and matching mature.

The user's direction, confirmed in this session: a **stable set of invoice "invariants"** (a
canonical JSON contract every invoice must satisfy regardless of layout), with the CSV and the
PDF both treated as *extractors* that emit that same contract, and **confidence-scored matching**
to map extracted fields onto known products and suppliers. Low-confidence matches surface into
the existing reconciliation/override surface rather than being silently accepted or dropped.

## 2. Decisions made (this session)

| # | Decision | Choice |
|---|---|---|
| A | Scope | **Full target-state spec, CSV-first delivery.** Phase 1 ships a reliable COGS-from-CSV slice; PDF extraction + matching land in later phases. |
| B | PDF-only fields that drive a metric now | **`category` + `uom`/`unit_quantity`/`pack_size`** become structured canonical columns. `WET`, banking, delivery → raw `extra` bucket. |
| C | Supplier identity key | **Normalized supplier name** (via `ProductNameKey.normalize`). `SupplierCode` is mislabeled (holds a customer account code) and `SupplierGLCode` is unverified; both codes are stored as secondary data only. |
| D | Extraction mechanism | **Hybrid** — deterministic per-supplier template parser when a layout is known; LLM (Spring AI, schema-prompted to emit the canonical JSON contract) for unknown/variable layouts. CSV is always deterministic (column map). |
| E | Match confidence bands | **Auto-accept ≥ 0.90, review 0.50–0.90, reject/flag < 0.50.** |
| F | Low-confidence routing | **Write canonical with best-effort match + `match_confidence`/`match_status`; surface in a review queue resolved via append-only alias overrides.** |
| G | CSV↔PDF line authority | **CSV is authoritative.** PDF enriches, never overrides CSV line totals; PDF-only lines (no CSV counterpart) are flagged. |
| H | SFTP drop contents | Drop may contain CSV and/or PDFs; the CSV's `PDF` filename column is the exact filename appearing in the drop and is the join key. The drop must catch everything. |
| I | Scanned PDFs / OCR | **Deferred.** No scanned invoices observed. The pipeline flags (a) invoices with no associated PDF and (b) PDFs with no extractable text (scans), so OCR can be added later without re-ingesting. |
| J | Currency | **Assumed always AUD.** A non-AUD currency does not break ingestion — it is stored as-is and flagged. |
| K | Known-product-keys universe | **Unresolved** (assumption below in §8.2, to validate in Phase 1). |

## 3. Scope and non-goals

### In scope

- A canonical **`ExtractedInvoice` JSON contract** (invariants + `extra`) shared by CSV and PDF.
- The pipeline stages: raw → extract → normalize → match → canonical → review/resolve.
- A new `InvoiceExtractor` port with CSV and PDF implementations emitting the same contract.
- A `MatchingService` with layered, confidence-scored product and supplier matching.
- Canonical write with per-row `match_confidence` + `match_status`, and an append-only alias
  override entity for the review queue.
- An SFTP-drop poller that delivers files to the existing HTTP ingest endpoints.
- A flagging/exceptions surface for the "observable ingestion" invariant (missing PDF, scanned
  PDF, non-AUD currency, unparseable quantity).
- Delivery phasing (CSV-first).

### Non-goals (explicitly unchanged)

- No OCR for scanned PDFs (deferred — §14).
- No ML embedding matching (deferred until deterministic matching proves insufficient).
- No `canonical_supplier` / `canonical_product` master entities (deferred — §14).
- No WET / banking / delivery as metrics (stored in `extra` only).
- No write-back to CTB, at any phase.
- No freeform SQL or generic AI query path; AI access stays tool-mediated.
- No change to stock-count or wastage ingestion (still source-deferred).

## 4. Source-data reality (the correction)

The Oct 6 "next-domains" spec recorded: *"the CSV carries only invoice metadata; the PDFs carry
the line items."* **That is now known to be wrong.** The actual CTB Custom Invoice Export is a
single CSV with **both header and line columns**:

- **Header columns:** `OutletName`, `SupplierGLCode`, `InvoiceDueDate`, `InvoiceCreatedDate`,
  `InvoiceUpdatedDate`, `InvoiceTotalExTax`, `InvoiceTaxFlag`, `InvoiceFreight`,
  `InvoiceFreightExTax`, `DocumentType`, `CreatedBy`, `OutletValue1..4`, `OutletCode`,
  `SupplierCode`, `Supplier`, `Date`, `Invoice`, `Total`, `GST`, `PONumber`, `PDF`.
- **Line columns:** `StockCode`, `StockDescription`, `StockGLCode`, `LineQuantity`,
  `LineUnitCost`, `LineUnitCostExTax`, `LineTotal`, `LineTotalExTax`, `LineTax`, `LineTaxFlag`,
  `LineDiscount`.

Consequences:

- `CtInvoiceCsvParser` is **stale** — it validates the *old* header names (`Co./Last Name`,
  `Supplier Invoice #`, `Purchase#`, `Amount`, `Tax Code`, `GST Amount`, `Freight Amount`,
  `Freight GST Amount`, `Inc-Tax Amount`) and will throw `CONNECTOR_SCHEMA_MISMATCH` on the new
  export. It also parses **headers only**.
- The CSV alone can produce header **and** line rows, so it is sufficient for COGS. The PDF is a
  **richer-but-fragile enrichment** of the same lines (adds pack size, unit quantity, UOM,
  category, WET, delivery, banking), not a separate line source.
- The CSV `PDF` column holds only a **filename**; the PDFs are delivered separately.

## 5. Binding architecture invariants (reused, not reinvented)

- **Byte-faithful raw first** — `IngestionService.ingestPush` already stores CSV and PDF bytes
  verbatim before any parsing; this is unchanged.
- **Bitemporal canonical, supersede-not-edit** — corrections close a row and insert a successor
  sharing the same logical identity (`invoice_number` for headers; `invoice_number:lineSeq` for
  lines). Unchanged.
- **Connectors one-way** — no write-back to CTB. Unchanged.
- **Business consumers read resolved views** — COGS is `InventoryProjector`'s `resolved_inventory_day`,
  computed from line totals grouped by invoice date, never from header totals. Unchanged.
- **Observable ingestion** — failures and anomalies are first-class states, not silent gaps.

## 6. The canonical JSON contract (`ExtractedInvoice`)

The single contract both extractors emit. Every extraction must satisfy the invariants; anything
layout-specific lives in `extra` (a free-form object, persisted verbatim). Nullable fields are
absent/`null` when unknown — absence is never fatal, but a missing **required** field marks the
invoice incomplete and routes it to review (§9.4).

```
ExtractedInvoice {
  schema: "goldys.invoice/v1",          // contract version
  invariants {
    invoice_number   string             // REQUIRED — join/dedup key (CSV: "Invoice")
    supplier {
      name           string             // REQUIRED — display name (CSV: "Supplier")
      key            string             // normalized (ProductNameKey.normalize(name))
      codes          { supplier_code?, supplier_gl_code? }  // secondary only
    }
    invoice_date     date               // REQUIRED (CSV: "Date")
    due_date         date?              // CSV: "InvoiceDueDate"
    currency         string             // default "AUD"; non-AUD flagged, never fatal
    totals {
      subtotal_ex_tax number?           // CSV: "InvoiceTotalExTax"
      tax             number?           // CSV: "GST"
      total           number?           // CSV: "Total"
      freight         number?           // CSV: "InvoiceFreight"
      freight_ex_tax  number?           // CSV: "InvoiceFreightExTax"
    }
  }
  line_items[] {
    seq                int              // 1-based order within the invoice
    code               string?          // CSV: "StockCode" (strong secondary key)
    description        string           // CSV: "StockDescription" (REQUIRED)
    quantity           number?          // CSV: "LineQuantity" (coarse — see §6.1)
    uom                string?          // structured (decision B)
    unit_quantity      number?          // structured (decision B) — e.g. 48
    pack_size          number?          // structured (decision B) — e.g. 4
    unit_cost_ex_tax   number?          // CSV: "LineUnitCostExTax"
    line_total_ex_tax  number           // REQUIRED — drives COGS (CSV: "LineTotalExTax")
    tax_flag           boolean?         // CSV: "LineTaxFlag"
    category           string?          // structured (decision B)
    extra              object?          // WET, per-line layout-specific fields
  }
  extra {                               // invoice-level layout-specific fields
    pdf_filename, document_type, created_date, updated_date, created_by,
    outlet_name, outlet_code, outlet_value_1..4, po_number, account_number,
    tax_code, banking, delivery, abn, ...
  }
}
```

**Field-to-column map** (CSV) is embedded above in comments. PDF extraction populates the same
fields where the layout exposes them; anything the PDF has that has no structured home (WET,
delivery, banking, ABN) goes into the appropriate `extra`.

### 6.1 Coarse `LineQuantity`

The CSV collapses quantity to strings like `"4 CTN / 48 EACH"`. Parse **best-effort** into
`pack_size` (4), `uom` (CTN), `unit_quantity` (48), `uom` (EACH) and a `quantity`; on any
ambiguity or unparseable value, leave the structured fields `null`, preserve the raw string in
`line.extra.raw_quantity`, and flag (`quantity-unparseable`, non-fatal — §11). `line_total_ex_tax` is the
COGS driver and is **not** derived from quantity — so a coarse quantity never corrupts COGS.

## 7. Pipeline stages

```
raw → extract → normalize → match → canonical → review/resolve
```

1. **Raw** — each file (CSV, PDF) is stored byte-faithfully via `IngestionService.ingestPush`
   (existing), yielding a `raw_record` id. Replayable and unchanged.
2. **Extract** — `InvoiceExtractor` produces an `ExtractedInvoice` (no canonical writes, no
   metric math). CSV is a deterministic column map; PDF is text → hybrid (§8.1).
3. **Normalize** — coerce and validate: `ProductNameKey.normalize` for product descriptions and
   supplier names; money/quantity parsing; currency defaulting; invariant completeness check.
4. **Match** — `MatchingService` assigns a canonical key + confidence to supplier and each line's
   product (§9).
5. **Canonical** — write `canonical_invoice` + `canonical_invoice_line` (bitemporal), each row
   carrying `match_confidence` + `match_status` (§9.4). Supersede-not-edit on the existing logical
   identities.
6. **Review/resolve** — rows with `match_status ∈ {REVIEW, REJECT}` (or incomplete invariants)
   surface in a review queue; a human resolves via append-only alias overrides, then
   `InventoryProjector` recomputes `resolved_inventory_day` (unchanged).

Normalize and match are **shared** by both extractors — they operate on the contract, never on a
vendor schema.

## 8. Extraction

### 8.1 `InvoiceExtractor` port

```
InvoiceExtractor {
  ExtractedInvoice extract(byte[] content, String contentType, InvoiceAttribution attribution)
}
```

- `InvoiceAttribution` carries externally-known facts (`invoiceNumber`, `invoiceDate`) for the PDF
  path, mirroring today's `CtInvoicePdfIngestService.ingest(pdf, invoiceNumber, invoiceDate)`. The
  PDF text layout is unconfirmed, so the caller supplies these rather than the extractor guessing.
- **`CsvInvoiceExtractor`** replaces `CtInvoiceCsvParser`; it maps the new header + line columns
  (deterministic) and does not require line items to come from PDFs.
- **`PdfInvoiceExtractor`** reuses the existing `DocumentTextExtractor` port (`PdfBoxTextExtractor`
  is the first impl) for text, then a hybrid text→JSON step:
  - a **deterministic per-supplier template** when the supplier's layout is registered/stable;
  - an **LLM schema-prompted** step (Spring AI, already wired) for unknown/variable layouts.
  Both emit the same `ExtractedInvoice`; the LLM is forced to the contract schema.

The existing `CtInvoiceCsvIngestService` / `CtInvoicePdfIngestService` are refactored to call the
shared `InvoiceIngestPipeline` (normalize → match → canonical) rather than each owning its own
canonicalization.

### 8.2 PDF enrichment vs. CSV authority

Phase 2: a PDF line is joined to its CSV line by `(invoice_number, code)` when `code` matches, else
`(invoice_number, normalized description)`. PDF provides `uom`/`unit_quantity`/`pack_size`/
`category` and `extra` fields; **CSV `line_total_ex_tax` remains authoritative**. Where CSV and PDF
totals disagree, CSV wins and the discrepancy is flagged. A PDF-only line (no CSV counterpart) is
flagged and held for review rather than silently creating a canonical line. The exact join key
(`code` vs. description) is validated against real data in Phase 1 (§15).

## 9. Matching service + confidence

### 9.1 Known-keys universe

There is no `canonical_product`/`canonical_supplier` master, so matching resolves to **canonical
string keys**, not row IDs. The known-key universe is derived at match time from already-canonical
data:

- **Suppliers:** the distinct `supplier.key` values in `canonical_invoice` (grown by each
  invoice as it is canonicalized) plus any human-approved alias overrides.
- **Products:** the distinct `product_name_key` values in `canonical_invoice_line` **and**
  `canonical_product_sales` (the POS-side name keys), plus alias overrides. `code` (`StockCode`)
  is a strong secondary key.

*Assumption to validate (decision K):* POS product name keys (menu-facing) and supplier stock
descriptions may be disjoint vocabularies. If so, product matching effectively canonicalizes
supplier stock codes/descriptions *within* the invoice universe rather than joining to POS items —
which is fine for COGS (totals are keyed by invoice date) and only affects per-unit/category
analytics. This does not block Phase 1.

### 9.2 Algorithm (layered, deterministic first)

For a raw string `s` against a candidate canonical key `k`, in order:

1. **Exact** — normalized `s == k` (or `StockCode` exact) → confidence `1.0`, strategy `EXACT`.
2. **Normalized edit distance** — Levenshtein and Jaro-Winkler similarity on
   `ProductNameKey.normalize(...)`; mapped to `[0,1]`.
3. **Token similarity** — token-set / Jaccard overlap of normalized tokens, weighted by
   token frequency (rarer tokens weigh more).

The three combine (weighted: exact dominates, then edit distance, then tokens) into a single 0–1
score. `MatchingService.matchProduct(code, description)` and `matchSupplier(name)` each return
`(canonicalKey, confidence, strategy, alternatives[])` where `alternatives` are the top near-misses
a human might pick in review.

### 9.3 `ProductNameKey` growth

`ProductNameKey.normalize` is the seed (lowercase, strip non-alphanumerics, fold leading "new ").
It grows into `MatchingService` plus a tokenizer and the alias-override lookup. No change to the
normalization *contract* (existing canonical keys stay stable); only the matching layer around it
is added.

### 9.4 Confidence → canonical → review

- `canonical_invoice` gains `supplier_name_key`, `match_confidence`, `match_status` (and stores
  supplier codes in `extra`).
- `canonical_invoice_line` gains `stock_code`, `uom`, `unit_quantity`, `pack_size`,
  `match_confidence`, `match_status` (and `category` is now populated when known).

Thresholds: `≥ 0.90` → `AUTO`; `0.50–0.90` → `REVIEW`; `< 0.50` → `REJECT` (also used for
unparseable/incomplete invariants). **All rows are still canonicalized** with their best-effort
key (decision F) — the confidence flag makes them visible, it does not block COGS.

The review queue is a query over `match_status ∈ {REVIEW, REJECT}` plus incomplete invoices. A
human resolves by writing an **append-only alias override** (bitemporal, mirroring
`InventoryOverrideService`): `ProductAliasOverride(raw → canonical product key)` and
`SupplierAliasOverride(raw → canonical supplier key)`, each with actor/reason/
`recorded_at`/`superseded_at`. Future matching consults overrides first, then the algorithm.

## 10. SFTP drop + poller

SFTP was already chosen over SMB. Design:

- CTB delivers files to an SFTP drop; the drop may contain CSV and/or PDFs (decision H).
- A **poller** (same `@Scheduled` + `@ConditionalOnProperty` pattern as `CtbScheduledPull`,
  e.g. `app.scheduling.ctb-sftp.enabled`, cron in `Australia/Melbourne`) lists the drop, downloads
  new files, and **POSTs them to the existing HTTP endpoints** (`/api/ingest/ctb-invoices` for CSV,
  `/api/ingest/ctb-invoices/pdf` for PDF, with `invoiceNumber`/`invoiceDate` from the CSV's
  `Invoice`/`Date` columns when a matching CSV row is known). Ingestion stays HTTP-bounded and the
  existing token gate (`ctb.drop-token`) is unchanged.
- Transport library: Spring Integration SFTP inbound adapter, or a lightweight Apache Commons VFS /
  JSch poller — decision deferred to implementation, kept behind a small `SftpDrop` port so the
  transport is swappable.
- **Idempotency:** move downloaded files to a `processed/` subfolder and/or track filenames, so a
  re-poll never re-ingests. The raw ledger's dedup is a second guard, not the primary one.
- **Association:** the CSV's `PDF` filename joins a PDF to its invoice. When a CSV row references a
  PDF filename that is absent from the drop (or a PDF has no CSV), that is flagged (§11).

## 11. Observability & flagging

Every anomaly is a first-class, queryable flag, never a silent drop:

| Anomaly | Behaviour |
|---|---|
| CSV references a `PDF` filename not present in the drop | Flag `missing-pdf`; invoice still canonicalized from CSV (CSV is authoritative). |
| PDF present with no extractable text (scan) | Flag `scanned-pdf`; raw stored, no canonical lines; queued for future OCR. |
| Currency ≠ AUD | Flag `foreign-currency`; stored as-is, totals preserved, not fatal. |
| `LineQuantity` unparseable | Flag `quantity-unparseable`; structured qty fields null, raw kept in `extra`. |
| CSV↔PDF line-total disagreement | Flag `pdf-csv-total-mismatch`; CSV wins. |
| Missing required invariant (e.g. no `invoice_number`) | Flag `incomplete-invariant`; held in review, not canonicalized. |

Flags reuse the existing ingestion-ledger/exception surface where possible; they are distinct from
the raw ledger and are how the venue sees "what still needs attention" without a data scientist.

## 12. Delivery phasing

- **Phase 1 — COGS from CSV (reliable, thousands-scale).** New `CsvInvoiceExtractor` (header +
  line columns) → normalize → match (supplier name key; product matching optional for COGS) →
  canonical → `InventoryProjector` COGS. The SFTP poller lands CSV files. This is the thin,
  trustworthy slice that unlocks COGS immediately.
- **Phase 2 — PDF enrichment.** `PdfInvoiceExtractor` (hybrid) enriches lines with
  `uom`/`unit_quantity`/`pack_size`/`category`; join + authority rules from §8.2.
- **Phase 3 — matching polish.** Confidence review queue + alias overrides in the UI; tune
  thresholds and token weighting against real data; consider ML embeddings only if deterministic
  matching is insufficient.

## 13. Testing

- **Contract tests** — both extractors emit a valid `ExtractedInvoice` against fixture CSV and PDF
  text (including the "4 CTN / 48 EACH" quantity and a non-AUD row).
- **Stale-parser regression** — the new CSV parser accepts the current header set and rejects a
  malformed file loudly.
- **Matching unit tests** — exact, edit-distance, and token layers; threshold boundaries (0.90 /
  0.50); alias-override precedence.
- **Canonicalization** — supersede-not-edit on re-ingest; `match_confidence`/`match_status`
  persisted; incomplete invariants held in review, not canonicalized.
- **Projector** — COGS = sum of line totals by invoice date; header totals never used; override
  wins over computed value.
- **SFTP poller** — idempotent re-poll; missing-PDF / scan / non-AUD flags emitted.
- **Architecture tests** — extend `ArchitectureBoundariesTest`: extractors never read canonical;
  the pipeline is the only path to canonical; reporting/AI read resolved views only.

## 14. Out of scope / known limitations

**Out of scope (this spec):** OCR for scanned PDFs; ML embedding matching; `canonical_supplier` /
`canonical_product` master entities; WET / banking / delivery as metrics; CTB write-back;
stock-count / wastage ingestion.

**Known limitations:**

- CSV line quantities are coarse; unit-level costing is best-effort from CSV and authoritative
  from PDF only when a PDF is present and parseable.
- PDF `invoice_number`/`invoice_date` are caller-supplied (not reliably extractable from the PDF
  text today); if a PDF arrives without a CSV anchor, its attribution is incomplete.
- Matching is string-canonicalization, not ID resolution — it cannot distinguish two genuinely
  different products that normalize to the same key.
- LLM extraction is non-deterministic; it needs eval fixtures and a per-run confidence/audit note.

## 15. Open questions remaining (not blocking this spec)

1. **CSV↔PDF line join key** — is `(invoice_number, StockCode)` reliable, or is
   `StockDescription` (normalized) the safe join? Validate against real data in Phase 1.
2. **Product-key universe** (decision K) — are POS product name keys and supplier stock
   descriptions disjoint? Determines whether product matching is intra-invoice canonicalization
   or a POS join.
3. **`LineQuantity` format** — is `"4 CTN / 48 EACH"` a consistent, parseable format across
   suppliers, or supplier-dependent?
4. **SFTP transport library + credentials** — Spring Integration SFTP vs. a lighter JSch/VFS
   poller; how credentials are provisioned (deferred to implementation).
5. **Review-queue UX scope** — which roles see/resolve `REVIEW`/`REJECT` rows (ties to the
   field-to-role permission matrix already tracked in `system-context.md`).
