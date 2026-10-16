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

- **Source:** `OPENTABLE` (GuestCenter reviews, CSV drop and JSON pull). Populated from reviews,
  which are the only source carrying the clean `Guest ID`.
- **Logical identity:** `UUID.nameUUIDFromBytes("guest:" + guestId)`.
- **Source identity:** `(source_system = OPENTABLE, source_record_ref = Guest ID)`.
- **Identity ruling — no Guest ID in reservations:** the reservations CSV has **no `Guest ID`
  column** — guest identity there is name plus **masked** phone/email (`**********`). No clean
  guest row can be created from reservations: matching on name + masked PII would merge distinct
  people and split name variants. The reviews export carries the clean `Guest ID`, so
  `canonical_guest` is populated from reviews only.
- **Matching strategy:** deterministic single source keyed on `Guest ID`. Reservation → guest
  linkage is the nullable `canonical_reservation.guest_id`, left null.
- **Deferred name-matching:** reservation → guest linkage by name is deferred; it is explicitly
  *not* guessed from names now.
- **Confidence:** n/a (single source).
- **Manual resolution path:** not wired.

## Review (`canonical_review`)

- **Source:** `OPENTABLE` (GuestCenter reviews; both the CSV drop and the assisted-session JSON
  pull canonicalize).
- **Logical identity:** `UUID.nameUUIDFromBytes("review:" + reviewId)`, keyed on the source
  `Review ID` / JSON `id` — `OT-140126-…` (OpenTable) / `RT-140126-…` (Google and Facebook), with
  `source` (`OpenTable` / `Google` / `Facebook`) distinguishing the origin.
- **Source identity:** `(source_system = OPENTABLE, source_record_ref = review id)`.
- **Matching strategy:** deterministic single source; re-record supersedes on a changed fact (the
  close-and-reinsert pattern), unchanged re-record is a no-op. No cross-source matching.
- **Confidence:** n/a (single source).
- **Manual resolution path:** not wired (read-only analytics).

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

- **Source:** CTB Custom Invoice Export CSV. The AJAX invoice feed remains raw-only.
- **Source identity:** supplier source code (fallback `name:<normalised supplier name>` when no
  code exists), followed by `::` and invoice number. A fallback name is visible and unverified;
  no cross-supplier alias or rename is inferred from it. Different known supplier codes remain
  separate even if their invoice numbers match.
- **Logical identity:** UUID derived from the scoped reference for new invoices. A transition
  from a legacy bare-number header retains that header's enduring logical ID when its supplier
  name matches, so already-parented children stay attached. This is a structural transition;
  it does not authorise historical value repair or invent supplier-code evidence.
- **Scope limitation:** different outlet/document contexts within one supplier/number delivery
  are rejected. Cross-delivery outlet/type identity and CTB revision semantics remain unconfirmed.
- **Invoice/PDF and legacy routes:** the evidence link still carries invoice number. Number-only
  reads, enrichment, notes and recovery explicitly reject ambiguous numbers instead of selecting
  the first supplier. CSV/PDF convergence uses the parent invoice logical ID. A future
  supplier-qualified document-viewer route is separate work; no source document is assigned by guess.

## Invoice line (`canonical_invoice_line`)

- CSV references combine the scoped invoice reference with a content signature and an occurrence
  discriminator for identical repeated lines. Reordering and identical replay retain identities;
  the signature is an export occurrence key, not a verified enduring CTB line ID.
- Parentage is `invoice_logical_id`. A legacy ordinal line can be re-keyed only through a
  one-to-one match on its existing CSV values under an unambiguous parent. Structural successors
  preserve logical identity, known valid time and PDF enrichment. Unknown legacy values that
  differ from replayed source values require separately reviewed repair.
- **Default export policy:** absent lines are retained. A known subset or identical/reordered
  delivery is safe; changed/additional occurrences on an existing invoice are withheld explicitly
  because complete-versus-partial and revision semantics have not been confirmed. Header/line
  validation is atomic; a failure leaves raw bytes and a failed processing stage available.
- `app.ctb-invoice-csv.complete-invoice-snapshots=true` permits authoritative replacement only
  after source completeness is confirmed. It defaults false. Tests that exercise replacement
  declare that policy for their fixtures; it is not a production policy.
- PDF-derived facts remain marked `origin=PDF`. Their convergence is scoped to the parent ID;
  no supplier can prune another supplier's lines through a shared invoice number. Older
  number-only recovery/enrichment paths stop on ambiguous identity.
- Missing quantity and unit cost remain null; zero is retained only when observed. No pack/UOM
  conversion or return sign is guessed. Header and line tax/conversion evidence remain separate.


## Stocktake header (`canonical_stocktake`)

- **Source:** `CTB` (`ctb-stocktakes` header list, pulled daily by `CtbConnector`).
- **Logical identity:** `UUID.nameUUIDFromBytes("stocktake:" + stocktakeId)`.
- **Source identity:** `(source_system = CTB, source_record_ref = "stocktake:<id>")`.
- **Matching strategy:** deterministic single source; re-pull supersedes on
  `(source_system, source_record_ref)` (unchanged → no-op).
- **Confidence:** n/a (single source).
- **Manual resolution path:** not wired.
- The header carries the stocktake's `stocktakeDate`; the detail payload does not, so
  `canonical_stock_count.counted_date` is resolved from this entity.

## Stock count (`canonical_stock_count`)

- **Source:** `CTB` (stocktake detail via `ctb-stocktake-detail`, backfilled by the one-off
  `ctb-detail-backfill` run).
- **Logical identity:** `UUID.nameUUIDFromBytes("stock-count:" + stocktakeId + ":" + lineId)`.
- **Source identity:** `(source_system = CTB, source_record_ref = "stocktake:<stocktakeId>:<lineId>")`.
- **Line id ruling:** `stocktakeDetailId` is **not** a line id (only 4 distinct values across all
  lines); the per-line id is the source `id` field, which is unique only within a stocktake. The
  parent `stocktakeId` therefore anchors identity — the same "owner-scoped line id" problem the
  recipe-ingredient slice solved.
- **Date:** `counted_date` is resolved from the canonical stocktake header via `stocktake_id` (the
  detail payload carries no date). A line whose header is absent fails the canonicalization run, never
  silently skipped.
- **Matching strategy:** deterministic single source; re-record supersedes on
  `(source_system, source_record_ref)`.
- **Confidence:** n/a (single source).
- **Manual resolution path:** not wired.

## Wastage (`canonical_wastage`)

- **Source:** `CTB` (wastage detail via `ctb-wastage-detail`, backfilled by the one-off
  `ctb-detail-backfill` run).
- **Logical identity:** `UUID.nameUUIDFromBytes("wastage:" + wastageRecordId + ":" + lineId)`.
- **Source identity:** `(source_system = CTB, source_record_ref = "wastage:<recordId>:<detailId>")`,
  where `lineId` is the source's globally unique `wastageRecordDetailId`.
- **No stock code:** wastage has no `stockCode` in the source — product identity is `product_name_key`
  from `stockDesc` only (a documented limitation).
- **Date:** self-contained (`recordDate`); no header entity is needed.
- **Matching strategy:** deterministic single source; re-record supersedes on
  `(source_system, source_record_ref)`.
- **Confidence:** n/a (single source).
- **Manual resolution path:** not wired.

### Receipt replay and count date lineage (16 October 2026)

Stocktake headers, stock counts and waste keep their existing source/logical identities. Each
writer records processing receipts, including unchanged observations, in
`inventory_observation_receipt` under its own kind/source/reference. A repeated receipt creates no
new fact. Previously unseen receipts older by `(raw.fetched_at, raw.id)` are retained as processed
but cannot replace a newer observation. This is deterministic reception order, not proof of CTB's
business revision or edited-export semantics; no deletion, completeness or source authority policy
is inferred from absence or age.

A COUNT processing receipt also identifies the accepted canonical header version used as its date
context. The count writer locks the current same-source header before the count and derives the
count date there, so an old caller hint cannot undo a header date correction. Reusing the latest
detail under a genuinely changed header date can append one dated count version. Reusing an older
detail cannot restore its quantity under that context. Header metadata changes without count/date
changes produce no count revision. Newly appended count versions retain `header_version_id`;
legacy rows remain unknown rather than receiving a guessed historical header association.

The flag-gated replay retains its latest-header selection but streams raw references/bytes one
payload at a time and commits each payload batch atomically. Missing referenced headers are checked
before canonical writes. Malformed detail references retain the existing visible warning/skip
behaviour. A later payload failure leaves earlier committed payloads intact; receipt guards make a
retry safe. No historical repair or production replay is run by this code change alone.

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

## Source product-sale fact (`source_product_sale_fact`)

- **Source:** `CTB` (`Sale/SearchSaleItemsByDateRange`, pages ingested under the shared
  `ctb-revenue` fetcher identity). A source observation of the product-day measures CTB supplies
  (cost, food cost, discount, tax, unit price, profit) that have no universal canonical home. It is
  written by the live connector and the flag-gated `SourceProductSaleFactBackfillRunner`, and is
  never projected into a resolved view.
- **Logical identity:** `UUID.nameUUIDFromBytes("source-product-sale-fact:" + sourceSystem + ":" +
  sourceRecordRef)`.
- **Source identity:** `(source_system = CTB, source_record_ref =
  "<stockCode>@<tradingDate>[@<uniqueGroupId>]")`. `uniqueGroupId` is the source's grouping
  namespace (documented in `field-preservation.csv` as previously dropped); it is included so
  distinct rows that share a stock code and day remain distinct. This assumes `uniqueGroupId` is
  stable across re-pulls of the same product-day — unverified against production data, so revisit
  the identity if it proves unstable.
- **Trading date:** the request window, recovered from the `sale-items:<day>` raw-record reference
  or the `canonical_product_sales.raw_record_id → trading_date` provenance — never derived from
  `fetched_at` or record ordering.
- **Matching strategy:** deterministic single source; a re-record supersedes on
  `(source_system, source_record_ref)`.
- **Confidence:** n/a (single source). The cost fields are source-estimated (CTB recipe costing),
  distinct from purchasing COGS (`canonical_invoice_line`).

## Unmatched / missing data

Where a second source is absent or has no record for an entity, the resolved layer surfaces
"no data from source X" (the same pattern as the sales slice) rather than silently dropping it.
Nothing here invents a cross-source match where only one source exists.


## CTB invoice safety guard (15 October 2026)

Existing invoice-number identities are retained pending verified stable source keys. CSVs with a
single invoice number spanning different supplier code/label, outlet or document-type contexts
are rejected as ambiguous. An existing canonical invoice cannot silently change supplier label.
A supplier rename may therefore need investigation rather than automatic replacement. This guard
prevents silent merging; it is not a full issuer-aware identity model. Export completeness,
post-export corrections, Return signs and alias history remain unconfirmed. Replay of stored
fetcher deliveries is ordered by receipt time and raw-record ID; source revision precedence is
not inferred from that receipt ordering.


## Foundation repair integration (16 October 2026)

This section supersedes older number-only transition notes below the historical domain entries.
Source-product identities retain source codes. Existing hub mappings, including ACCEPTED, REJECTED
and automatic mappings, survive source refresh/rename; an explicit mapping operation is required
to change them. New name-selected hub associations are labelled NAME rather than SOURCE_CODE.
Daily canonical/resolved product sales remain name-keyed; these tables alone do not close that gap.

Product-sale replay records each `(source, source reference, raw record)` observation once, including
identical intermediate receipts. Later replay cannot manufacture a second correction history or
replace newer received evidence with an older receipt. Receipt ordering remains distinct from
unverified source-effective/revision ordering. Raw observations remain immutable; original values
and unknown business dates are not guessed. Replay processes one stored payload batch at a time.


### Invoice read and document qualification — 16 October 2026

Invoice detail, supplier breakdowns and graph navigation carry stored invoice/supplier logical UUIDs.
Number/name-only compatibility reads require uniqueness; unresolved parent lines remain unattributed.
PDF link evidence gains nullable `invoice_logical_id` in V95. New links capture the unambiguous stored
parent only when identity history has a single logical invoice. Historical links are not bulk-assigned.
A legacy link is usable on read only when the invoice's identity history is unique and its current
PDF filename agrees uniquely with that link. Qualified documents pair raw ID and filename; multiple
documents require explicit selection. No receipt-order document authority or new source matcher is
introduced. Raw PDF access additionally requires the existing `connectors` source-record grant.


## CTB product daily observations (16 October correctness increment)

New daily facts use `(source_system, trading_date, siteNumber, stockCode)`. The source product
reference is length-delimited code plus site (`unknown` when absent); the logical daily UUID also
includes source and date. Display-name normalization remains a compatibility reporting alias,
never the source identity. Source group observations retain quantity, amount, original name/site
and financial fields. Every contributing page is linked in `product_sales_contributor`.

Repeated `(site, code, uniqueGroupId)` observations within a fetched day contribute once. Inconsistent
repeats or missing code/group/quantity/amount reject the batch; raw receipts remain retained.
Qualified daily facts are published atomically after pagination completes. The canonical writer
retains equal-value receipts and rejects already-observed/older receipt replay as new revisions.
It does not infer deletion from absence or claim that a fetched report is all business activity.

Legacy canonical rows remain immutable. On a source/day with qualified facts the name compatibility
read excludes its legacy name aggregates, preventing a second representation after a rename. All old
and new aliases for the day are reprojected. Multiple qualified products from the same source sharing
one name/day produce a conflict with unavailable values, including when a source override exists.
Cross-source equivalence and category history remain name-based compatibility, pending reviewed
hub/variant mappings; no historical repair or automatic name-based promotion is performed.

The group-financial observation ref remains code/date/group for backwards compatibility. Different
known sites colliding on that ref fail visibly instead of superseding one another. A dedicated
site-scoped transition there requires further source evidence and is not silently guessed.
