# Invoice identity: supplier-scoped headers and reorder-stable lines (design)

Status: proposal — awaiting approval before implementation.
Findings addressed: F03 (invoice number not globally unique), F05 (ordinal line identities leave stale/duplicated candidates).

## Problem

Two identity choices in the invoice canonical layer are wrong for the real CTB data:

1. **Headers** (`canonical/CanonicalInvoiceService.java`) key on `invoiceNumber` alone — both the
   logical id `UUID.nameUUIDFromBytes("invoice:" + invoiceNumber)` and the source lookup
   `lockCurrentSourceFact(sourceSystem, invoiceNumber)`. Production has **2 invoice numbers shared
   across suppliers**, so one supplier's invoice can supersede another's: the same
   `source_record_ref` collides, gap recovery and convergence then reconcile against the wrong
   header, and spend is misattributed.
2. **Lines** (`connectors/ctb/CtInvoiceCsvIngestService.java:109`) key on `invoiceNumber + ":" +
   seq`, i.e. **position in the CSV**. A reordered re-export supersedes lines with *other* lines'
   data (the ordinal ref is stable but the row behind it moved), and a re-export with *fewer* lines
   leaves the old tail lines current forever — inflating purchases. Production shows **18 repeat
   groups and 79 excess candidate lines**.

## Facts to design around

- The CSV (`CtInvoiceCsvParser`) columns are `Invoice, Supplier, Date, StockCode,
  StockDescription, LineQuantity, LineUnitCostExTax, LineTotalExTax, PONumber, InvoiceDueDate,
  InvoiceTotalExTax, GST, InvoiceFreight, Total, PDF`. There is **no stable source line id**.
- The CTB AJAX invoice pull (`ctb-invoices-ajax`) carries **stable source invoice ids**; it is
  raw-only today.
- Short or partial CSV exports are a **known, real phenomenon** — gap recovery exists precisely
  because of them. So "a re-export replaces the invoice's whole line set" **cannot** be assumed
  blindly.
- PDF gap lines (`origin='PDF'`) and convergence logic (`InvoiceGapRecoveryService`,
  `CtInvoiceCsvIngestService.convergePdfLinesAfterCsvIngest`,
  `CanonicalInvoiceLineService.prunePdfOriginLines`) all join on `invoiceNumber`.

## Design

### 1. Supplier-scoped header identity

- **Logical id:** `UUID.nameUUIDFromBytes("invoice:" + normalizedSupplier + ":" + invoiceNumber)`.
- **Source identity:** keep `source_system = CTB`; make `source_record_ref =
  normalizedSupplier + ":" + invoiceNumber` (or keep the invoice number as the ref and add a
  separate supplier discriminator column — decide at implementation, prefer the explicit
  `normalizedSupplier + ":" + invoiceNumber` ref so the lock is unambiguous).
- **Supplier normalisation** must be conservative (never merge two genuinely distinct suppliers),
  applied identically to the CSV `Supplier` column and the AJAX `supplier`:
  - trim, collapse internal whitespace, uppercase;
  - strip trailing legal-suffix noise (`PTY LTD`, `PTY. LTD.`, `AUST`, `AUS`, `LTD`, `LIMITED`)
    case-insensitively;
  - do **not** fuzz-match beyond that (no Soundex/levenshtein) — a supplier that changes its legal
    name is a new header, flagged rather than silently merged.
- The two observed cross-supplier collisions are resolved by the supplier scope; an invoice number
  that is still ambiguous within one supplier is flagged (see `AMBIGUOUS_PDF`-style) rather than
  merged.

### 2. Line identity that survives reordering

- **Replace** `invoiceNumber + ":" + seq` with a **content-stable** source ref, scoped to the
  invoice header: `source_record_ref = headerRef + ":" + sha256(normalized content) + ":" +
  occurrenceIndex`, where:
  - `headerRef` is the supplier-scoped header ref from §1;
  - `normalized content` is the trimmed, case-normalised `StockCode`, `StockDescription`,
    `LineQuantity`, `LineUnitCostExTax`, `LineTotalExTax` (a line's identifying business content);
  - `occurrenceIndex` is the 1-based ordinal of this line among *identical* lines in the same
    export (so N identical lines are N distinct refs), assigned in a **deterministic** order
    (document order) so a pure reorder of identical lines is stable.
- A reordered export re-keys the same physical lines to the same refs (content, not position, is the
  identity), so reordering supersedes like-for-like instead of cross-wiring.

### 3. Lines that disappear from a re-export

The hard case: a re-export with fewer lines is either a genuine correction (lines removed) or a
short export (the known real phenomenon). Do **not** blindly supersede disappearing lines:

- **Full replacement is only safe when the re-export is proven complete.** Completeness evidence:
  the CSV line total reconciles with the header total within the existing `0.02` ex-tax tolerance
  (the same signal gap recovery already uses).
- **When the re-export reconciles with the header** → treat it as authoritative: supersede current
  lines not present in the new export (bitemporal close-and-reinsert).
- **When it does not reconcile** (short export) → keep the new lines, and treat the *absent*
  current lines as suspect rather than superseding them:
  - retain them (so COGS is not silently dropped), and
  - flag the invoice `INVOICE_EXPORT_INCOMPLETE` with a detail naming the absent lines and the
    shortfall, reusing the existing flag type rather than adding a new one unless the detail is
    insufficient.
- This mirrors the existing PDF-gap convergence: never delete, never guess; keep what is known and
  make the gap a queryable flag.

### 4. Re-keying existing rows — supersession only

- Existing rows with ordinal refs (`invoiceNumber + ":" + seq`) are closed, **never updated or
  deleted**, per the bitemporal invariant.
- One-off re-key (a migration/backfill runner, not a Flyway DML migration): for each current
  header, compute the new supplier-scoped logical id and source ref; for each current line, compute
  the new content-stable ref. Supersede the old row and insert the new row carrying the same fact
  (fields, `raw_record_id`, dates). A re-key is idempotent: a re-run recognises the already-re-keyed
  ref and creates no new version (same pattern as the PDF replay).
- Because canonical rows are closed-and-reinserted, the re-key is replayable and reversible by
  reverting code (no data migration rollback needed).

### 5. Projection recompute

- Line identity changes mean the inventory COGS projection must be rebuilt after re-keying so the
  resolved layer reflects the new line set (the same `recomputeAll`/startup-rebuild path the
  inventory domain already uses).
- The re-key runner publishes the same per-date projection events the line service already emits,
  or triggers a full inventory recompute, so no resolved figure is left stale.

### 6. Documentation

Update `docs/connectors/matching-and-identity.md`:
- the `canonical_invoice` section: supplier-scoped logical id and source ref, supplier
  normalisation rules;
- the `canonical_invoice_line` section: content-stable ref, occurrence disambiguation, and the
  disappear-vs-short-export rule;
- note the AJAX stable invoice id as the preferred future source id once the AJAX path is
  canonicalised.

### 7. Test plan

- **Cross-supplier collision (F03):** two suppliers, same `invoiceNumber`, both ingested → two
  distinct headers, no supersession across suppliers; spend attributed to each supplier.
- **Supplier normalisation:** `"ACME PTY LTD"`, `"ACME PTY. LTD."`, `"acme pty ltd"` normalise to
  one ref; `"ACME FOODS"` and `"ACME BEV"` remain distinct.
- **Reordered re-export (F05):** ingest N lines, re-ingest the same N lines in a different order →
  same N current lines, no cross-wired supersession, line totals unchanged.
- **Short export (gap):** ingest N lines, re-ingest a subset that does **not** reconcile with the
  header → the absent lines stay current, `INVOICE_EXPORT_INCOMPLETE` flagged, COGS not inflated.
- **Complete corrected export:** ingest N lines, re-ingest a *correct* full set that reconciles →
  absent old lines superseded, new set is authoritative.
- **Duplicate identical lines:** N identical lines survive as N distinct refs (occurrence index),
  reorder of identical lines is stable.
- **Re-key idempotence:** run the re-key twice → second run creates no new versions.
- **Projection:** after re-key, resolved COGS equals the sum of the current (re-keyed) lines.
- **Regression:** PDF gap recovery and convergence still reconcile correctly after the identity
  change (they key on invoice number today and must be updated to the supplier-scoped header).

## Open questions / risks

- Supplier-normalisation boundaries: where to stop stripping legal suffixes without merging two
  real suppliers — needs the production supplier list to validate (the audit has it).
- Whether the AJAX path should be canonicalised first (it carries the stable source invoice id) —
  doing so would give headers a genuine source id and shrink the scope of content-derived line
  identity. This spec keeps the AJAX path raw-only and makes the CSV content-stable, which works
  either way.
- The exact "completeness" test for authoritative replacement (header reconciliation vs PDF
  reconciliation) should be confirmed against the 2-supplier and 18-repeat-group production cases.
