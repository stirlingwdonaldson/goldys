# OpenTable Connector Design

**Status:** Approved
**Date:** 2026-09-23
**Scope:** `docs/prd.md` Requirement 4, OpenTable source only

## 1. Objective

Add the OpenTable source connector: a read-only, automated pull of reservation
data from OpenTable GuestCenter reporting, byte-faithfully through the shared
ingestion envelope, mapped into a new bitemporal canonical reservation entity.
No manual export step; failures are recorded, never silent; the source is
one-way (no write-back), per `docs/system-context.md`.

## 2. Background and Decisions

OpenTable has no self-serve API — only a gated partner program
(`docs.opentable.com`, approval required). GuestCenter reporting supports CSV
export of reservation data, but only manually; it must be automated via a
scripted browser pull. The OpenTable ↔ Lightspeed integration does **not** carry
reservation data into Lightspeed (only guest name/email/phone, notes, and party
size), so it cannot substitute for this connector — see
`docs/connectors/source-access.md`.

Decisions locked with the project owner:

1. **Mechanism:** scripted browser pull (Playwright) of GuestCenter CSV only. No
   partner-API track in this phase.
2. **Scope:** raw ingestion *plus* canonical mapping into a new
   `CanonicalReservation` entity (mirrors how `CtbConnector` both ingests raw
   JSON and canonicalizes into `CanonicalDailySales`/`CanonicalProductSales`).
3. **Data:** reservations only. No Guestbook/guest-contact ingestion (name,
   email, phone, tags) — that is a clean follow-up slice.
4. **Structure:** a self-contained `connectors/opentable/` package with a
   Playwright client local to OpenTable (no premature shared browser
   abstraction).
5. **Scheduling:** deferred to a separate cross-cutting slice. The connector
   implements `fetch()` and is schedule-agnostic; no `@Scheduled` cron exists
   today (pull connectors currently run on-demand via `IngestionService`).

## 3. In scope

- `OpenTableClient` browser-automation port + `PlaywrightOpenTableClient`.
- `OpenTableCsvParser` and `OpenTableReservation` record.
- `OpenTableConnector implements SourceConnector` (raw + canonical).
- `CanonicalReservation` bitemporal entity + input, service, ingest facade,
  repository, and Flyway migration `V11__reservations.sql`.
- `OpenTableConfig` wiring credentials from environment variables.
- Headless-Chromium provisioning in the backend runtime image.
- Fixture-driven parser and canonical integration tests.

## 4. Out of scope

- Guestbook / guest-contact ingestion.
- Reservation reconciliation (no second source produces reservations).
- Reservation reporting UI.
- The connector scheduler (separate slice, applies to CTB + OpenTable).
- OpenTable partner API.

## 5. Architecture

### 5.1 Components

New package `com.goldys.platform.connectors.opentable`, mirroring
`connectors/ctb/`:

| Class | Responsibility |
|---|---|
| `OpenTableClient` (interface) | `void login(String email, String password)`; `byte[] exportReservationsCsv(LocalDate from, LocalDate to)`. |
| `PlaywrightOpenTableClient` | Launches headless Chromium; logs in to GuestCenter; navigates to the Reservations report; sets the date filter; triggers CSV export; captures download bytes. |
| `OpenTableCsvParser` | `List<OpenTableReservation> parse(byte[] csv)` via `commons-csv`. |
| `OpenTableReservation` | Parsed reservation record (see §5.3). |
| `OpenTableConnector` | `implements SourceConnector`; `sourceSystem()` → `"OPENTABLE"`, `connectorName()` → `"opentable-guestcenter"`. |
| `OpenTableConfig` | `@Configuration` wiring Playwright + `@Value("${opentable.email:}")` / `@Value("${opentable.password:}")`. |

New canonical files, mirroring `CanonicalDailySales*`:

- `CanonicalReservation` (package-private entity, extends `BitemporalEntity`).
- `ReservationInput` (public record).
- `CanonicalReservationService` (package-private, `@Transactional`).
- `CanonicalReservationIngest` (public facade).
- `CanonicalReservationRepository` (package-private, extends `BitemporalRepository`).
- Flyway `V11__reservations.sql` (matching the new entity, since `ddl-auto: validate`).

**Deliberate deviation from CTB:** `CtbConnector` depends on the concrete
`CtbClient`. `OpenTableConnector` depends on the `OpenTableClient` *interface*
so a real browser (which cannot run in CI) is replaced by a stub in unit tests.
This is the one intentional divergence, justified by testability.

### 5.2 Data flow

`OpenTableConnector.fetch(watermark, sink)`:

1. `client.login(email, password)` — failure throws
   `ConnectorFetchException("CONNECTOR_AUTH_FAILED", …)`.
2. `byte[] csv = client.exportReservationsCsv(from, to)` — a rolling window
   (default `today − 30d` → `today + 14d`, configurable), because reservations
   are created/cancelled/moved after the fact.
3. `UUID rawId = sink.accept(new FetchedPayload(FetchMethod.SCRAPE, "text/csv", csv, UTF-8, "opentable-guestcenter"))`.
4. For each parsed row, `canonical.record(new ReservationInput(…, rawId))`.

Re-runs are idempotent: the bitemporal upsert (§5.3) creates a new version only
when a fact actually changed, so re-exporting the window is safe.

### 5.3 Canonical reservation model

`CanonicalReservation extends BitemporalEntity` — fact columns are immutable:

| Column | Type | Notes |
|---|---|---|
| `reservation_ref` | String | OpenTable reservation ID; also `sourceRecordRef` and the lock key. |
| `reservation_at` | Instant | UTC booking moment. |
| `party_size` | int | Covers. |
| `status` | String | Normalized vocabulary (provisional): `BOOKED`, `SEATED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`, `WALK_IN`. |
| `table` | String, nullable | Table name/number. |
| `source_channel` | String, nullable | Booking source (app/web/phone/walk-in). |
| `party_name` | String, nullable | Party name only — no email/phone/tags (Guestbook is out of scope). |

Identity and versioning mirror `CanonicalDailySalesService`:

- `logicalId = UUID.nameUUIDFromBytes(("reservation:" + reservationRef))`.
- Lock current row by `(reservationRef, sourceSystem)` with `PESSIMISTIC_WRITE`;
  if `sameFact`, return existing; else `supersede(now)` then insert a successor.
- `validFrom = recordedAt = now` (observation time), matching daily sales.
- No reconciliation target: reservations accumulate bitemporally.

### 5.4 Error handling

All failures are `ConnectorFetchException` (recorded by `ConnectorRunner` into
`IngestionFailure`):

- `CONNECTOR_AUTH_FAILED` — login failed (mirrors CTB).
- `CONNECTOR_BROWSER_FAILED` — Chromium/Playwright launch failed (new; makes
  "browser not installed" diagnosable).
- `CONNECTOR_FETCH_FAILED` — navigation/export/download failed.
- `CONNECTOR_SCHEMA_MISMATCH` — CSV columns don't match expectations.

## 6. Testing

- `OpenTableCsvParserTest` — TDD against a sanitized fixture CSV (the
  exact-byte pattern used for Lightspeed/CTB parsers).
- `OpenTableConnectorTest` — stub `OpenTableClient`; assert the `SCRAPE` payload
  reaches the sink, `CanonicalReservationIngest.record` is called per row, and
  failure types are classified.
- `CanonicalReservationIntegrationTest` — Testcontainers Postgres: upsert
  idempotency, supersede-on-change, versioning (mirrors
  `CanonicalDailySalesIntegrationTest`).
- No live-browser test in CI — the real GuestCenter flow is exercised by a
  one-off scripted smoke run.

## 7. Risks and prerequisites

1. **Headless Chromium in the runtime image** — `backend/Dockerfile` runs
   `eclipse-temurin:25-jre` with no browser. Playwright needs Chromium plus OS
   libraries (libnss3, libatk, fonts, …). This is the largest infra change and
   requires its own task.
2. **Discovery first** — the exact GuestCenter Reservations CSV columns, status
   vocabulary, and export trigger/download mechanics are unconfirmed. Per the
   repo's "confirm the actual working path" rule, a discovery task (scripted
   export → capture a sanitized sample → pin columns) precedes the parser.
3. **No scheduler** — out of scope here; see §4.

## 8. Success criteria

- A scripted run pulls GuestCenter reservations, persists the CSV byte-faithfully
  as a `SCRAPE` payload tagged `OPENTABLE`/`opentable-guestcenter`, and maps rows
  into `CanonicalReservation`.
- A failed run records a classified failure (never looks like "no new data").
- Re-runs are idempotent; changed reservations produce bitemporal successor
  versions, not duplicates.
- Parser and canonical behavior are covered by fixture/integration tests that run
  without a live browser.
