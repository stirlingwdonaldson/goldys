# Invoice PDF Enrichment (Phase 2) — Design

**Status:** In review
**Date:** 2026-10-08
**Scope:** Phase 2 of invoice ingestion — enrich the already-canonicalized invoice lines with
PDF-only fields (UOM, pack size, WET) where the supplier's PDF carries them, joined to CSV lines
on `(invoice_number, stock_code)`. Builds on
`docs/superpowers/specs/2026-10-08-invoice-ingestion-design.md` (Phase 1). Design only — no
implementation or migrations yet.

---

## 1. Objective

The CSV (Phase 1) already canonicalizes quantity, unit cost and line totals — enough for COGS.
The PDFs carry a few extra fields the CSV lacks, chiefly **unit of measure (UOM)** (is `3.25` kg,
each, or carton?), plus **pack/carton size** and **WET** (Wine Equalisation Tax) on the liquor
invoices. Phase 2 extracts those and attaches them to the existing lines, so the platform can do
unit-level costing (cost-per-kg / per-each / per-carton) and liquor-tax accounting.

## 2. Decisions (this session)

| # | Decision | Choice |
|---|---|---|
| A | Scope | **Full rich extraction** — UOM, pack size, category, WET, wherever the PDF has them. |
| B | Extraction mechanism | **Hybrid** — deterministic per-supplier template where the layout is known/stable; LLM (Spring AI, schema-prompted) fallback for the rest. |
| C | Authority (carried from Phase 1) | **CSV is authoritative** for quantity/unit-cost/line-total; the PDF enriches, never overrides those. |
| D | PDF-only lines | A PDF line with no CSV counterpart is **flagged**, not canonicalized. |

## 3. Source-data reality (measured this session)

- **664 of 666 PDFs are text-based** (2 scanned). Extraction is PdfBox text → parse; OCR is
  deferred for the 2 scans.
- **The CSV↔PDF join key is exact and complete**: the CSV's `PDF` filename column contains 666
  filenames, and all 666 are present in the drop (0 missing).
- **Layouts are heterogeneous** across ~36 suppliers. Field presence across a 40-PDF sample:

  | Field | Present in |
  |---|---|
  | `UOM` (unit of measure) | ~20% (8/40) |
  | `WET` (Wine Equalisation Tax) | ~25% (10/40) — liquor suppliers |
  | `Pack` / `Carton` / cases | ~12% (5/40) — beverage suppliers |
  | `ABN` / `BSB` / delivery | nearly all — supplier reference data |
  | **`Category`** | **0% (0/40) — no PDF carries it** |

- **Consequence:** `category` has **no PDF source**. It cannot be "extracted" from these invoices;
  it needs a separate product→category reference map, which does not exist yet (deferred — §9).
  Phase 2 therefore extracts **UOM, pack size, and WET** (and stores the rest of the PDF text in
  `extra`); the `category` column stays null until a mapping source lands.

## 4. Schema (migration V29)

The canonical tables are at V28 (Phase 1 added no columns). Add to
`canonical_invoice_line` (all nullable — PDF-only enrichment):

```
stock_code     varchar(255)   -- the CSV's StockCode, now persisted, the CSV↔PDF join key
uom            varchar(32)    -- "KG" | "EACH" | "CTN" | "LT" ... (PDF "UNIT"/"UOM")
unit_quantity  numeric(14,4)  -- per-pack unit count when the PDF states it (e.g. 48)
pack_size      numeric(14,4)  -- pack/carton size (e.g. 4 cartons)
wet_amount     numeric(14,4)  -- WET for this line (liquor only)
```

`category` already exists (V22) and is **not** populated by this phase. `stock_code` is new because
Phase 1 parsed the CSV's `StockCode` but dropped it; it must now be persisted so the join works.

## 5. Pipeline

```
raw PDF (already stored) → extract text (PdfBox) → parse (hybrid) → PDF lines
   → join to CSV lines (invoice_number + stock_code) → enrich (supersede-not-edit)
```

The PDF is already byte-faithful in the raw ledger (Phase 1 stored it raw-only). This phase reads
the PDF back — from the SFTP drop by filename (the CSV's `PDF` column) or from the raw record if
the filename is retained — extracts text, parses line items, and enriches the existing canonical
lines. No new raw storage — the raw PDF is already there.

## 6. Extraction

- **Text:** reuse the existing `DocumentTextExtractor` port (`PdfBoxTextExtractor`) — unchanged.
- **Text → structured lines (hybrid, decision B):**
  1. A **deterministic per-supplier template** for suppliers whose layout is known/stable — a
     table-position or anchor-keyword parser mapping the line table's `QTY / CODE / DESCRIPTION /
     UNIT / UNIT PRICE` columns (and the `UOM`/`WET`/`Carton` fields where present).
  2. An **LLM schema-prompted** step (Spring AI) for unknown/variable layouts, forced to the
     contract below.
- **Output contract** (per PDF): an invoice number + line items, each with
  `stock_code`, `description`, `quantity`, `uom`, `unit_quantity`, `pack_size`, `wet_amount`,
  and an `extra` map for everything else (ABN, BSB, delivery, and any supplier-specific text).

## 7. Join + enrichment

- **Invoice attribution:** a PDF's `invoice_number` comes from the CSV's `PDF` filename mapping
  (the CSV row whose `PDF` column equals this PDF's filename) — exact and already 100% matched.
  (The PDF's own text also states the invoice number; the mapping is the reliable key.)
- **Line join:** for each PDF line, match the CSV line on `(invoice_number, stock_code)` (exact),
  falling back to `(invoice_number, normalized description)`. `ProductNameKey.normalize` is the
  normalization.
- **Enrich (supersede-not-edit):** when a line matches, close the current `canonical_invoice_line`
  and insert a successor carrying the same CSV-authoritative `quantity`/`unit_cost`/`line_total`
  **plus** the PDF's `uom`/`unit_quantity`/`pack_size`/`wet_amount`. The PDF never overwrites the
  CSV's totals.
- **PDF-only line** (no CSV match): flag `pdf-only-line`, do not canonicalize.
- **CSV line with no PDF** (or a scanned PDF): left as-is; optionally flag `missing-pdf` /
  `scanned-pdf`.

## 8. Flags

All non-fatal, surfaced (Phase 3 will wire the flag persistence; for now they log + are returned):

| Flag | When |
|---|---|
| `scanned-pdf` | PDF has no extractable text (the 2 scans) |
| `pdf-unparseable` | extraction/parsing failed for a PDF |
| `pdf-only-line` | a PDF line has no CSV counterpart |
| `pdf-csv-mismatch` | PDF line total ≠ CSV line total (CSV wins; discrepancy flagged) |
| `missing-pdf` | a CSV row's `PDF` filename is not in the drop (currently 0) |

## 9. Out of scope / known limitations

**Out of scope:** OCR for the 2 scanned PDFs · the product→category map (`category` stays null —
it has no PDF source) · product/supplier master entities · confidence-scored matching + review
queue (Phase 3) · re-using the PDFs to *verify* CSV totals (a cross-check was considered but the
user chose extraction-first).

**Known limitations:**

- Only ~20% of PDFs state UOM explicitly; ~25% state WET; ~12% state pack/carton. Most lines will
  simply not get these fields — that's expected, not an error.
- The LLM path is non-deterministic; it needs a small eval fixture set to catch hallucinated
  UOM/pack values.
- Deterministic templates break on supplier layout changes (the hybrid design exists precisely so
  a drift falls through to the LLM).

## 10. Open questions (not blocking)

1. **WET is line-level or invoice-level?** Assumed line-level (`wet_amount` per line); some
   suppliers may only print an invoice-level WET total. Confirm against a real liquor invoice.
2. **`category` source** — if category matters, where does the product→category map come from
   (a curated list? CTB stock GL codes?)? Not in the PDFs.
3. **`pack_size` vs `unit_quantity` semantics** — is "4 CTN / 48 EACH" `pack_size=4, unit_quantity=48`,
   or does `pack_size` mean 48 and `unit_quantity` mean 4? Pin one convention from a real PDF.
4. **How many suppliers get deterministic templates first** (the top-by-volume handful) vs. LLM
   from day one — a phasing detail for the plan.
