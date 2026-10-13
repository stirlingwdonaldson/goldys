# OpenTable reviews, menu items & rich reservations — design

- **Date:** 2026-10-13
- **Status:** design — awaiting plan.
- **Related:** `docs/connectors/opentable.md`, `docs/connectors/matching-and-identity.md`
  (reservation identity), `docs/connectors/source-access.md`, and the earlier reservation
  domain spec `docs/superpowers/specs/2026-10-06-next-domains-reservations-labour-inventory-design.md`.

## Context

OpenTable has, until now, been ingested as a thin 8-column GuestCenter reservation CSV
(`Reservation ID, Date, Time, Party Size, Status, Table, Source, Guest Name`) posted to
`POST /api/ingest/opentable`, canonicalized into `canonical_reservation`. The
`docs/connectors/opentable.md` note that the CSV schema was "provisional — first upload reveals it"
has now been resolved: a one-time manual GuestCenter export produced the real, full exports for
restaurant **140126** (Goldy's), across three datasets the platform does not yet model:

| File | Rows | What it is |
|---|---|---|
| `opentable-reservations-140126-complete.csv` | 27,108 | the full GuestCenter reservation export — **156 columns**, a superset of the thin CSV |
| `opentable-reviews-140126-complete.csv` | 1,578 | OpenTable **and** Google reviews (a `Source` column distinguishes them), with sub-ratings, comments, tags/keywords and reply/response tracking |
| `opentable-menu-items-140126-complete.csv` | 2,003 | the OpenTable digital menu — items, prices, modifier groups (sizes/variations), categories, dietary, status |

The reservation export's 156 columns break into four facets the business wants, plus two
structural facts that shape the design:

- **Guest identity & lifetime value** — name/contact, first/recent visit, visit counts,
  no-show/cancel counts, lifetime and per-cover spend.
- **Operational/floor detail** — dining area (assigned/booked + ids), table type, wait staff,
  wait/turn times, reservation type, full visit lifecycle timestamps (created/seated/finished/
  cancelled/no-show).
- **Booking source & marketing attribution** — online channel, partner, campaign, referrer,
  RestRef (referral) fields.
- **Revenue & payment breakdown** — the prepaid-experience block and a POS breakdown (subtotal,
  tax, service charges, tip, paid, due) plus income/revenue totals.

Two structural facts:

1. **The reservations CSV has no `Guest ID` column.** Guest identity there is name plus
   **masked** phone/email (`**********`). The reviews CSV **does** carry a clean `Guest ID`
   (e.g. `6a8e7ffc…`).
2. The export is **current-state snapshots** with per-guest lifetime rollups repeated on every
   row for the same guest, and the file carries a UTF-8 BOM.

## Goal

1. **Ingest all three datasets into the raw ledger**, byte-faithfully, as a one-time backfill
   that is **recurrence-ready** — the same report shape can be re-uploaded later and will
   supersede rather than duplicate (reusing the existing bitemporal close-and-reinsert).
2. **Model the reservations + guest slice** (slice 1): extend `canonical_reservation` with the
   four facets as typed columns, and introduce a `canonical_guest` entity.

Reviews (slice 2) and menu items (slice 3) canonicalization are recorded as future work, not
designed here.

## Non-goals

- **No reviews canonicalization** (`canonical_review`) and **no menu canonicalization** in this
  slice — raw-ledger ingest only for those two. Slices 2–3.
- **No cross-source reconciliation** for reservations: they remain single-source (`OPENTABLE`).
  The POS-spend block is a *future* reconciliation candidate vs Lightspeed (recorded below, not
  built).
- **No guest name-matching** (reservation → guest linkage). The reservation's `guest_id` stays
  null until reviews provide `Guest ID`s (slice 2). Masked PII and duplicate names make clean
  matching impossible now.
- **No scheduling / automated pull.** The existing "scripted browser pull" decision still stands
  as a manual CSV drop; recurrence here means *re-upload the same shape*, not a schedule.
- **No new dashboard screens or reporting tools** in this slice. The resolved reservation day
  projection and its semantic queries are unchanged.

## Design

### 1. Raw ingest — three fetcher identities

Three new ingest paths, each mirroring today's `opentable-csv-drop` mechanics
(`IngestionService.ingestPush` → byte-faithful `raw_record`, then a typed parser):

| Fetcher identity | Endpoint | Canonical target |
|---|---|---|
| `opentable-reservations` | `POST /api/ingest/opentable-reservations` | `canonical_reservation` (extended) |
| `opentable-reviews` | `POST /api/ingest/opentable-reviews` | raw only (slice 2) |
| `opentable-menu-items` | `POST /api/ingest/opentable-menu-items` | raw only (slice 3) |

- All three are token-gated (the existing `X-Webhook-Token` / `token` mechanism, fail-closed when
  unset) and gain an in-app operator upload entry (multipart, OIDC + `connectors:write`), matching
  the existing OpenTable upload. `KNOWN_SOURCES` in `ConnectorStatusController` gains the three
  entries.
- `source_system = OPENTABLE`, `fetch_method = FILE_EXPORT`, `content_type = text/csv` for all
  three.
- The existing `opentable-csv-drop` (thin 8-column) is **deprecated** as the reservation source:
  it writes the same `canonical_reservation` with a sparse field set, so an upload of the thin
  export *after* the rich one would bitemporally supersede rich facts with sparse ones. The rich
  export is authoritative. The old endpoint/parser stay in place but are no longer the intended
  path (recorded in `docs/connectors/opentable.md`).
- **Sparse-supersession guard:** because both paths write the same `canonical_reservation`, the
  plan must prevent a thin upload from superseding a rich one. Preferred: remove the thin path's
  canonicalization (keep the endpoint byte-faithful only, or retire it); acceptable fallback: make
  the thin path skip rows whose `Reservation ID` already has a current canonical row with
  non-null rich columns. This is a blocking decision for the plan, not an open question.

**Why reuse `ingestPush` rather than a `SourceConnector` backfill:** these arrive as operator
uploads (a fixed set of files), not a fan-out of N endpoint calls; `ingestPush` already matches
the existing OpenTable CSV path and the file is a single raw record per upload. (Contrast with
`ctb-detail-backfill`, which is a one-run-many-records fan-out.)

### 2. Parsers — header-validated, fail-loud, BOM-safe

One typed parser per dataset (`OpenTableReservationsCsvParser`, `OpenTableReviewsCsvParser`,
`OpenTableMenuItemsCsvParser`), all following the `OpenTableCsvParser` contract:

- Read the header and validate the **required columns**, throwing
  `ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", …)` on a missing/renamed column — so a
  report-shape change fails loudly rather than producing wrong rows.
- Strip a leading UTF-8 BOM at parse time (the raw bytes keep it).
- Handle quoted multi-line fields (review comments contain embedded newlines) — already provided
  by `commons-csv`.
- Parse per-row and fail on malformed required values; blank optional fields → `null`.
- **No reporting arithmetic in the parser** (same invariant as CTB): it emits facts (visit,
  ratings, prices), never computed metrics.

The reservations parser maps the 156-column export's names to the canonical columns below
(`Visit Date` → `reservation_at`, `Size` → `party_size`, etc.). `Status` is normalized; `Finished`
→ `COMPLETED`. `Reservation Status` (system `Done`/`AssumedSeated`/`AssumedDone`/`Cancelled`) is
kept as a separate `reservation_status` column rather than folded into `status`.

### 3. Stable identity keys (drive supersession)

| Entity | Key column | Notes |
|---|---|---|
| Reservation | `Reservation ID` | two formats coexist: legacy `CSV_140126_RES0000095` and native UUIDs (`6059805…`) — both are stable per reservation |
| Review | `Review ID` | `OT-140126-…` (OpenTable) / `RT-140126-…` (Google) |
| Menu item | `Invariant ID` | stable across edits; `Item ID` changes per edit, so invariant is the logical key |
| Guest | `Guest ID` | reviews only |

Supersession reuses the existing pattern: on a re-upload, a changed fact closes the current
canonical row and inserts a successor sharing the same logical identity; an unchanged fact is a
no-op (`sameFact`). The raw ledger always appends (never dedupes), so re-runs are safe.

### 4. Slice 1a — extend `canonical_reservation`

One new Flyway migration (V51+) adds nullable columns to `canonical_reservation`; existing
columns (`reservation_at`, `party_size`, `status`, `table_name`, `source_channel`, `party_name`)
are unchanged, so the thin CSV and the existing resolved reservation projection keep working.
New columns grouped by facet (all `nullable`, all `updatable = false`, all part of `sameFact`):

- **Visit lifecycle** — `created_at`, `cancelled_at`, `no_show_at`, `seated_at`, `finished_at`,
  `last_updated_at`, `total_actual_duration_minutes`.
- **Operational / floor** — `table_type`, `dining_area_assigned`, `dining_area_booked`,
  `dining_area_id_assigned`, `dining_area_id_booked`, `wait_staff`, `created_by`,
  `turn_time_setting`, `online_waitlist`, `quoted_wait_time`, `total_wait_time`,
  `reservation_type`, `is_flex_reso`, `occasion`, `reservation_status`.
- **Booking source / marketing** — `online_channel`, `partner`, `campaign_type`,
  `campaign_details`, `referrer_name`, `referrer_business`, `restref_source`,
  `restref_campaign_name`, `restref_id`.
- **Guest (denormalized dimension + reported rollups)** — `guest_first_name`, `guest_last_name`,
  `guest_nickname`, `guest_email`, `guest_phone` (both masked → `null`), `marketing_opt_in`,
  `company`, `birthday`, `anniversary`, `first_visit_date`, `recent_visit_date`,
  `upcoming_visits`, `completed_visits`, `cancelled_count`, `no_show_count`, `lifetime_spend`,
  `lifetime_per_cover_spend`, and `guest_id` (nullable FK to `canonical_guest`, always null this
  slice).
- **Experience / prepayment** — `experience_title`, `experience_type`, `experience_type_enum`,
  `experience_price_type`, `experience_price`, `prepayment_method`, `prepayment_status`,
  `experience_subtotal`, `addons_subtotal`, `service_charges`, `prepaid_experience_tip`,
  `prepaid_experience_vat`, `prepaid_experience_total_paid`, `experience_total_sales`,
  `experience_total_sales_with_tip`, `prepayment_required_total`, `prepayment_paid_total`.
- **Payment / revenue (POS breakdown)** — `pos_subtotal`, `pos_tax`, `pos_service_charges`,
  `pos_tip`, `pos_paid`, `pos_due`, `pos_check_ids`, `total_tips`, `total_vat`, `total_income`,
  `total_income_with_tip`, `total_revenue`, `total_revenue_with_tip`, `total_revenue_without_tip`,
  `pos_spend`, `pos_refund`, `pos_revenue`, `refund_amount`, `vendor_payout_amount`,
  `vendor_payout_status`, `currency`.
- **Notes / tags** — `guest_requests`, `visit_notes`, `reservation_tags`, `guest_tags`.

Stays in the raw ledger (nested/opaque, not typed columns): the `Price Breakdown …` array
columns, `Experience Prices`, `Private Dining Spaces`, `Guestbook Notes (raw)`, `Additional
Payments` and `Replies (raw JSON)`. They remain byte-faithful in `raw_record` and are parsed into
columns only if a later slice needs them.

The `CanonicalReservation.sameFact` comparison extends to cover every new field, so a re-upload
with a changed wait-staff value (for example) supersedes correctly and a no-change re-upload is a
no-op.

### 5. Slice 1b — add `canonical_guest`

One new Flyway migration (V52+) creates `canonical_guest` (bitemporal, mirroring
`CanonicalReservation`'s base):

```
source_record_ref varchar   -- OpenTable Guest ID (identity; reviews only)
guest_name varchar
first_name varchar
last_name varchar
nickname varchar
email varchar
phone varchar              -- masked in export → null
marketing_opt_in boolean
company varchar
first_visit_date date
recent_visit_date date
upcoming_visits int
completed_visits int
cancelled_count int
no_show_count int
lifetime_spend numeric
lifetime_per_cover_spend numeric
(+ bitemporal columns: valid_from/valid_to, recorded_at/superseded_at)
```

- Logical identity = `UUID.nameUUIDFromBytes("guest:" + guestId)`; source identity
  `(source_system = OPENTABLE, source_record_ref = Guest ID)`, with the standard
  `WHERE superseded_at IS NULL` unique index.
- **This slice defines the schema but does not populate it.** The reservation CSV has no `Guest ID`,
  so no clean guest row can be created from reservations (name + masked PII would merge distinct
  people and split name variants). `canonical_guest` is populated in slice 2 (reviews, which carry
  the clean key). The guest *lifetime* facts are nevertheless captured this slice — as reported
  columns on `canonical_reservation` (single-source, not recomputed).
- Reservation → guest linkage is the nullable `canonical_reservation.guest_id`, left null until
  slice 2's name-matching.

### 6. Reconciliation & resolved layer (unchanged)

`ReservationProjector` / `resolved_reservation_day` and the reservation semantic queries are
unchanged — they aggregate `party_size`/`status` from the (now wider) `canonical_reservation`.
Covers remain single-source and **not** reconciled against Lightspeed's `Covers`. The new
payment/revenue columns are read-only facts; any reconciliation of reservation POS spend against
Lightspeed sales/payments is a separate future slice (recorded in
`docs/connectors/matching-and-identity.md`).

### 7. Docs

- `docs/connectors/opentable.md` — replace the "8-column CSV" section with the three real export
  shapes; mark the thin `opentable-csv-drop` deprecated.
- `docs/connectors/matching-and-identity.md` — add `Guest` (identity, source identity, the
  no-guest-id-in-reservations ruling, deferred name-matching) and note the future review / menu
  identities.
- `docs/connectors/source-access.md` — OpenTable row now names the three reports.

## Testing

- **Parser unit tests** — each parser: BOM-stripping, header validation (missing column throws
  `CONNECTOR_SCHEMA_MISMATCH`), multi-line quoted review comments, malformed required values,
  blank-optional → null, and the reservation `Status`/`Reservation Status` mapping. Fixtures are
  trimmed copies of the real CSVs (a few rows each, checked in under
  `src/test/resources/fixtures/opentable/`).
- **Ingest service tests** — one raw record per upload with the right `fetcher_identity`; each row
  canonicalizes with the correct `source_record_ref`; an unchanged re-upload is a no-op and a
  changed field supersedes (close-and-reinsert).
- **Canonical integration (Testcontainers)** — extend the reservation integration test to assert
  the new columns persist and that a supersession closes the prior row and inserts a successor.
- **Migration test** — the reservation extension + `canonical_guest` migrations apply cleanly
  against a fresh DB and against the current schema (no dropped data).
- Full gate before finishing: `./gradlew test spotlessCheck`.

## Rollout

- New migrations (V51, V52) are additive (nullable columns + a new table), so no data migration;
  rollback is "revert the deploy".
- Ingestion is operator-triggered upload (no flag, no startup runner): backfill by uploading the
  three CSVs through the new endpoints, then verify the raw ledger gains the three identities with
  the expected row counts and `canonical_reservation` carries the richer columns.
- No canonical writes from reviews/menu items, so those uploads are raw-ledger-only and safe to
  re-run.

## Future work (recorded intent — not this slice)

- **Slice 2 — reviews:** add `canonical_review` (keyed on `Review ID`; `Source` = OpenTable/Google),
  populate `canonical_guest` from `Guest ID`, and (optionally) name-match reservations → guests to
  back-fill `guest_id`. Enables reputation/sentiment reporting.
- **Slice 3 — menu items:** add `canonical_menu_item` (keyed on `Invariant ID`, with modifier-group
  children), opening menu engineering and, later, reconciliation against CTB recipes / Lightspeed
  products.
- **Reconciliation — reservation POS spend vs Lightspeed sales/payments** (a second source for the
  same revenue), pending a confirmed `POS Check IDs` ↔ Lightspeed sale link.
- **Guest name-matching** for reservations when reviews provide the `Guest ID` anchor.

## Open questions

- Whether the thin `opentable-csv-drop` endpoint/parser is **removed** outright or kept in a
  byte-faithful-only form — the sparse-supersession guard (Design §1) is the binding constraint;
  the removal-vs-keep choice itself is a plan-level decision.
- Whether `guest_email`/`guest_phone` will ever be unmasked in future exports (they are masked in
  this one); if so, the guest identity/linkage strategy can strengthen.
- Exact `Status` value set for the rich export (observed: `Finished`, `Seated`, `Cancelled`,
  `Booked`; `Reservation Status` observed: `Done`, `AssumedSeated`, `AssumedDone`, `Cancelled`) —
  the parser normalizes the known set and passes through unknowns upper-cased (current behaviour).
