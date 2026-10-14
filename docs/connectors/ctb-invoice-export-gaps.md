# CTB invoice export — missing line items (evidence pack)

Status: **evidence only, no remediation performed.** Prepared 2026-10-11 from a
**read-only** production session. Intended as the basis for raising the export gap with
Cooking the Books (CTB).

## What this is

CTB's Custom Invoice Export CSV is the authoritative source of supplier invoice lines. For a
subset of invoices the CSV **omits line items that appear on the supplier's PDF** — in the
worst case the CSV carries only an invoice total and no lines at all. Because purchases/COGS
are computed from `canonical_invoice_line`, those invoices understate cost.

This pack quantifies the gap and names the affected suppliers/invoices. It does **not** claim a
total-dollar figure (that needs PDF line amounts reconciled to the invoice header — see
`docs/superpowers/specs/2026-10-11-ctb-invoice-gap-recovery-design.md`).

## How it was measured

1. Took the **913 open `PDF_ONLY_LINE` flags** in production (`invoice_ingest_flag`).
2. Classified each by running the current line matcher (`InvoiceLineMatcher`) against that
   invoice's current `canonical_invoice_line` rows: **373 MATCH** (false flags — a line *is* in
   the CSV, just keyed differently), **13 AMBIGUOUS**, **527 NO_MATCH**.
3. Split the 527 no-match rows by content:

   | Kind | Count | Examples |
   |---|---:|---|
   | Surcharges / levies | 217 | `Fuel Levy (GST INC)`, `Temporary Fuel Levy Charge`, `Keg Freight`, `CYL DELIVERY` |
   | Annotations | 57 | `(3 BOXES)`, `(pick up saturday 1 box)`, `COMMENT` |
   | Service / non-product | 25 | `Trivia at Goldy's!`, `Canopy Filter Exchanged`, membership |
   | **Product-like (the gap)** | **228** | see below |

   The 228 product-like rows (174 with a PDF stock code, 54 without) are in
   [`ctb-invoice-export-gaps.csv`](ctb-invoice-export-gaps.csv), by invoice and supplier.

## Findings

### Concentrated in a few suppliers

| Supplier | Product-like gap lines |
|---|---:|
| Infiniti Group | 95 |
| Tim & Terry Oyster Supply | 21 |
| Air Liquide | 19 |
| Hop Nation | 13 |
| Long Fine Hospitality Supplies | 11 |
| Summit Security Group Pty Ltd | 8 |
| Oranges & Lemons | 8 |
| Alsco | 7 |
| Good Bar Trade | 6 |
| (others, incl. 3 with no current invoice metadata) | 40 |

(The biggest raw flag counts — Grange Meat Co 111, G & T Chickens 75 — are almost entirely
delivery charges, box annotations and fuel levies, **not** product lines, so they are not in the
228.)

### Two confirmed mechanisms (raw-ledger evidence)

- **CSV is line-less for some invoices.** Air Liquide `NU3379`: the canonical invoice has exactly
  one line, `total | 126.6700`. The PDF lists the actual cylinders (LPG, ALIGAL, cylinder fees).
- **CSV is partial for others.** Infiniti `758262`: the CSV has 8 real lines (knives, cutting
  boards, tongs, grater), but the supplier PDF additionally carries `B12BG Brush Grill Medium
  Prince Castle`, `Luzerne Tintin Round Bowl`, etc.

The supplier PDFs **do** carry per-line amounts and totals (validated from stored PDFs — e.g.
Infiniti `#759352` line `… Brush Grill … $175.00 $175.00 $17.50 $192.50` with `Total Ex GST
$175.00`), so the omission is a function of the CSV export, not of the source PDF.

## Impact

For the affected invoices, `canonical_invoice_line` (and therefore purchases/COGS) is short the
omitted lines. The size of the shortfall is not yet quantified; it requires extracting PDF line
amounts and reconciling them to the invoice header total (design in progress).

## Request to CTB

1. Confirm whether the Custom Invoice Export is expected to include **all** invoice line items.
2. If it can, fix the export for the affected suppliers/invoices (list in the CSV).
3. If some lines are intentionally excluded (e.g. fees, fees-as-lines), tell us the exclusion
   rule so Goldy's can model it rather than guess.

## Caveats

- The classification is a content heuristic layered on the matcher output, not a human-reviewed
  line-by-line audit; the 228 is an upper bound on "product-like" (some may still be fees).
- PDFs are not yet amount-reconciled to invoice headers, so no dollar figure is asserted.
- The 373 "MATCH" flags are false alarms addressed separately by the matcher fix on branch
  `fix/ctb-invoice-line-matching`.

## Scheduled follow-up (accuracy review)

PDF-derived gap lines (`origin='PDF'`) inherit their stock codes from the PDF extractor, which for
non-templated suppliers is the LLM fallback. Those codes can be hallucinated (`FUEL` vs
`FUEL LEVY`, placeholder codes like `12345`) even though line descriptions are usually sound. On a
low-frequency cadence (e.g. quarterly), sample a handful of `origin='PDF'` lines across the top gap
suppliers and verify their codes/descriptions against the stored PDFs; where a supplier keeps
producing bad codes, write a deterministic template extractor (see
`docs/superpowers/specs/2026-10-13-supplier-pdf-templates-design.md`).

