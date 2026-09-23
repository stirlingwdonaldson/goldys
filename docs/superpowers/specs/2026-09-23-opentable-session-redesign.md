# OpenTable Connector Session Redesign

**Status:** Approved
**Date:** 2026-09-23
**Scope:** `backend/src/main/java/com/goldys/platform/connectors/opentable/` and deployment

## 1. Objective

Rework the OpenTable connector's browser automation so it can reliably pull
GuestCenter reservations from a residential deployment, after the discovery
that GuestCenter's login is gated by an invisible hCaptcha and a three-stage
flow (GuestCenter → Okta → Okta password + hCaptcha). The connector will reuse
a persistent authenticated session instead of re-logging-in every run, log in
with human-like pacing only when the session is missing or expired, drive a
Fortress stealth Chromium engine over CDP for a stable fingerprint, and surface
a clear "manual re-auth needed" state instead of failing silently.

## 2. Background and Decisions

Discovery (2026-09-23) established that the real GuestCenter login is not a
single email+password form:

1. `guestcenter.opentable.com/login` → `input[name=email]` + "Continue".
2. Redirect to Okta OAuth (`restauth.opentable.com/oauth2/...`) →
   `input[name=identifier]` → "Next".
3. `input[type=password]` → "Sign in", gated by an **invisible hCaptcha**.

The hCaptcha blocks naive headless automation (fingerprint + IP + behavioral
scoring). A datacenter-IP test of the Fortress stealth engine confirmed it
clears the fingerprint but the datacenter IP still triggers the challenge. The
production deployment is a residential household IP, which materially lowers
the IP-reputation signal; the remaining controllable lever is behavioral
scoring plus session reuse.

Decisions locked with the project owner:

1. **Session acquisition:** auto-login with human-like pacing, with a manual
   session-handoff fallback.
2. **Browser engine:** Fortress stealth Chromium, run as a **sidecar
   container**; the backend connects over CDP via a configurable URL.
3. **Session mechanism:** Playwright `storage_state` (cookies + localStorage)
   managed by the backend at a configurable path. The manual fallback is the
   same file: a human captures a valid session and drops it at that path.

## 3. In scope

- `OpenTableClient` interface change: `login(email, password)` → `authenticate()`.
- `FortressOpenTableClient` (replaces `PlaywrightOpenTableClient`): CDP
  connection, `storage_state` load/save, session validation, three-stage paced
  login, report export.
- `OpenTableConnector` and `OpenTableConfig` updates.
- Behavioral-pacing helper (typed input, mouse movement, randomized delays).
- New failure classifications (`CONNECTOR_BROWSER_FAILED` for unreachable
  Fortress; `CONNECTOR_AUTH_FAILED` carries a "manual re-auth" hint).
- Deployment: Fortress sidecar in docker-compose, backend config + volume for
  the session file, `.env.example` updates.

## 4. Out of scope

- The GuestCenter CSV **schema** confirmation (column names, date/time format,
  status vocabulary) — still pending a manual CSV export and tracked separately
  against `OpenTableCsvParser` / the fixture.
- Captcha *solving* / bypass services; the design only lowers the risk score and
  reuses sessions, it does not solve a visible challenge.
- Reservation canonical model, reconciliation, or UI (unchanged).
- The connector scheduler (unchanged).

## 5. Architecture

### 5.1 Components

| File | Change |
|---|---|
| `OpenTableClient` (interface) | `authenticate()` replaces `login(...)`; keep `exportReservationsCsv(LocalDate, LocalDate)` and `close()`. |
| `FortressOpenTableClient` | New impl (replaces `PlaywrightOpenTableClient`). |
| `OpenTableConnector` | `fetch()` calls `client.authenticate()`; drops credentials from its own constructor. |
| `OpenTableConfig` | Wires CDP URL, session path, credentials, timezone, window. |
| `docker-compose.yml`, `docker-compose.prod.yml` | Add `fortress` sidecar; backend env + volume. |
| `.env.example` | `OPENTABLE_FORTRESS_CDP_URL`, `OPENTABLE_SESSION_PATH`. |

`OpenTableCsvParser`, the fixture, and the canonical layer are untouched.

### 5.2 Session lifecycle

`authenticate()`:

1. If `storage_state` exists at the session path, load it into a context and
   validate by navigating to the reservations report; if the report loads (not
   redirected to `/login`), reuse the session and return.
2. Otherwise (missing/expired): run the three-stage auto-login (§5.3) and save
   the resulting `storage_state` back to the session path.
3. If login fails (hCaptcha challenge or bad credentials), throw
   `ConnectorFetchException("CONNECTOR_AUTH_FAILED", ...)` with a message naming
   the session path and "manual re-auth may be required".

Manual fallback: a human captures a valid session (one-off headed script) and
writes it to the session path; the connector loads and reuses it without
attempting auto-login. No separate code path — the session file is the single
interface.

### 5.3 Login flow (corrected, three-stage, paced)

`input[name=email]` → "Continue" → `input[name=identifier]` → "Next" →
`input[type=password]` → "Sign in" (invisible hCaptcha) → wait for redirect to
GuestCenter → save session. All input is paced (§5.4).

### 5.4 Behavioral pacing

An internal helper replaces instant `fill()`/`click()`:

- typed input at ~30–80ms/character,
- `mouse.move()` in steps between fields,
- randomized 300–1500ms pauses between actions.

### 5.5 Failure classification

- `CONNECTOR_AUTH_FAILED` — login blocked (hCaptcha/bad credentials) or session
  expired; message includes the "manual re-auth" hint.
- `CONNECTOR_BROWSER_FAILED` — Fortress CDP unreachable / launch failed.
- `CONNECTOR_FETCH_FAILED` — report navigation/export/download failed.
- `CONNECTOR_SCHEMA_MISMATCH` — unchanged (parser).

## 6. Deployment

- `fortress` sidecar: `tilion/fortress`, CDP on `:9222`, no host port (internal
  only).
- Backend: `OPENTABLE_FORTRESS_CDP_URL=http://fortress:9222`,
  `OPENTABLE_SESSION_PATH=/app/data/opentable-session.json` on a mounted volume
  (gitignored, restricted permissions — it holds live cookies).

## 7. Testing

- `OpenTableConnectorTest` — updated for `authenticate()` (Mockito `OpenTableClient`).
- Pacing helper — extracted and unit-tested for non-timing logic.
- `FortressOpenTableClient` (CDP, session, login) — not CI-runnable; covered by
  an opt-in live smoke test following the existing `PlaywrightSmokeTest`
  env-gated pattern.

## 8. Risks

1. **hCaptcha may still challenge** even with residential IP + pacing + Fortress;
   this is mitigated, not eliminated. The manual fallback is the safety net.
2. **Session expiry** — Okta/GuestCenter sessions lapse; the connector must
   detect it and either re-auth or surface `CONNECTOR_AUTH_FAILED`.
3. **Fortress dependency** — a third-party stealth engine as a sidecar; version
   and availability risk, tracked separately from the backend.
4. **Schema still unconfirmed** — the parser is unchanged pending the CSV
   export; this redesign does not touch it.
5. **Report/export selectors provisional** — the reservations report URL and
   the CSV-export trigger were never confirmed behind login; the implementation
   plan carries a discovery step for the authenticated report flow.

## 9. Success criteria

- A scheduled run reuses a valid session without re-login; re-login happens only
  on expiry, with human-like pacing, over the Fortress CDP engine.
- A stored session is loaded/saved at the configured path; a manually-provided
  session is honored.
- Failure to authenticate surfaces `CONNECTOR_AUTH_FAILED` (never "no new
  data"); unreachable Fortress surfaces `CONNECTOR_BROWSER_FAILED`.
- The connector passes its unit tests via a mocked `OpenTableClient`.
