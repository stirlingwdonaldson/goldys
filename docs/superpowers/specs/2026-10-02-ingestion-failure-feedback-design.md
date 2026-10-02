# Ingestion Failure Feedback Design

**Status:** In review
**Date:** 2026-10-02
**Scope:** Surface connector/ingestion failure details in the UI so an operator can see
and copy what went wrong, instead of a bare failure count or a silent failure.

## 1. Objective

Make ingestion failures diagnosable from the frontend. Today a connector "Run" either
fails with an opaque count or — for CTB — fails silently, even though the backend has
already recorded the failure's type, message, and timestamp in the append-only
`ingestion_failure` ledger. This change exposes that already-stored detail over the
existing API and renders it in a "Logs" drawer with a copy affordance.

## 2. Sources of Truth and Baseline

- `docs/system-context.md` — "Ingestion failure must be observable" invariant; the
  append-only raw log + ingestion ledger.
- `backend/.../ingestion/IngestionFailure.java` — already records `failureType`,
  `detail`, `occurredAt` per run (table `ingestion_failure`).
- `backend/.../ingestion/ConnectorRunner.java` — classifies `ConnectorFetchException`
  vs unclassified `RuntimeException`; records failures in the ledger.
- `backend/.../api/ConnectorStatusController.java` — `GET /api/connectors` and
  `POST /api/connectors/{source}/run`; the DTO carries only `source`, `connectorName`,
  `lastRunAt`, `status`, `failureCount`.

**Baseline findings:**

- `IngestionService.runConnector("LIGHTSPEED")` throws `Unknown source: LIGHTSPEED`
  because no Lightspeed `SourceConnector` implements the pull port (only `CtbConnector`
  does). This is a synchronous validation error — it is thrown *before* a run starts, so
  it is **not** in the ingestion ledger and must be surfaced from the HTTP 400 directly.
- A CTB failure *is* recorded (type + message + timestamp) in `ingestion_failure`, but
  `ConnectorStatusDto` only exposes a numeric `failureCount`, so the detail never reaches
  the UI.

## 3. Scope

### In scope (Approach A)

- Expose the **latest failure detail** (`type`, `message`, `at`) per source on the
  existing connector API — no new endpoint.
- A frontend "Logs" drawer (global entry point) that shows each source's latest run
  status + failure detail, with a **copy** affordance per entry and a "copy all".
- Surface synchronous run errors (e.g. `Unknown source: LIGHTSPEED`) directly in the UI.

### Out of scope (deferred, documented — see §6)

- **Approach B:** a history endpoint listing recent runs/failures per source (e.g.
  `GET /api/connectors/{source}/runs`). Documented as future work.
- A server-side general log store beyond the existing ingestion ledger. Documented.
- Client-side persistence (localStorage) — explicitly rejected; the backend is the
  source of truth and the frontend fetches via the normal API.
- Building the missing Lightspeed `SourceConnector` (a separate bug).

## 4. Backend — surface the latest failure detail

- Add `FailureDetail(String type, String message, Instant at)` as a value type in
  `com.goldys.platform.ingestion`.
- `IngestionRunSummary` gains a nullable `FailureDetail failure` field.
- `IngestionService.runSummary(runId)` (and the `latestRunPerSource` mapping) resolves the
  latest `IngestionFailure` for that run and populates `failure`; null when the run has no
  failure. The failure therefore belongs to the **latest run** — a source whose latest run
  succeeded shows no failure, even if an older run failed (matching the existing
  latest-run-per-source status semantics). This reuses the existing
  `IngestionFailureRepository`.
- `ConnectorStatusController` maps `failure` onto `ConnectorStatusDto` as
  `failure: { type, message, at }` (null when absent). The `at` timestamp is the failure's
  `occurredAt`, ISO-8601.
- No new endpoint; `GET /api/connectors` and `POST /api/connectors/{source}/run` both
  return the richer DTO.

**Boundary note:** the synchronous `Unknown source: <src>` validation error from
`runConnector` is already a 400 with a clear message via `ApiExceptionHandler`
(`IllegalArgumentException` → `VALIDATION_FAILED`). The frontend surfaces that directly
(§5) — it needs no backend change beyond what exists.

## 5. Frontend — "Logs" drawer + copy + error surfacing

- **Types** (`lib/api/types.ts`): extend `ConnectorStatus` with
  `failure?: { type: string; message: string; at: string } | null`.
- **Logs drawer** (`components/logs/logs-drawer.tsx`): a `Sheet` opened from a "Logs"
  button in the header (next to "Ask Goldy's"). On open it fetches `GET /api/connectors`
  and lists each source's latest run — status + (when failed) the failure `type`/`message`/
  `at`. Each row has a **copy icon** (copies a formatted line via `navigator.clipboard`);
  the drawer header has a "Copy all".
- **Run error surfacing** (`components/connectors/`): when `runConnector(source)` rejects,
  show the error message (e.g. `Unknown source: LIGHTSPEED`) in the existing connectors UI
  rather than swallowing it; the returned DTO's `failure` is shown when a run completes
  failed.

No localStorage; the drawer reads live data from the backend on each open.

## 6. Future work (documented, not built now)

### 6.1 Approach B — run/failure history endpoint

A future slice should add `GET /api/connectors/{source}/runs` returning recent runs
(limit + cursor), each with its `failure` detail, so the Logs drawer can show history
rather than only the latest. This reuses the same `ingestion_run` + `ingestion_failure`
tables; it is a read-only projection over an append-only ledger, so it is low-risk and
does not need a schema change.

### 6.2 Server-side general log store

Beyond connector failures, a general event/audit log (one append-only table, keyed by
`source` + `occurredAt`, reusing the ingestion-failure pattern) would let the Logs drawer
cover other operator actions (manual overrides, rule changes, exports). Design it against
the same invariants as the raw log: append-only, `occurredAt` timestamped, operator-facing
messages only (never payload contents/credentials). The frontend fetch + drawer in §5 is
the natural consumer; the store just widens what it reads.

## 7. Testing

- **Backend unit:** `runSummary` maps the latest `IngestionFailure` into `failure`
  (and null when none); DTO serializes `failure` correctly.
- **Backend integration (PostgreSQL):** run a connector that fails (seed a
  `ConnectorFetchException`), assert `GET /api/connectors` returns the failure
  `type`/`message`/`at`.
- **Frontend (Vitest):** the Logs drawer renders each source's failure detail and the copy
  action; the connectors UI surfaces a run error message.
- **Copy:** the copy path formats and invokes `navigator.clipboard.writeText`.

## 8. Decisions Recorded

- **Approach A** now (extend the existing DTO); **Approach B** (history endpoint) deferred
  and documented.
- **No localStorage** — logs are fetched live from the backend, not persisted client-side.
- Copy affordance per entry + "copy all" in the drawer header.
- Synchronous run errors are surfaced directly (they are not in the ingestion ledger).

## 9. Open Questions

- Whether the Logs drawer should also list `DEPUTY`/`OPENTABLE` (both are in
  `KNOWN_SOURCES` but have no pull connector / run path yet) — assumed yes, shown as
  `never_run` with no failure.
- The exact retention/cap for the future history endpoint (§6.1) — left to that slice.
