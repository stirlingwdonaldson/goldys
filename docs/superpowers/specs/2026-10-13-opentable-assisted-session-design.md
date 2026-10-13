# OpenTable Assisted-Session Connector Design

**Status:** Approved (direction agreed with the project owner 2026-10-13)
**Date:** 2026-10-13
**Scope:** `connectors/opentable/`, a browser sidecar in `docker-compose.prod.yml`, the Data
health page. Supersedes decisions 1 and 2 of
[`2026-09-23-opentable-session-redesign.md`](2026-09-23-opentable-session-redesign.md) (automated
paced login and the stealth browser engine). The CSV-drop path stays as a fallback.

## 1. Objective

Make an OpenTable sync a job of about two minutes of human time, doable from any device: an
owner clicks **Sync OpenTable** in the app, signs in to GuestCenter through a live view of a
browser running on the VM, and closes the tab. The backend then pulls reservations, reviews and
menu items from GuestCenter's own JSON endpoints, stores every response byte-faithfully in the raw
ledger, and canonicalizes reservations. No credentials are stored and no bot-detection is bypassed.

## 2. Findings that drive the design (discovery, 2026-10-13)

From a manual, logged-in extraction of the full GuestCenter history for restaurant 140126:

| Finding | Consequence |
|---|---|
| Login is GuestCenter → Okta → password, gated by hCaptcha; Akamai Bot Manager cookies (`bm_so`, `bm_lso`) are set on every page | Automating the login means defeating bot detection. We don't: a human signs in. |
| `OT-SessionId` expires 30 min after the last activity (sliding); the app refreshes its token every 1800 s (`tokenRefreshIntervalSeconds`) and re-bootstraps every 12 h. Auth cookies are HttpOnly, so the absolute session ceiling is unknown | Plan for a fresh login per sync; reuse the session only within the idle window. Measure the ceiling in production (§10). |
| The report's **Export** button does not download; it emails the report to the account address | The "capture the download" approach in the 09-23 design cannot work. |
| The exported/on-screen report has no Reservation ID column; statuses read `Finished`/`No show`; 88 selectable columns | `OpenTableCsvParser`'s assumed columns (`Reservation ID, Date, Time, …`) do not match the real report. |
| The report UI is backed by JSON endpoints that return every field (153 per reservation) including `reservationId` | Ingest the JSON responses, not a CSV. |
| The reservations endpoint returns 429 under burst load; ~1.5 s spacing with exponential backoff was clean | Throttle and back off; classify persistent 429s. |
| Offset paging over `sort=visitDate` skipped 11 of 24,170 rows where visit times tie | Never page: use windows small enough to fit one page (half-month, `limit=1000`), and split any window that comes back full. |
| The UI shows only 26 months; the API served everything back to 2020-12 | Full backfill is possible through the API. |
| Phone and email are masked (`**********`) in the API response; money is in cents (`currencyMultiplier` 100) | Don't model contact details; divide money by 100 when canonicalizing. |
| Reviews: POST `reviews/details`, 25 per page, `meta.totalCount`; hidden reviews need `restaurantVisibility: ["Visible","Hidden"]`; "Updated" status is `showUpdatedByDiner: true`; `sources: []` means all (it also returns Facebook, which the filter UI doesn't list) | Encode these as the fixed request body. |
| Menu items: `sources=Opentable` (the page default) hides `ExperienceAddOns` items | Omit the `sources` parameter. |

## 3. Decisions

1. **Human login, automated everything after.** The connector never types credentials and
   never solves or evades a challenge. Supersedes the 09-23 "auto-login with human-like pacing"
   and "Fortress stealth Chromium" decisions.
2. **Remote view through the app, not a new public port.** Production publishes only the
   frontend; `/api/*` is rewritten to the backend with same-origin cookies. Next.js rewrites do
   not proxy WebSockets, so noVNC would need a new published port and its own auth. Instead the
   backend relays a **CDP screencast** (JPEG frames over Server-Sent Events) and accepts
   mouse/keyboard events over POST, all behind the existing session and `PermissionService`.
3. **A plain, headed Chromium sidecar.** Ordinary Chromium under Xvfb with a persistent profile
   volume and `--remote-debugging-port` on a private Docker network shared only with the backend.
   No stealth patches, no fingerprint spoofing.
4. **Raw JSON in the ledger.** Each endpoint response is one `FetchedPayload`
   (`application/json`) under a per-dataset `fetcherIdentity`. Parsing happens after the bytes
   are stored, as for CTB.
5. **Datasets.** Reservations: raw and canonical (existing `canonical_reservation`). Reviews and
   menu items: raw ledger only for now, the same pattern as CTB's inventory endpoints. Their
   canonical domains are separate `add-domain` slices.
6. **Incremental by default, full on demand.** Incremental reservations cover visit dates from
   today − 60 days to today + 18 months; incremental reviews cover the last 120 days by review
   date; menu items are always pulled in full (about 80 KB). "Full sync" pulls everything from
   `opentable.backfill-from` (default 2020-01-01).
7. **The CSV drop stays** as the manual fallback and is not removed in this slice.

## 4. In scope / out of scope

**In:** browser sidecar and compose wiring; backend session service (connect, screencast relay,
input relay, auth detection, idle teardown); `OpenTableApiConnector` with the three datasets;
reservation JSON parser and canonical mapping; the session and sync REST endpoints and permission
seed; the Data health "Sync OpenTable" panel (live and demo `Api`); docs.

**Out:** canonical/resolved domains for reviews and menu items; scheduling (a human is in the
loop by design); guest contact data; removing or fixing the CSV parser; the OpenTable partner API.

## 5. Architecture

```
 owner's browser ──HTTPS──▶ frontend (Next.js) ──/api rewrite──▶ backend
     ▲   SSE frames / POST input                                   │ CDP over WebSocket
     └──────────────────────────────────────────────────────────── ▼
                                                     opentable-browser sidecar (Chromium + Xvfb)
                                                     network: opentable-cdp (backend only)
                                                     volume: opentable_browser_profile
```

### 5.1 Sidecar `opentable-browser`

- A small image (`opentable-browser/Dockerfile`: pinned Debian slim + distro `chromium`, `xvfb`,
  `fonts-noto`) running `xvfb-run chromium --remote-debugging-address=0.0.0.0
  --remote-debugging-port=9222 --user-data-dir=/profile --window-size=1280,800`.
- Headed Chromium binds DevTools to 127.0.0.1 regardless of `--remote-debugging-address`, so the
  entrypoint also runs `socat TCP-LISTEN:9223,fork,reuseaddr TCP:127.0.0.1:9222`. DevTools rejects
  a `Host` header that is a hostname (DNS-rebinding guard), so the backend resolves the sidecar's
  service name to its container IP and connects to `http://{ip}:9223`. Chromium runs with
  `--remote-allow-origins=*`, which is acceptable only because the port is unreachable outside
  `opentable-cdp`.
- No `ports:`. It joins only `opentable-cdp`, an `internal: true` network that the backend also
  joins. CDP is full browser control, so no other service may reach it.
- `/profile` is the named volume `opentable_browser_profile`. It holds a live OpenTable session,
  so treat it as a secret: it is never backed up with application data, and logout wipes it (§5.3).

### 5.2 Backend: `connectors/opentable/session/`

- `OpenTableBrowser` is a port with two adapters: `CdpOpenTableBrowser` and a test fake. It
  exposes `navigateToLogin()`, `isAuthenticated()`, `startScreencast(Consumer<Frame>)`,
  `stopScreencast()`, `dispatch(InputEvent)` and `fetchJson(method, path, body)`. `fetchJson` runs
  `fetch()` inside the GuestCenter page (`Runtime.evaluate` with `awaitPromise`), so requests
  carry the real session and origin.
- `CdpOpenTableBrowser` is a small Chrome DevTools Protocol client on the JDK's
  `java.net.http.WebSocket` plus Jackson, not Playwright Java. The backend image is a minimal JRE
  (it has not launched a browser since the Fortress change), and Playwright Java would add a
  bundled Node driver to call about eight CDP methods: `Target.getTargets`/`attachToTarget`,
  `Page.navigate`, `Page.startScreencast`/`stopScreencast`/`screencastFrameAck`,
  `Input.dispatchMouseEvent`/`dispatchKeyEvent`/`insertText`, and `Runtime.evaluate`. It
  discovers the page target from `http://{cdp-host}/json/list`.
- `isAuthenticated()` is a `GET /gateway/proxies/gcrestaurant/api/userContext` that returns 200,
  not a page-text heuristic.
- `OpenTableSessionService` holds a small state machine:
  `DISCONNECTED → LOGIN_REQUIRED → AUTHENTICATED → SYNCING → DONE | FAILED`. It polls
  `isAuthenticated()` every 2 s while `LOGIN_REQUIRED`. On `AUTHENTICATED` it stops the screencast
  and starts the sync run automatically, so the owner can close the tab.
- At most one viewer at a time. The screencast stops after 10 minutes without input, and on
  `SYNCING`.
- Input events are bounded: mouse coordinates are clamped to the viewport, key names come from an
  allow-list, and text is limited to 256 characters per event. **Input payloads and frames are
  never logged or persisted.** They carry passwords and guest PII.

### 5.3 REST (all OWNER-only via `PermissionService`, resource `connectors.opentable.session`)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/connectors/opentable/session` | Attach to the sidecar; navigate to login unless already authenticated. Body `{ "mode": "INCREMENTAL" \| "FULL" }`. Returns state. |
| `GET` | `/api/connectors/opentable/session/stream` | SSE: `state` events and `frame` events (base64 JPEG, about 5 fps, quality 60). |
| `POST` | `/api/connectors/opentable/session/input` | One input event (`mouse`, `wheel`, `key`, `text`). |
| `DELETE` | `/api/connectors/opentable/session` | Stop viewing. With `?logout=true`, also clear the profile (sign out of OpenTable). |

The sync run itself is an ordinary `IngestionRun`, visible on Data health like any other.

### 5.4 `OpenTableApiConnector implements SourceConnector`

`sourceSystem()` = `OPENTABLE`, `connectorName()` = `opentable-api`. `fetch(watermark, sink)`
throws `CONNECTOR_AUTH_FAILED` ("Sign in to OpenTable from Data health → Sync OpenTable") when
the session is not authenticated. The watermark carries the mode (`FULL` or blank for
incremental). Each dataset runs independently and failures are aggregated, as in `CtbConnector`.

| Dataset (`fetcherIdentity`) | Request | Window / paging | `sourceRecordRef` |
|---|---|---|---|
| `opentable-reservations` | `GET /gateway/long-proxies/restaurant-reporting/reportingBiDatasourcesGold/api/v5/reservations/gold/?rid={rid}&startDate=&endDate=&offset=0&limit=1000&sort=visitDate&startTime=&endTime=&stateCategories=booked,seated,finished,noShow,cancelled&isVisitDate=true` | Half-month windows; a window that returns 1000 rows is split in two and retried; a single day at 1000 fails `CONNECTOR_SCHEMA_MISMATCH` | `{start}..{end}` |
| `opentable-reviews` | `POST /gateway/proxies/hospitality/ot4rFeedbackService/api/v1/feedback/restaurant/{rid}/reviews/details` with all statuses, `showUpdatedByDiner`, all ratings, `sources: []`, `restaurantVisibility: ["Visible","Hidden"]`, text and rating-only reviews | `page` 0..n until `meta.totalCount` | `{start}..{end}#p{page}` |
| `opentable-menu-items` | `GET /gateway/proxies/menu-manager/restaurantMenuService/v2/restaurants/{rid}/items?pageSize=100&pageNumber={n}&statuses=Active,Inactive,Unassigned,Archived` | until a short page | `p{n}` |
| `opentable-menus` | `GET …/v1/restaurants/{rid}/menus` | single | `menus` |

All requests are spaced at least 1.5 s apart. A 429 or 5xx backs off exponentially (5 s → 90 s,
10 attempts), then fails `CONNECTOR_RATE_LIMITED`. A 401 or 403 mid-run fails
`CONNECTOR_AUTH_FAILED`, and the run ends `PARTIAL` with the windows already stored kept. The
restaurant id comes from `opentable.restaurant-id` (140126).

### 5.5 Reservation canonicalization

`OpenTableReservationJsonParser` maps each element of `data[]` to `ReservationInput`. A missing
required key fails `CONNECTOR_SCHEMA_MISMATCH`.

| `ReservationInput` | From |
|---|---|
| `reservationId` | `reservationId` |
| `reservationAt` | `visitDate` (venue-local, no offset) at `opentable.timezone` |
| `partySize` | `partySize` |
| `status` | `stateCategory`: `booked`→`BOOKED`, `seated`→`SEATED`, `finished`→`COMPLETED`, `noShow`→`NO_SHOW`, `cancelled`→`CANCELLED`; **but** `primarySource = walkin` with `seated`/`finished` → `WALK_IN` (keeps `ReservationProjector`'s walk-in count) |
| `tableName` | `tableId` |
| `sourceChannel` | `primarySource` (`website`, `network`, `phone`, `walkin`) |
| `partyName` | `guestName` |

Identity is unchanged (`matching-and-identity.md`): `reservation:` + OpenTable reservation id.
Re-pulling an unchanged reservation is a no-op (`sameFact`). A changed one supersedes.

## 6. Security

- CDP is reachable only on the internal `opentable-cdp` network. Frames and input go only to an
  authenticated OWNER, over the existing origin.
- Keystrokes, including the OpenTable password, pass through the backend in memory only. They are
  never logged, persisted, or included in exception messages or Sentry events. A Sentry
  `beforeSend` scrub covers the input endpoint path.
- The profile volume is a live credential. It is excluded from backups and wiped by
  `DELETE …?logout=true`.
- Connectors stay one-way. The connector issues only the GETs and read-only POST queries listed
  in §5.4.

## 7. Failure handling

| `failureType` | When |
|---|---|
| `CONNECTOR_BROWSER_FAILED` | Sidecar unreachable, or CDP connect fails |
| `CONNECTOR_AUTH_FAILED` | Not signed in at start, or 401/403 mid-run |
| `CONNECTOR_RATE_LIMITED` | Backoff exhausted |
| `CONNECTOR_SCHEMA_MISMATCH` | Missing expected keys, non-JSON body, or a one-day window at the 1000-row cap |

## 8. Testing

- Parser tests use synthetic fixtures shaped like the real responses (never real guest data):
  status mapping including walk-in, venue-zone conversion, and a missing key throwing.
- Connector tests use a fake `OpenTableBrowser`: window splitting on a full window, 429 backoff
  then success, auth failure mid-run → PARTIAL with earlier payloads kept, and per-dataset
  failure isolation.
- An integration test (Testcontainers Postgres) checks that payloads land in `raw_record` and a
  re-pull is a canonical no-op.
- The sidecar and CDP are not exercised in CI. There is a manual smoke checklist in the plan.

## 9. User flow and timing

1. Data health → **Sync OpenTable** → live view of the GuestCenter login (about 3 s).
2. Owner signs in (email → Okta → password, plus a challenge if hCaptcha shows one): about 1 min.
3. The panel shows "Signed in, syncing…" and the owner can close the tab. An incremental run
   takes under a minute; a full backfill takes about 5 minutes.
4. The run shows on Data health with payload counts and any classified failure.

## 10. Risks and open questions

- **Internal endpoints can change without notice.** Schema checks fail loudly. The CSV fallback
  remains.
- **Terms of service.** Automated access to GuestCenter's internal API may conflict with
  OpenTable's terms. Mitigations: a human authenticates each sync, read-only, low request rate.
  The owner accepts this residual risk.
- **Akamai may still flag the sidecar** (container fingerprint, VM IP). If it does, the owner sees
  a challenge in the live view and solves it. If GuestCenter blocks outright, fall back to the
  CSV drop.
- **Absolute session ceiling is unknown.** Record the time from login to first forced re-login in
  production and note it in `docs/connectors/opentable.md`.
- **Existing CSV-drop rows.** Before the first API sync, check (read-only)
  `canonical_reservation WHERE source_system = 'OPENTABLE'`. The CSV parser requires a
  `Reservation ID` column the real report lacks, so this is expected to be empty. If it isn't,
  decide how those refs relate before syncing.
