# OpenTable (GuestCenter) Access

How reservation data gets from OpenTable GuestCenter into the platform. Records
login and export mechanics and the CSV columns, never credentials.

## Current path: manual CSV drop

An operator exports the GuestCenter Reservations CSV and posts it to
`POST /api/ingest/opentable` (token-gated by `OPENTABLE_DROP_TOKEN`, fail-closed when
unset). `OpenTableCsvIngestService` stores the bytes in the raw ledger, then parses
and canonicalizes each row into `canonical_reservation`. A scripted Playwright pull
was designed (`docs/superpowers/specs/2026-09-23-opentable-*.md`) but is **not**
implemented; the login and export notes below are kept for when it is.

## Login

- URL: `https://guestcenter.opentable.com/login`
- Email + password form; success lands on the GuestCenter dashboard.

## Reservations export

- Navigate to the Reservations report and choose the date range, then trigger the
  CSV export. A future scripted pull would capture the download with Playwright
  (`page.waitForDownload`).
- Provisionally: report at `https://guestcenter.opentable.com/reports/reservations`,
  export button labelled "Export".

## CSV columns

`Reservation ID`, `Date`, `Time`, `Party Size`, `Status`, `Table`, `Source`, `Guest Name`.

- `Status` values observed: `Booked`, `Seated`, `Completed`, `Cancelled`,
  `No Show`, `Walk-in` (normalized by `OpenTableCsvParser`).
- `Table` and `Source` may be blank.
