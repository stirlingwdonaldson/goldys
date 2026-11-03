# CTB invoice gap recovery — design

- **Date:** 2026-10-11
- **Status:** design — not implemented. Awaiting review before a plan is written.
- **Related:** `docs/connectors/ctb-invoice-export-gaps.md` (evidence),
  `docs/connectors/matching-and-identity.md` (current matching rules),
  branch `fix/ctb-invoice-line-matching` (the matcher fix this builds on).

## Context

CTB's Custom Invoice Export CSV is the authoritative source of supplier invoice lines:
`CtInvoiceCsvIngestService` writes `canonical_invoice` + `canonical_invoice_line`, and the
invoice PDFs only *enrich* those lines (UOM/pack/WET). The documented invariant is **"PDFs never
create lines on their own."**

A production read-only audit found that for a subset of invoices the CSV **omits product lines
that appear on the supplier's PDF** — sometimes the CSV holds only an invoice total (evidence
pack). Purchases/COGS derive from `canonical_invoice_line`, so those invoices understate cost.

The matcher fix (separate branch) clears the ~373 false flags and leaves the genuine gaps as
`PDF_ONLY_LINE` flags. This design is the fallback for recovering the genuinely-missing cost when
CTB cannot or does not fix the export.

**Validated prerequisite:** stored CTB PDFs carry per-line amounts and an invoice total (e.g.
Infiniti `#759352` `… Brush Grill … $175.00 $175.00 $17.50 $192.50` + `Total Ex GST $175.00`),
so reconciling PDF lines to the invoice header is feasible.

## Goal

Make purchases/COGS **complete** when the CSV export is *provably* incomplete, without double
counting, without weakening the raw-ledger/bitemporal invariants, and without guessing about a
PDF parse.

## Non-goals

- Do **not** replace the CSV as the primary line source.
- Do **not** auto-create lines for surcharges/levies/annotations (freight is already in the
  invoice header; those are not stock).
- Do **not** improve PDF extraction accuracy generally.
- Do **not** contact CTB (that is the evidence pack).

## Invariants and constraints

- Raw ledger is append-only; canonical rows are bitemporal (close-and-insert, never update).
- Reads flow from resolved/canonical projections only.
- Every canonical row has a `raw_record_id` FK. PDF-sourced lines anchor to the **PDF raw
  record** (real provenance) — distinct from enrichment, which deliberately keeps the CSV raw id.
- The **invoice header total is authoritative** for the invoice's ex-tax cost.
- The pipeline must be deterministic, idempotent and replayable.

## Options considered

- **A — Escalate only.** Report the gap; change nothing. Cheapest, but leaves COGS short.
- **B — PDF-derived gap lines, gated by header reconciliation (chosen).** Only create PDF lines
  when the PDF provably reconciles to the header and the CSV does not, so the header stays
  authoritative and double counting is structurally prevented.
- **C — Source affected suppliers from the PDF entirely.** Larger blast radius, loses the CSV as
  the cross-check, and assumes PDF parses are always right.

## Chosen design (B)

### 1. Extract PDF line amounts

Add an ex-tax line amount (and unit price where present) to `PdfExtractedLine`, populated by each
per-layout `*InvoicePdfExtractor`. This is the bulk of the work and is layout-specific. Layouts
whose amounts cannot be parsed reliably are **skipped, not guessed** (they keep their flags).

### 2. Invoice-level reconciliation

After CSV ingest + PDF enrichment, for each invoice:

- `csvSum` = Σ current CSV line `line_total`
- `pdfSum` = Σ PDF line ex-tax amounts
- `header` = `canonical_invoice.amount_ex_tax`

| Condition | Action |
|---|---|
| `abs(csvSum - header) <= tolerance` | CSV complete → create nothing; supersede any existing PDF gap lines |
| `abs(pdfSum - header) <= tolerance` and CSV not complete | CSV provably incomplete → create gap lines (§3) |
| neither reconciles | ambiguous → flag for a human; create nothing |

`tolerance` absorbs rounding/GST rounding (default a small epsilon such as ±$0.02 or 0.5%, exact
in tests). A configurable, documented value — never an invented per-line fudge.

### 3. Gap-line creation

For each PDF line with no CSV match (`InvoiceLineMatcher` → NO_MATCH), and only when §2 says the
PDF reconciles:

- **Identity:** `source_system = CTB`, `source_record_ref = "{invoiceNumber}:pdf:{pdfSha256}:{lineSeq}"`
  — content-stable, so re-processing the same PDF is idempotent.
- **Provenance:** `raw_record_id` = the **PDF** raw record id.
- **Values:** quantity, unit cost, line total from the PDF; `product_name_key` from the PDF
  description; category only if the PDF provides one.
- Never create a line that pushes the invoice over the header total.

### 4. Marking and trust

- New nullable `canonical_invoice_line.origin` column: `'CSV'` (default/null) or `'PDF'`, so
  PDF-sourced lines are explicitly queryable and a projection/audit can tell them apart.
- New append-only flag type `INVOICE_EXPORT_INCOMPLETE` recording the invoice and shortfall, and
  that gap lines were derived. Surfaced through the existing flags API / work queue
  (`resolved_at` lifecycle from the matcher branch).

### 5. Convergence (when CTB later fixes the export)

On CSV (re)ingest, re-run reconciliation. If the CSV now reconciles and PDF gap lines exist,
**supersede (close) those PDF lines** so they stop contributing — no double count. The matcher
re-identifies them by code/description. This makes the whole path self-healing in both directions.

### 6. Projection

The inventory/COGS projector sums current invoice lines as today; with gap lines present the
invoice sums to the header, so no projector change is expected beyond confirming it reads all
current lines.

## Data model changes

- Migration: `canonical_invoice_line.origin text` (nullable; null ⇒ CSV).
- Depends on the matcher branch's `V42` (`invoice_ingest_flag.resolved_at`) for flag retirement.

## Testing

- **Unit:** reconciliation math — complete / incomplete / neither, tolerance edges, GST rounding;
  identity stability; the "never exceed header" guard.
- **Integration (Testcontainers):** partial CSV + complete PDF fixture → gap lines created and
  invoice sum == header; complete CSV → none; CSV later fixed → gap lines superseded, no double
  count; replay idempotent.
- Architecture boundary tests for any new package.

## Rollout

- Behind an off-by-default property (like `app.ctb-invoice-reenrich`); enable per deploy, verify
  on production data read-only, then decide the default.
- The existing replay runner backfills history once amount extraction is in.

## Sequencing / dependencies

1. Matcher fix (branch `fix/ctb-invoice-line-matching`) — prerequisite; clears false flags.
2. PDF amount extraction per layout.
3. Reconciliation + gap-line creation + origin column + flag.
4. Convergence handling.
5. Backfill replay and verification.

## Open questions

- Tolerance policy (exact vs epsilon) and GST-rounding behaviour.
- Are header totals ex-tax, and do per-line ex-tax amounts sum to them across every layout?
  (Validate on a sample before building.)
- Should gap lines count immediately, or after one review cycle?
- Should surcharges/levies (currently 217 flags) also be modelled, or left in the header?
- Category source for PDF-sourced lines.

## Evidence

- `docs/connectors/ctb-invoice-export-gaps.md` and `.csv`.
