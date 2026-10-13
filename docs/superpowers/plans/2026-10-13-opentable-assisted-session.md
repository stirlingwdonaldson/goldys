# OpenTable Assisted-Session Connector Implementation Plan

**Goal:** An owner clicks **Sync OpenTable** on Data health and signs in to GuestCenter through
a live view of a browser on the VM. The backend then pulls reservations, reviews and menu items
from GuestCenter's JSON endpoints into the raw ledger and canonicalizes reservations.

**Architecture:** Headed Chromium sidecar on a private network; a minimal CDP client in the
backend relays a screencast (SSE) and input (POST) through the existing `/api` origin; an
`OpenTableApiConnector` (`SourceConnector`) runs in-page `fetch()` calls and streams each JSON
response to the sink. Reviews and menu items stay raw-only.

**Spec:** `docs/superpowers/specs/2026-10-13-opentable-assisted-session-design.md`

## Global constraints

- Flyway only; next version is `V52`. Never edit an applied migration.
- Bytes hit `raw_record` before any parsing; canonical is append-only with supersession.
- The connector never writes to OpenTable. It issues only the GETs and read-only POST queries in
  spec §5.4.
- **Never log, persist, or put in an exception message or Sentry event:** input payloads,
  screencast frames, cookies, response bodies. `ConnectorFetchException` messages are
  operator-facing and generic.
- Permission resource `connectors.opentable.session` is seeded `ALL × OWNER` only (READ for
  state/stream, WRITE for start/input/end).
- Test fixtures are synthetic. Never commit real guest names or review text.
- Checks: `cd backend && ./gradlew test spotlessCheck`; `cd frontend && bun run typecheck && bun
  run lint && bun run test`. Work on this branch; no push or deploy without approval.

## Tasks

0. **Pre-flight (read-only).** With a `default_transaction_read_only=on` session, count
   `canonical_reservation WHERE source_system = 'OPENTABLE'` in production. If it's non-zero,
   stop and resolve spec §10 before Task 7 lands.
1. **Sidecar.** `opentable-browser/Dockerfile` (pinned `debian:bookworm-slim`; `chromium`,
   `xvfb`, `xauth`, `socat`, `fonts-noto`, `fonts-noto-color-emoji`; non-root user) and
   `entrypoint.sh` (`xvfb-run -s "-screen 0 1280x800x24" chromium --no-first-run
   --user-data-dir=/profile --remote-debugging-port=9222 --remote-allow-origins=*
   --window-size=1280,800 about:blank` plus `socat TCP-LISTEN:9223,fork,reuseaddr
   TCP:127.0.0.1:9222`; exits if either dies). Compose (prod and dev):
   - service `opentable-browser` with no `ports:`, `shm_size: 1gb`, `restart: unless-stopped`
   - network `opentable-cdp` (`internal: true`), joined by `backend` and the sidecar only
   - volume `opentable_browser_profile` mounted at `/profile`
   - backend env `OPENTABLE_CDP_HOST=opentable-browser`, `OPENTABLE_CDP_PORT=9223`,
     `OPENTABLE_API_ENABLED`
2. **Config.** `OpenTableApiConfig` (`@ConfigurationProperties("opentable")`): `restaurantId`
   (140126), `cdpHost`, `cdpPort`, `backfillFrom` (2020-01-01), `requestSpacing` (1.5 s),
   `apiEnabled` (default false). Beans below are `@ConditionalOnProperty(opentable.api-enabled)`.
   Update `.env.example`.
3. **CDP client** (`connectors/opentable/cdp/`).
   - `CdpTransport` interface; `JdkWebSocketCdpTransport` uses `java.net.http.WebSocket`,
     reassembling partial text frames.
   - `CdpConnection`: id-correlated `send(method, params) → CompletableFuture<JsonNode>` with a
     timeout, event listeners by method name, and close handling that fails pending futures.
   - Target discovery: resolve `cdpHost` to an IP (`InetAddress`), `GET http://{ip}:{port}/json/list`,
     pick the first `page` target or create one with `/json/new?about:blank`.
   - Tests: a stub transport for correlation, timeout, event dispatch, and close-fails-pending.
4. **Browser port.** `OpenTableBrowser` (in `connectors/opentable/session/`) with
   `CdpOpenTableBrowser` and `FakeOpenTableBrowser` (test).
   - `navigateToLogin()`: `Page.navigate` to `https://guestcenter.opentable.com/restaurant/{rid}/`,
     which redirects to login when signed out.
   - `isAuthenticated()`: in-page `fetch('/gateway/proxies/gcrestaurant/api/userContext')`
     returns status 200.
   - `fetchJson(method, path, body)`: `Runtime.evaluate` with `awaitPromise`, returning
     `{status, contentType, bodyText}`. It rejects any path not starting with `/gateway/` and any
     method other than GET/POST.
   - Screencast: `Page.startScreencast` (jpeg, quality 60, maxWidth 1280, everyNthFrame 2) and
     ack each frame.
   - Input: `dispatch(InputEvent)` maps to `Input.dispatchMouseEvent`, `dispatchKeyEvent` or
     `insertText`.
5. **Session service** (`OpenTableSessionService`).
   - State machine `DISCONNECTED → LOGIN_REQUIRED → SYNCING → DONE | FAILED`, with
     a single viewer.
   - Poll `isAuthenticated()` every 2 s while `LOGIN_REQUIRED`. On success, stop the screencast
     and call `IngestionService.runConnector("OPENTABLE", mode)`. Add that overload; the port is
     unchanged.
   - Watch the run to `DONE`/`FAILED` via the `onComplete` callback.
   - Stop the screencast after 10 minutes idle.
   - `InputEvent` is a sealed record: `Mouse(type, x, y, button, clicks)`, `Wheel(x, y, dx, dy)`,
     `Key(type, key, code, modifiers)`, `Text(value ≤ 256)`. Clamp coordinates; key names come
     from an allow-list.
   - Tests use the fake browser: transitions, auto-sync on auth, idle teardown, invalid input
     rejected, and a second viewer replacing the first.
6. **REST + permissions.**
   - `V52__seed_opentable_session_permission.sql` (`connectors.opentable.session`, ALL × OWNER).
   - `OpenTableSessionApplicationService` (in `application/`, calls `PermissionService.require`).
   - `OpenTableSessionController` (in `api/`): `POST/DELETE /api/connectors/opentable/session`,
     `GET …/session/stream` (`SseEmitter`: `state` and `frame` events, a heartbeat every 15 s,
     no timeout while viewing), `POST …/session/input`.
   - Extend `ArchitectureBoundariesTest` (new packages) and `PermissionBoundaryIntegrationTest`
     (non-owner → 403 on all four endpoints).
   - Sentry needs no extra scrub: `max-request-body-size: none` already keeps request bodies out.
7. **Connector** (`OpenTableApiConnector`, `OpenTableApiClient`).
   - Requests come from spec §5.4, with spacing, backoff (5 s doubling to 90 s, 10 attempts),
     401/403 → `CONNECTOR_AUTH_FAILED`, exhausted retries → `CONNECTOR_RATE_LIMITED`, and
     non-JSON → `CONNECTOR_SCHEMA_MISMATCH`.
   - Reservations: half-month windows (FULL: from `backfillFrom` to today + 18 months;
     incremental: today − 60 days to today + 18 months). A window returning 1000 rows is split
     in half recursively; a one-day window at 1000 fails.
   - Reviews: paged until `totalCount` (FULL: from 2000-01-01; incremental: last 120 days).
   - Menu items (paged) and menus (single) are always pulled.
   - Each response goes to the sink with its `fetcherIdentity`/`sourceRecordRef`. Reservation
     windows are canonicalized right after their raw id returns.
   - Datasets run independently and failures are aggregated (copy `CtbConnector.pull/aggregate`).
   - Tests use a fake browser and in-memory sink: window splitting, 429 then success, auth loss
     mid-run keeps earlier payloads and rethrows, one dataset failing doesn't block others.
8. **Reservation parser** (`OpenTableReservationJsonParser`).
   - Mapping and walk-in rule per spec §5.5; `visitDate` is venue-local → `Instant` via
     `opentable.timezone`.
   - Tests: each status, walk-in override, a missing key throws, null guest name allowed.
   - Integration test (Testcontainers): raw rows stored; a second identical pull is a canonical
     no-op; a status change supersedes.
9. **Frontend.**
   - `Api` additions with live and demo implementations (`frontend-api-method` skill):
     `startOpenTableSession(mode)`, `openTableSessionStreamUrl()`, `sendOpenTableInput(event)`,
     `endOpenTableSession(logout)`. Demo mode shows a static placeholder frame and walks the
     states on a timer.
   - `components/connectors/opentable-sync-panel.tsx`:
     - an `<img>` updated from SSE frames, scaled with preserved aspect ratio
     - pointer events mapped to remote coordinates
     - a focusable surface capturing keys (printable characters and paste go via `Text`)
     - a state banner ("Sign in to OpenTable" / "Signed in, syncing; you can close this" /
       done or failed with the run link)
     - Incremental/Full toggle and a Sign out button
   - Data health: a **Sync OpenTable** action on the OpenTable row. Tests: state rendering,
     coordinate mapping, demo flow.
10. **Docs.**
    - `docs/connectors/opentable.md`: rewrite with the assisted flow, endpoints, findings, and
      the CSV fallback.
    - `source-access.md` OpenTable row.
    - `system-context.md` ingestion-reality row (assisted session, not a manual export).
    - `matching-and-identity.md`: API source identity, status mapping, walk-in rule.
    - `operations/deployment.md`: sidecar, network, profile volume excluded from backups,
      session ceiling measurement.
    - `superpowers/README.md` index row.
11. **Manual smoke** (not in CI; on the dev stack first):
    - The sidecar is up and CDP is reachable from backend only (`docker run --rm --network
      goldys-prod_default curlimages/curl` fails).
    - The live view renders and typing works.
    - Signing in triggers an auto-sync. The run completes with payload counts close to the
      2026-10-13 extraction: 24,170 reservations to that date, 1,216 reviews, 2,002 items.
    - A re-run within 30 minutes needs no login.
    - Logout clears the profile.
    - Logs contain no input or response content.
