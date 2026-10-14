# OpenTable (GuestCenter) Access

How OpenTable data gets from GuestCenter into the platform. Records the access mechanics,
endpoints and quirks, never credentials. Design record:
[`2026-10-13-opentable-assisted-session-design.md`](../superpowers/specs/2026-10-13-opentable-assisted-session-design.md).

## Current path: assisted sign-in + JSON API pull

An owner opens **Data health → OpenTable → Sync OpenTable**, picks *Recent and upcoming* or
*Full history*, and signs in to GuestCenter in a live view of a browser running on the VM (the
`opentable-browser` sidecar). The backend notices the sign-in, closes the view and starts an
`OPENTABLE` / `opentable-api` ingestion run on its own; the owner can close the tab.

- **Nothing types credentials or solves challenges automatically.** GuestCenter's login is
  GuestCenter → Okta → password behind hCaptcha, with Akamai Bot Manager on every page. The
  human does the login (including any challenge) in the live view; automation only starts after.
- **The live view opens GuestCenter's root** (`guestcenter.opentable.com`), which 302s to `/login`.
  Navigating straight to a restaurant path (`/restaurant/<id>/`) returns a 404 "Page not found"
  while signed out, so the assisted flow never uses it; the restaurant id is only used in the JSON
  API paths after sign-in.
- **Off by default.** Enable with `OPENTABLE_API_ENABLED=true` and run the sidecar
  (`docker-compose.prod.yml`). Disabled, the dialog says so and the CSV path below still works.
- **Sessions.** `OT-SessionId` lasts 30 minutes from the last activity, and the app refreshes its
  token every 30 minutes. A sync started within that window of the previous one skips the login.
  The absolute session ceiling is unknown (the auth cookies are HttpOnly); record it here once
  observed in production.
- **The browser is parked on `about:blank`** after every sync run, after an idle sign-in view
  times out, and when a sign-in is abandoned. A GuestCenter page left open refreshes its own
  login every 30 minutes, which would keep the VM signed in indefinitely; parked, the session
  lapses on OpenTable's own timeouts. Whether a later sync still needs a login depends on how
  long OpenTable's (HttpOnly) cookies last. The live view handles either case.
- **Sign out** in the dialog clears the OpenTable login from the sidecar's profile volume.

### What the run pulls

All requests are same-origin `fetch()` calls inside the signed-in GuestCenter page, spaced ≥1.5 s
apart, with 429/5xx backoff (5 s doubling to 90 s, 10 attempts). Responses are stored in the raw
ledger byte-for-byte before parsing.

**Unchanged responses aren't stored again.** A response is skipped when it matches the latest
stored payload for the same dataset window (`fetcherIdentity` + `sourceRecordRef`), ignoring the
top-level `meta-data` / `meta` blocks, which carry a fresh request id and timestamp on every call.
The earlier payload stays the evidence for that content; nothing is deleted or rewritten. Skipped
reservation windows are still canonicalized against the stored record, a no-op when nothing
changed. Reservation windows snap to half-month boundaries so their refs repeat between syncs.
Reviews are keyed by the run's date range, so they're always stored, but they're small (tens of
KB). A sync where nothing changed ends `NO_NEW_DATA`.

| Dataset (`fetcherIdentity`) | Endpoint | Paging | Canonical |
|---|---|---|---|
| `opentable-reservations` | `GET /gateway/long-proxies/restaurant-reporting/reportingBiDatasourcesGold/api/v5/reservations/gold/?rid=…&startDate=…&endDate=…&offset=0&limit=1000&sort=visitDate&stateCategories=booked,seated,finished,noShow,cancelled&isVisitDate=true` | Half-month windows; a full (1000-row) window is split and retried | `canonical_reservation` |
| `opentable-reviews` | `POST /gateway/proxies/hospitality/ot4rFeedbackService/api/v1/feedback/restaurant/{rid}/reviews/details` | `page` 0…n, 25 per page, until `meta.totalCount` | `canonical_review` |
| `opentable-menu-items` | `GET /gateway/proxies/menu-manager/restaurantMenuService/v2/restaurants/{rid}/items?pageSize=100&pageNumber=n&statuses=Active,Inactive,Unassigned,Archived` | until a short page | raw only |
| `opentable-menus` | `GET …/restaurantMenuService/v1/restaurants/{rid}/menus` | single | raw only |

*Recent and upcoming* covers visit dates from 60 days ago to 18 months ahead plus the last 120
days of reviews; *Full history* starts at `opentable.backfill-from` (2020-01-01). Menu items are
always pulled in full.

### Quirks (found 2026-10-13, restaurant 140126)

- **Never page reservations by offset.** The endpoint sorts by visit time only; offset pages over
  tied times skipped 11 of 24,170 rows. Windows small enough for one page avoid it (the busiest
  month seen had ~1,250 reservations).
- The report UI shows 26 months; the API returned everything back to December 2020.
- 153 fields per reservation, including `reservationId`. Phone and email are masked
  (`**********`) in the API, so contact details aren't available this way. Money fields are in
  cents (`currencyMultiplier` 100).
- With all five statuses selected the UI omits `stateCategories` entirely; both forms return the
  same rows.
- Reviews: hidden reviews need `restaurantVisibility: ["Visible","Hidden"]`; the "Updated"
  status filter is `showUpdatedByDiner: true`; `sources: []` means all sources and also returns
  Facebook reviews, which the filter UI doesn't list.
- Menu items: the page requests `sources=Opentable`, which hides `ExperienceAddOns` items. Omit
  `sources` to get them.
- The reservations report's **Export** button does not download a file; it emails the report to
  the account's address.

Status mapping and identity are in [`matching-and-identity.md`](matching-and-identity.md).

## Fallback: manual CSV drop

Three rich GuestCenter reports are ingested as CSV — via a token-gated webhook or an in-app
multipart upload from the connectors screen. Each ingest service stores the bytes in the raw
ledger first; the reservations and reviews paths also canonicalize. Menu items are validated
for shape and parked raw until their canonical slice lands.

| Report | Endpoint | Fetcher identity | Canonical target |
|---|---|---|---|
| Reservations (rich) | `POST /api/ingest/opentable-reservations` | `opentable-reservations` | `canonical_reservation` |
| Reviews | `POST /api/ingest/opentable-reviews` | `opentable-reviews` | `canonical_review` |
| Menu items | `POST /api/ingest/opentable-menu-items` | `opentable-menu-items` | raw only (future `canonical_menu_item`) |

The shapes below are the real columns from a one-time manual export of restaurant **140126**
(Goldy's), not provisional.

### Reservations (rich, 156 columns)

Required columns: `Reservation ID`, `Visit Date`, `Size`, `Status`. The full export groups into
four facets:

- **Guest identity & lifetime value** — name/contact, first/recent visit, visit counts,
  no-show/cancel counts, lifetime and per-cover spend. Phone/email are **masked** (`**********`)
  and map to `null`.
- **Operational/floor detail** — dining area (assigned/booked + ids), table type, wait staff,
  wait/turn times, reservation type, and the full visit lifecycle timestamps
  (created/seated/finished/cancelled/no-show).
- **Booking source & marketing** — online channel, partner, campaign, referrer, RestRef fields.
- **Revenue & payment breakdown** — prepaid-experience block and a POS breakdown (subtotal, tax,
  service charges, tip, paid, due) plus income/revenue totals.

`Status` values observed: `Finished` (→ `COMPLETED`), `Seated`, `Cancelled`, `Booked`; a separate
`Reservation Status` column holds the system values (`Done`, `AssumedSeated`, `AssumedDone`,
`Cancelled`) and is kept distinct. The file carries a UTF-8 BOM (stripped at parse time; the raw
bytes keep it).

### Reviews

Both the CSV drop and the assisted-session JSON pull canonicalize into `canonical_review`.

**CSV columns.** Required columns: `Review ID`, `Source`, `Review Date`, `Overall Rating`; `Comment`
optional (multi-line, quoted). `Source` distinguishes OpenTable reviews from Google reviews. Reviews
carry a clean `Guest ID` (e.g. `6a8e7ffc…`) — the anchor for `canonical_guest`. Review identity is
`Review ID` (`OT-140126-…` for OpenTable, `RT-140126-…` for Google).

**JSON (API pull).** The same fields arrive under different names: `id` is the review identity
(identical to `otReviewId`; `OT-…` for OpenTable, `RT-…` for Google and Facebook), `source` is
`OpenTable` / `Google` / `Facebook`, `reviewDate`/`visitDate` are ISO instants, `recommends` is the
would-recommend flag, `noiseText` is the free-text noise label, `privateNotes` is the private note,
and `tags`/`keywords` are already arrays. Ratings are whole floats (`5.0`) with `0.0` meaning "not
rated"; the parser normalises both to the canonical 1–5 integer with `0` → null. The numeric
`noiseRating` is not modelled (see the review-analytics design spec's open questions).

### Menu items

Required columns: `Item ID`, `Invariant ID`, `Items`, `Price`. `Price` carries a currency prefix
(e.g. `A$72.00`). The **`Invariant ID` is the stable logical key** (survives edits); `Item ID`
changes per edit. Rows include modifier groups (sizes/variations), categories, dietary and status.

The thin 8-column `opentable-csv-drop` path (`POST /api/ingest/opentable`,
`OpenTableCsvIngestService`) is **deprecated** (raw-only, no longer canonicalizes) — its assumed
columns (`Reservation ID`, `Date`, `Time`, …) do not match GuestCenter's current report, so a
stale thin upload cannot bitemporally supersede a rich reservation row. Prefer the assisted sync
or the rich exports above.
