# Matching & Identity Strategies

How the platform decides that two source records describe the same real-world entity, and how
identity is assigned when no source shares a common ID. This is the design artifact the
Reconciliation Engine assumes exists before any comparison logic is written (PRD Requirement 7).

All six current canonical entity types are **single-source** today: each has one authoritative
source, so there is no cross-source matching to perform. The document still pins, per entity, its
logical identity, source identity, matching strategy, confidence, and the manual-resolution path —
so a second source can be added later without re-deriving these decisions.

## Reservation (`canonical_reservation`)

- **Source:** `OPENTABLE` (GuestCenter CSV drop).
- **Logical identity:** `UUID.nameUUIDFromBytes("reservation:" + reservationId)`.
- **Source identity:** `(source_system = OPENTABLE, source_record_ref = OpenTable Reservation ID)`.
- **Matching strategy:** deterministic single source; no cross-source matching. Covers are derived
  from OpenTable party size and are **not** reconciled against Lightspeed's `Covers` metric (a
  downstream echo of the same party size).
- **Confidence:** n/a (single source).
- **Manual resolution path:** `ReservationOverrideService.save(...)` sets an override for a
  `(trading_date, service_period)`, replacing the computed covers.

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

- **Source:** `CTB` (custom invoice-export CSV).
- **Logical identity:** `UUID.nameUUIDFromBytes("invoice:" + invoiceNumber)`.
- **Source identity:** `(source_system = CTB, source_record_ref = invoice number)`.
- **Matching strategy:** deterministic single source.
- **Confidence:** n/a.
- **Manual resolution path:** not yet wired; COGS comes from line items, so invoice-header totals
  are metadata only.

## Invoice line (`canonical_invoice_line`)

- **Source:** `CTB` (bulk-exported invoice PDFs, text-extracted).
- **Logical identity:** `UUID.nameUUIDFromBytes("invoice-line:" + invoiceNumber + ":" +
  sourceRecordRef)`.
- **Source identity:** `(source_system = CTB, source_record_ref = invoice number + ":" + line seq)`.
- **Matching strategy:** the one genuine intra-source match — invoice metadata (CSV) ↔ line items
  (PDF) are joined on `invoice_number` (exact match). The line also carries a denormalized
  `invoice_date` so COGS can be bucketed by date from lines alone; a line ingested before its
  invoice metadata still contributes to the correct day's COGS.
- **Confidence:** high for the invoice-number join (exact); line-item text parsing is provisional
  pending a real invoice PDF.
- **Manual resolution path:** `InventoryOverrideService.save(...)` sets an override for a
  `trading_date`, replacing the computed purchases (COGS).

## Stock count (`canonical_stock_count`)

- **Source:** `CTB` (stocktake export — **not yet ingested**).
- **Logical identity / matching:** to be finalized when the ingestion source is confirmed. Model is
  in place; identity is deferred with the source.

## Wastage (`canonical_wastage`)

- **Source:** `CTB` (wastage export — **not yet ingested**).
- **Logical identity / matching:** deferred with the source, as above.

## Unmatched / missing data

Where a second source is absent or has no record for an entity, the resolved layer surfaces
"no data from source X" (the same pattern as the sales slice) rather than silently dropping it.
Nothing here invents a cross-source match where only one source exists.
