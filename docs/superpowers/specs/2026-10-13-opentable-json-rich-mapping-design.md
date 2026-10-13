# OpenTable reservation JSON → rich canonical mapping — design

- **Date:** 2026-10-13
- **Status:** implemented (2026-10-13).
- **Related:** `docs/superpowers/specs/2026-10-13-opentable-reviews-menu-reservations-design.md` (the CSV
  slice that added the 91 columns), `docs/superpowers/specs/2026-10-13-opentable-assisted-session-design.md`
  (the JSON path), `docs/connectors/opentable.md`.

## Context

Two ingestion paths write `canonical_reservation` under the same identity (`OPENTABLE` /
reservation id):

- The **CSV** path (`OpenTableReservationsCsvParser`) maps ~91 columns.
- The **JSON** path (`OpenTableReservationJsonParser`, the assisted-session sync) maps only ~6
  fields.

Because `sameFact` compares every field and a newer record supersedes the current one, a JSON
sync (6 fields) after a CSV backfill (91 fields) would bitemporally overwrite the rich row with a
sparse one — the "sparse supersession" problem.

A live capture of the `reservations/gold` detail response (2026-10-13, restaurant 140126) shows
the JSON **does carry essentially all 91 fields**, including the per-guest lifetime rollups (under
a `diner…` prefix). The sparseness is a *parser* gap, not a data gap.

## Decision

**Extend `OpenTableReservationJsonParser` to map the full ~91-column field set**, so both paths
write complete records and supersession is safe. This is a field-name mapping plus a
money-in-cents coercion, not a structural change. The nested arrays (`experiencePrices`,
`additionalPayments`, `priceBreakdown…`) stay in the raw ledger in both paths, exactly as the CSV
parser does.

## Design

### 1. Mapping rule

Default: the JSON field name is the canonical column name in **camelCase** (e.g. column
`dining_area_assigned` ← JSON `diningAreaAssigned`, `reservation_status` ← `reservationStatus`).
The following columns deviate from that default:

| Canonical column | JSON field | Note |
|---|---|---|
| `wait_staff` | `server` | |
| `pos_tip` | `posGratuity` | gratuity = tip |
| `total_tips` | `totalGratuity` | |
| `prepaid_experience_tip` | `experienceGratuity` | |
| `total_vat` | `totalTax` | tax = VAT |
| `prepaid_experience_vat` | `experienceTax` | |
| `experience_price` | `experiencePricePerCover` | |
| `experience_total_sales` | `experienceTotalRevenue` | sales → revenue |
| `experience_total_sales_with_tip` | `experienceTotalRevenueWithTip` | |
| `total_income` | `totalIncomeWithoutTip` | |
| `first_visit_date` | `dinerFirstVisitDate` | guest lifetime rollups use the `diner…` prefix |
| `recent_visit_date` | `dinerRecentVisitDate` | |
| `upcoming_visits` | `dinerUpcomingVisits` | |
| `completed_visits` | `dinerCompletedVisits` | |
| `cancelled_count` | `dinerCancelledCount` | |
| `no_show_count` | `dinerNoShowCount` | |
| `lifetime_spend` | `dinerLifetimeSpend` | |
| `lifetime_per_cover_spend` | `dinerLifetimePerCoverSpend` | |

`guest_id` is absent from the JSON (as it is from the CSV) and stays null — populated only when
the reviews slice provides a `Guest ID`.

### 2. Money is in cents

Every money field in the JSON is an integer number of cents; `currencyMultiplier` (100 for AUD)
is the divisor. Divide each money field by `currencyMultiplier` (do **not** hardcode 100) to get
dollars. Money fields are the columns typed `BigDecimal` in `canonical_reservation` — same set the
CSV parser coerces.

### 3. Currency

`currency` arrives lowercase (`"aud"`) — normalise to uppercase (`"AUD"`).

### 4. Dates / timestamps

`visitDate`, `createdDate`, `seatedDate`, … are venue-local wall time with no offset
(`2026-09-01T17:00:00`) — parse in the venue zone, as the current `reservationAt(...)` already
does for `visitDate`. Guest rollup dates (`dinerFirstVisitDate`, `dinerRecentVisitDate`,
`birthday`, `anniversary`) are date-only; parse to `LocalDate`.

### 5. Status

`stateCategory` (JSON) maps to the existing normalised `status`; `reservationStatus` (JSON) maps
verbatim to the `reservation_status` column. Same as today.

## Out of scope

- The JSON exposes a handful of fields the CSV's 91 columns did not model (fees:
  `vendorProcessingFee`, `otServiceFee`, `otBoostFee`, and totals `totalSalesWithTip`,
  `experienceTotalIncome`, `experienceTotalRevenueWithoutTip`, `prepaymentRefundedTotal`,
  `posGratuity`). These stay in the raw ledger; no new columns this slice.
- No canonical change: the columns already exist (`V54__reservation_detail.sql`).
- No change to `sameFact` or supersession — making both paths rich resolves the sparse
  supersession without touching the bitemporal mechanics.

## Privacy note

The JSON returns `email` and `phone` **unmasked** (`fin.r1996@gmail.com`), whereas the CSV masked
them. Populating `guest_email` / `guest_phone` from the JSON therefore stores real contact data —
confirm this is acceptable before implementing, or mask it in the parser.

## Testing

- Extend `OpenTableReservationJsonParserTest` with a real-shape reservation fixture (scrubbed PII)
  and assert each of the 91 fields maps correctly, including: the `diner…` rollups, a cents→dollars
  conversion (e.g. `totalGratuity: 2500` → `25.00`), currency uppercasing, and the `server` /
  `gratuity` / `tax` renames.
- A supersession test: record a full JSON-derived `ReservationInput` over a full CSV-derived one
  and assert the rich columns survive (no sparse blanking).

## Open questions

- **Resolved:** `email`/`phone` are left null (masked) by default, matching the CSV path's
  masking; revisit if guest contact data is wanted.
- Exact mapping for `campaign_type` (JSON `otBoostType` is the best guess) and
  `prepaid_experience_total_paid` (no distinct JSON field — left null) — both usually null;
  confirm against a prepaid-experience example before relying on them.
