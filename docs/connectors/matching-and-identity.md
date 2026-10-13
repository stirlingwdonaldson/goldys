# Matching & Identity Strategies

How the platform decides that two source records describe the same real-world entity, and how
identity is assigned when no source shares a common ID. This is the design artifact the
Reconciliation Engine assumes exists before any comparison logic is written (PRD Requirement 7).

All current canonical entity types are **single-source** today: each has one authoritative
source, so there is no cross-source matching to perform. The document still pins, per entity, its
logical identity, source identity, matching strategy, confidence, and the manual-resolution path —
so a second source can be added later without re-deriving these decisions.

## Reservation (`canonical_reservation`)

- **Source:** `OPENTABLE`, from the assisted-session JSON pull (`opentable-api`) or the CSV drop.
- **Logical identity:** `UUID.nameUUIDFromBytes("reservation:" + reservationId)`.
- **Source identity:** `(source_system = OPENTABLE, source_record_ref = OpenTable Reservation ID)`.
  From the JSON pull this is the response's `reservationId` (a 24-character hex string, e.g.
  `6ac9ab1b204b58f759bb1af6`). Re-pulling an unchanged reservation is a no-op; any change to a
  modelled field supersedes the current version.
- **Status (JSON pull):** `stateCategory` maps `booked`→`BOOKED`, `seated`→`SEATED`,
  `finished`→`COMPLETED`, `noShow`→`NO_SHOW`, `cancelled`→`CANCELLED`. A seated or finished
  reservation whose `primarySource` is `walkin` becomes `WALK_IN`, so `ReservationProjector` keeps
  counting walk-ins (it counts them as attended too). Unknown categories pass through upper-cased
  and count as bookings only.
- **Reservation time:** `visitDate` is venue-local wall time with no offset, interpreted in
  `opentable.timezone`.
- **Matching strategy:** deterministic single source; no cross-source matching. Covers are derived
  from OpenTable party size and are **not** reconciled against Lightspeed's `Covers` metric (a
  downstream echo of the same party size).
- **Confidence:** n/a (single source).
- **Manual resolution path:** `ReservationOverrideService.save(...)` sets an override for a
  `(trading_date, service_period)`, replacing the computed covers.

## Guest (`canonical_guest`)

- **Source:** `OPENTABLE` (GuestCenter reviews export). The schema exists (`canonical_guest`) but is
  **not populated this slice** — reviews are ingested raw only until slice 2.
- **Logical identity:** `UUID.nameUUIDFromBytes("guest:" + guestId)`.
- **Source identity:** `(source_system = OPENTABLE, source_record_ref = Guest ID)`.
- **Identity ruling — no Guest ID in reservations:** the reservations CSV has **no `Guest ID`
  column** — guest identity there is name plus **masked** phone/email (`**********`). No clean
  guest row can be created from reservations: matching on name + masked PII would merge distinct
  people and split name variants. The reviews CSV carries the clean `Guest ID`, so `canonical_guest`
  is populated from reviews only.
- **Matching strategy:** deterministic single source keyed on `Guest ID`. Reservation → guest
  linkage is the nullable `canonical_reservation.guest_id`, left null this slice.
- **Deferred name-matching:** reservation → guest linkage by name is deferred until reviews provide
  the `Guest ID` anchor; it is explicitly *not* guessed from names now.
- **Confidence:** n/a (single source, unpopulated).
- **Manual resolution path:** not wired.

## Review (`canonical_review`) — future

- **Source:** `OPENTABLE` (GuestCenter reviews export; ingested raw only until slice 2).
- **Logical identity:** keyed on `Review ID` — `OT-140126-…` (OpenTable) / `RT-140126-…` (Google),
  with `Source` distinguishing the origin. Identity/strategy finalized when slice 2 lands.

## Menu item (`canonical_menu_item`) — future

- **Source:** `OPENTABLE` (GuestCenter menu export; ingested raw only until slice 3).
- **Logical identity:** keyed on `Invariant ID` (stable across edits; `Item ID` changes per edit,
  so invariant is the logical key). Modifier-group children are a later design. Identity/strategy
  finalized when slice 3 lands.

## Labour entry (`canonical_labour_entry`)

- **Source:** `DEPUTY` (raw webhook; canonicalization deferred until the payload schema is
  confirmed).
- **Logical identity:** `UUID.nameUUIDFromBytes("labour:" + sourceRecordRef)`.
- **Source identity:** `(source_system = DEPUTY, source_record_ref = Deputy shift/timesheet id)`.
  Provisional until the first payload is observed.
- **Matching strategy:** deterministic single source.
- **Confidence:** n/a.
- **Manual resolution path:** `LabourOverrideService.save(...)` sets an override for a
  `(trading_date, department)`, replacing the computed actual hours.

## Invoice metadata (`canonical_invoice`)

- **Source:** `CTB` — two ingestion paths for the same records:
  - **Custom Invoice Export CSV** upload (`/api/ingest/ctb-invoices`, `fetch_method=FILE_EXPORT`,
    `fetcher_identity=ctb-invoices`), and
  - **`Invoice/SearchInvoices`** AJAX pull (`fetch_method=API`,
    `fetcher_identity=ctb-invoices-ajax`).
- **Logical identity:** `UUID.nameUUIDFromBytes("invoice:" + invoiceNumber)`.
- **Source identity:** `(source_system = CTB, source_record_ref = invoice number)`.
- **Matching strategy:** both paths key on `invoice_number` (exact). The CSV path canonicalizes
  today; the AJAX path is **raw-ledger only** and is deliberately *not* canonicalized yet, so the
  two paths cannot silently double-count the same invoice. Before the AJAX path feeds
  `canonical_invoice`, the two must be de-duplicated on `invoice_number` (a second path for the same
  number supersedes rather than appends, per the bitemporal close-and-reinsert pattern).
- **Confidence:** high for the `invoice_number` join (exact).
- **Manual resolution path:** not yet wired; COGS comes from line items, so invoice-header totals
  are metadata only.

## Invoice line (`canonical_invoice_line`)

- **Source:** `CTB` Custom Invoice Export CSV (authoritative), enriched from the matching invoice
  PDF delivered over SFTP.
- **Logical identity:** `UUID.nameUUIDFromBytes("invoice-line:" + invoiceNumber + ":" +
  sourceRecordRef)`.
- **Source identity:** two `source_record_ref` shapes, both keyed `(source_system = CTB)`:
  - CSV-sourced line: `invoice number + ":" + line seq`.
  - PDF-derived gap line (see below): `invoice number + ":pdf:" + sha256(pdf) + ":" + line seq`.
- **Origin:** `canonical_invoice_line.origin` is `"CSV"` for export-sourced lines and `"PDF"` for
  PDF-derived gap lines; `null` is a legacy CSV row (ingested before the column existed) and is
  treated as CSV for matching and convergence.
- **Matching strategy:** invoice metadata ↔ lines join on `invoice_number` (exact). PDFs never
  create lines on their own except through gap recovery: `InvoicePdfEnrichmentService` resolves a
  PDF to its invoice through the CSV's persisted `pdf_filename` (falling back to the invoice number
  in the PDF text only when the filename does not resolve), then maps each parsed PDF line to one of
  that invoice's current CSV lines via `InvoiceLineMatcher` and enriches it (stock code, unit of
  measure, unit quantity, pack size, WET amount). Lines carry a denormalized `invoice_date` so COGS
  can be bucketed by date from lines alone.
- **Gap recovery (PDF-derived lines):** when the header ex-tax total reconciles with the PDF line
  sum but not the CSV line sum (a short export), `InvoiceGapRecoveryService` records the CSV's
  missing PDF lines as canonical `origin='PDF'` lines so COGS is complete. The gap-line identity is
  `source_record_ref = "{invoiceNumber}:pdf:{pdfSha256}:{lineSeq}"`, anchored to the PDF raw record
  (`raw_record_id`). A re-run over the same PDF is a no-op: the re-computed line sum now includes
  the gap line already recorded, so the invoice reconciles as CSV-complete and nothing new is
  created. The shared reconciliation tolerance is `0.02` (ex-tax,
  2 cents): a line sum within that of the header counts as reconciling. Gap lines are created only
  when the PDF reconciles and the CSV does not, and the created sum does not exceed the header
  shortfall (`header − csvSum`) beyond the tolerance; otherwise `INVOICE_EXPORT_INCOMPLETE` is
  flagged and nothing is created.
- **Convergence:** once the CTB CSV later includes the lines it omitted, the CSV's now-complete
  lines supersede the PDF-derived gap lines (bitemporally closed, never deleted), so only the CSV
  lines remain current.
- **PDF↔line key (tiered, scoped to one invoice):** the CTB export and its PDFs key the same line
  differently, so the matcher tries progressively looser keys — (1) normalized stock code, where
  CTB's synthetic `PDF_<hex>` placeholder counts as "no code"; (2) a unique code variant (one
  normalized code is a prefix of the other, e.g. `HAMB008` vs `HAMB008A1c`); (3) exact normalized
  description; (4) whole-token containment, accepted for a single shared token only when the extra
  tokens are packaging noise (`... SET PRICE 4 KG PRICE`, `... ORDER BY BOX`); (5) a majority of
  shared significant tokens. **Ambiguity is skipped, not guessed:** if more than one line wins a
  tier, the line is left un-enriched and `PDF_ONLY_LINE` is recorded.
- **Before matching:** `HybridInvoicePdfExtractor` sanitizes the extractor output
  (`InvoicePdfSanitizer`). A parsed invoice number with no digit (a header label such as
  `Tax Invoice No`) becomes null, so the filename join decides. Annotation rows (no stock code, no
  amount, and a parenthesised or `COMMENT`/`NOTE` description, or no quantity and no unit) are
  dropped: they are delivery notes, not purchases. If the PDF still resolves to no invoice the
  export knows (no current header and no current lines), its lines are not matched: a live drop
  raises one `AMBIGUOUS_PDF` flag for the PDF, and a replay (no filename) raises nothing.
- **Confidence:** high for the invoice-number and filename joins (exact); line matching is
  tolerant-but-conservative (tiers above, unique-winner only). PDF text extraction is
  per-supplier-layout (`*InvoicePdfExtractor`, with an LLM fallback); unmatched or inconsistent
  PDFs raise `InvoiceIngestFlag` rows instead of being dropped.
- **Anomaly lifecycle:** `invoice_ingest_flag` is append-only. A flag carries a nullable
  `resolved_at`; the read API returns **open** flags by default (`?status=all` includes resolved).
  `PDF_ONLY_LINE` marks a parsed PDF line the matcher could not unambiguously assign; when the
  matcher improves, a one-off replay (`app.ctb-invoice-reenrich=true`, then unset —
  `CtbInvoicePdfReenrichRunner`) re-enriches the stored raw PDFs and sets `resolved_at` on the
  `PDF_ONLY_LINE` flags that now resolve, so the work queue reflects reality without deleting the
  historical anomaly record. The same pass also retires `PDF_ONLY_LINE` flags that are annotation
  text or that name an invoice number the export does not know; neither could ever match. `INVOICE_EXPORT_INCOMPLETE` marks an invoice whose CSV line sum does
  not reconcile with the header and whose PDF could not close the gap (missing header, unresolved
  PDF sum, PDF-only lines that would over-state the shortfall, or no resolvable invoice date).
- **Manual resolution path:** `InventoryOverrideService.save(...)` sets an override for a
  `trading_date`, replacing the computed purchases (COGS).

## Stock count (`canonical_stock_count`)

- **Source:** `CTB` (stocktake detail — **ingested raw** via `ctb-stocktake-detail`, under the
  one-off `ctb-detail-backfill` run).
- **Logical identity / matching:** to be finalized when the canonical source is confirmed. Model is
  in place; identity is deferred with the source.

## Wastage (`canonical_wastage`)

- **Source:** `CTB` (wastage detail — **ingested raw** via `ctb-wastage-detail`, under the one-off
  `ctb-detail-backfill` run).
- **Logical identity / matching:** deferred with the source, as above.

## Recipe composition

- **Source:** `CTB` (recipe cost `ctb-recipe-cost` + sale↔recipe links `ctb-sale-recipe-links`,
  canonicalized by the recipe-composition slice D; plus recipe ingredients `ctb-recipe-ingredients`,
  canonicalized by the recipe-ingredients slice). Recipe-level cost and ingredient lines are
  single-source (CTB) — no cross-source reconciliation.
- See "Recipe ingredient line" below for the line identity.

## Recipe ingredient line (`canonical_recipe_ingredient`)

- **Source:** `CTB` (`ctb-recipe-ingredients`, ingested under the one-off `ctb-detail-backfill` run
  and replayed into canonical by the flag-gated `RecipeIngredientCanonicalizationRunner`).
- **Logical identity:** `UUID.nameUUIDFromBytes("recipe-ingredient:" + parentRecipeId + ":" +
  recipeDetailId + ":" + stockId)`. `recipeDetailId` is CTB's OWNER-scoped line id (not globally
  unique), so the parent recipe must anchor the identity; `stockId` disambiguates lines that share a
  detail id and is `null` (stringified `"null"`) when CTB sent no stock reference (a sentinel id or
  a broken link).
- **Source identity:** `(source_system = CTB, source_record_ref =
  "recipe-ingredient:{parent}:{detail}:{stock}")`, with a partial unique index
  `WHERE superseded_at IS NULL`.
- **Duplicate-line quirk:** the source repeats some lines verbatim (recipe 617 "Pickle Brine" emits
  the same `recipeDetailId` *and* `stockId` four times). The parser collapses exact-duplicate lines,
  and the current-fact uniqueness lives on `(source_system, source_record_ref)` — there is
  deliberately **no** unique index on `(parent_recipe_id, recipe_detail_id)` alone.
- **Parent vs line recipe:** `parent_recipe_id` is the recipe the payload was fetched for (from the
  raw record's `source_record_ref = "recipe:{id}"`); `line_recipe_id`/`line_recipe_name` are the
  recipe the line itself belongs to (the parent for main lines, a sub-recipe for `is_sub_recipe =
  true` lines).
- **Matching strategy:** deterministic single source; no cross-source matching. Sub-recipe lines
  carry the sub-recipe's own quantities — there is no parent usage quantity, so sub-recipe cost
  roll-up is out of scope for this slice.
- **Confidence:** n/a (single source).
- **Manual resolution path:** not wired (read-only master data).

## Payment (`canonical_payment`)

- **Source:** `LIGHTSPEED` (Insights "all-payments" scheduled-report webhook, `fetch_method=FILE_EXPORT`, `fetcher_identity=lightspeed-payments`).
- **Logical identity:** `UUID.nameUUIDFromBytes("payment:" + sourceRecordRef)`.
- **Source identity:** `(source_system = LIGHTSPEED, source_record_ref = sale_number + "|" + payment_type_code + "|" + register_code + "|" + trading_date)`. Composite because Lightspeed exposes no payment id and a single sale can be split across several tenders (same `sale_number`, different `payment_type_code`/register). Provisional — prefer a payment id if Lightspeed ever exposes one.
- **Matching strategy:** deterministic single source; no cross-source matching.
- **Confidence:** n/a (single source).
- **Manual resolution path:** not yet wired.

## Deleted sale (`canonical_deleted_sale`)

- **Source:** `LIGHTSPEED` (Insights "all-deleted-orders" scheduled-report webhook, `fetch_method=FILE_EXPORT`, `fetcher_identity=lightspeed-deleted-sales`).
- **Logical identity:** `UUID.nameUUIDFromBytes("deleted-sale:" + saleNumber)`.
- **Source identity:** `(source_system = LIGHTSPEED, source_record_ref = sale number)` — the sale number is unique in the deleted-orders report.
- **Matching strategy:** deterministic single source; no cross-source matching.
- **Confidence:** n/a (single source).
- **Manual resolution path:** not yet wired.

## Sale item (`canonical_sale_item`)

- **Source:** `LIGHTSPEED` (Insights "sales-details" scheduled-report webhook, `fetch_method=FILE_EXPORT`, `fetcher_identity=lightspeed-sale-items`).
- **Logical identity:** `UUID.nameUUIDFromBytes("sale-item:" + receiptLineId)`.
- **Source identity:** `(source_system = LIGHTSPEED, source_record_ref = receipt line id)` — `salelines.id` is unique.
- **Matching strategy:** deterministic single source; no cross-source matching.
- **Confidence:** n/a (single source).
- **Manual resolution path:** not yet wired.
- **Important:** the report must be scheduled **without** the `Product Salelines → Product ID` field; that join silently drops ~92% of rows (see the 2026-10-10 design spec).

## Unmatched / missing data

Where a second source is absent or has no record for an entity, the resolved layer surfaces
"no data from source X" (the same pattern as the sales slice) rather than silently dropping it.
Nothing here invents a cross-source match where only one source exists.
