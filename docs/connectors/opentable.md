# OpenTable (GuestCenter) Access

How the OpenTable connector reaches reservation data. Records login and export
mechanics and the CSV columns, never credentials.

## Login

- URL: `https://guestcenter.opentable.com/login`
- Email + password form; success lands on the GuestCenter dashboard.

## Reservations export

- Navigate to the Reservations report and choose the date range, then trigger the
  CSV export. The browser download is captured by Playwright (`page.waitForDownload`).
- Provisionally: report at `https://guestcenter.opentable.com/reports/reservations`,
  export button labelled "Export".

## CSV columns

`Reservation ID`, `Date`, `Time`, `Party Size`, `Status`, `Table`, `Source`, `Guest Name`.

- `Status` values observed: `Booked`, `Seated`, `Completed`, `Cancelled`,
  `No Show`, `Walk-in` (normalized by `OpenTableCsvParser`).
- `Table` and `Source` may be blank.
