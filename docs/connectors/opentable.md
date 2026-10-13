# OpenTable (GuestCenter) Access

How reservation, review and menu data gets from OpenTable GuestCenter into the platform. Records
login and export mechanics and the CSV columns, never credentials.

## Current path: manual CSV drop

An operator exports a GuestCenter report and posts it to a token-gated ingest endpoint
(`OPENTABLE_DROP_TOKEN`, fail-closed when unset), or uploads it in-app through the connectors
screen (multipart, OIDC + `connectors:write`). Three reports are ingested:

| Report | Endpoint | Fetcher identity | Canonical target |
|---|---|---|---|
| Reservations (rich) | `POST /api/ingest/opentable-reservations` | `opentable-reservations` | `canonical_reservation` |
| Reviews | `POST /api/ingest/opentable-reviews` | `opentable-reviews` | raw only (future `canonical_review`) |
| Menu items | `POST /api/ingest/opentable-menu-items` | `opentable-menu-items` | raw only (future `canonical_menu_item`) |

Each ingest service stores the bytes in the raw ledger first; only the reservations path also
canonicalizes. Reviews and menu items are validated for shape and parked raw until their slices
land.

The thin 8-column `opentable-csv-drop` path (`POST /api/ingest/opentable`,
`OpenTableCsvIngestService`) is **deprecated**: it is now raw-only — it no longer canonicalizes, so
a stale thin upload cannot bitemporally supersede a rich reservation row. The rich export is the
authoritative reservation source.

A scripted Playwright pull was designed (`docs/superpowers/specs/2026-09-23-opentable-*.md`) but is
**not** implemented; the login and export notes below are kept for when it is.

## Login

- URL: `https://guestcenter.opentable.com/login`
- Email + password form; success lands on the GuestCenter dashboard.

## Reservations export

- Navigate to the Reservations report and choose the date range, then trigger the CSV export. A
  future scripted pull would capture the download with Playwright (`page.waitForDownload`).
- Provisionally: report at `https://guestcenter.opentable.com/reports/reservations`, export button
  labelled "Export".

## CSV shapes

The three reports were captured as a one-time manual export for restaurant **140126** (Goldy's);
the shapes below are the real columns, not provisional.

### Reservations (rich, 156 columns)

Required columns: `Reservation ID`, `Visit Date`, `Size`, `Status`. The full export is a superset
of the old thin CSV, grouping into four facets:

- **Guest identity & lifetime value** — name/contact, first/recent visit, visit counts,
  no-show/cancel counts, lifetime and per-cover spend. Phone/email are **masked**
  (`**********`) in the export and map to `null`.
- **Operational/floor detail** — dining area (assigned/booked + ids), table type, wait staff,
  wait/turn times, reservation type, and the full visit lifecycle timestamps
  (created/seated/finished/cancelled/no-show).
- **Booking source & marketing** — online channel, partner, campaign, referrer, RestRef fields.
- **Revenue & payment breakdown** — prepaid-experience block and a POS breakdown (subtotal, tax,
  service charges, tip, paid, due) plus income/revenue totals.

`Status` values observed: `Finished` (→ `COMPLETED`), `Seated`, `Cancelled`, `Booked`; a separate
`Reservation Status` column holds the system values (`Done`, `AssumedSeated`, `AssumedDone`,
`Cancelled`) and is kept distinct.

Notable: the reservations CSV has **no `Guest ID` column** — guest identity there is name plus
masked phone/email. The file carries a UTF-8 BOM (stripped at parse time; the raw bytes keep it).

### Reviews

Required columns: `Review ID`, `Source`, `Review Date`, `Overall Rating`; `Comment` optional
(multi-line, quoted). `Source` distinguishes OpenTable reviews from Google reviews. Reviews carry a
clean `Guest ID` (e.g. `6a8e7ffc…`) — the anchor for the future `canonical_guest` and reservation →
guest linkage. Review identity is `Review ID` (`OT-140126-…` for OpenTable, `RT-140126-…` for
Google).

### Menu items

Required columns: `Item ID`, `Invariant ID`, `Items`, `Price`. `Price` carries a currency prefix
(e.g. `A$72.00`). The **`Invariant ID` is the stable logical key** (survives edits); `Item ID`
changes per edit. Rows include modifier groups (sizes/variations), categories, dietary and status.
