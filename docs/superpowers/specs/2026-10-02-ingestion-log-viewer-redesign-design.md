# Ingestion Log Viewer Redesign

**Status:** In review
**Date:** 2026-10-02
**Scope:** Move the connector logs from a header drawer to a sidebar page, render them as a
table, and reveal/copy the full backend stack trace on demand.

## 1. Objective

Rework the connector "Logs" surface from a header drawer (cards + display text) into a
sidebar page under "Data health" that lists runs as a table, and — when a row is clicked
— reveals the **full backend stack trace** for that failure, with the copy action copying
that stack trace rather than a formatted summary.

## 2. Sources of Truth and Baseline

- `docs/superpowers/specs/2026-10-02-ingestion-failure-feedback-design.md` — the current
  "Logs" drawer (Approach A: latest-failure detail on the connector DTO).
- `backend/.../ingestion/IngestionFailure.java` — `failureType`/`detail`/`occurredAt`;
  no stack trace.
- `backend/.../ingestion/ConnectorRunner.java` — classifies `ConnectorFetchException` vs
  unclassified `RuntimeException`; for the latter, stores only `e.getClass().getName()`
  (deliberately, to avoid leaking tokens).
- `backend/.../api/ConnectorStatusController.java` — `ConnectorStatusDto` +
  `FailureDetailDto(type, message, at)`.
- `frontend/components/logs/logs-drawer.tsx` — the current header drawer (to be removed).

**Baseline finding:** a failure's stack trace is never captured or stored, so the "backend
logs" the user wants to reveal don't exist yet. Storing full stack traces is a deliberate
reversal of the existing token-leakage guard (unclassified faults currently record only the
exception class). This is accepted because the operator who runs connectors already holds
those secrets, and the ledger is append-only and trusted.

## 3. Scope

### In scope

- Store the full stack trace per failure and expose it on the connector API.
- A sidebar "Logs" page (under "Data health") with a table of runs.
- Click-to-expand a row to reveal the stack trace; copy the stack trace (per row + all).

### Out of scope

- Approach B (a run history endpoint) — still deferred.
- Capturing the raw connector HTTP response bodies (only the stack trace is added).
- Pivoting the CTB connector to the standard CSV export (separate connector work).
- A general server-side log store beyond the ingestion ledger.

## 4. Backend — store and expose the stack trace

- Migration `V14__ingestion_failure_stack_trace.sql`: `ALTER TABLE ingestion_failure ADD
  COLUMN stack_trace text;` (nullable).
- `IngestionFailure`: add `stackTrace` (nullable) + record it.
- `IngestionRunService.recordFailure(...)`: accept a `stackTrace` argument and store it.
- `ConnectorRunner`: build the stack trace with a `stackTraceOf(Throwable)` helper
  (`StringWriter` + `printStackTrace`) for both the `ConnectorFetchException` and the
  unclassified `RuntimeException` branches.
- `FailureDetail`: add a nullable `stackTrace` field.
- `IngestionService.latestFailure`: include the failure's `stackTrace`.
- `ConnectorStatusController`: add `stackTrace` to `FailureDetailDto` (nullable).

## 5. Frontend — sidebar page + table + drill-down

- Remove `frontend/components/logs/logs-drawer.tsx` and its header button (in
  `app-header.tsx`).
- Add a sidebar nav item **"Logs"** in the "Data" group, directly under "Data health"
  (route `/logs`).
- New `frontend/app/(app)/logs/page.tsx`: a table — columns **Source · Connector · Status ·
  Last run · Failure** — one row per source (from `listConnectorStatuses()`).
- Each row is expandable (click → reveal the full `failure.stackTrace` in a `<pre>`), with
  a per-row **copy** button that copies the stack trace, and a "Copy all" that copies every
  row's stack trace joined together. Rows with no failure show "—".

## 6. Testing

- **Backend unit:** `stackTraceOf` formats a throwable; `recordFailure` persists the stack
  trace.
- **Backend integration (PostgreSQL):** a failed run's latest failure carries the stack
  trace; `GET /api/connectors` returns `failure.stackTrace`.
- **Frontend (Vitest):** the table renders sources + failure summary; clicking a row reveals
  the stack trace; copy writes the stack trace text.

## 7. Decisions Recorded

- Sidebar page (not a drawer), table format, click-to-expand, copy = stack trace.
- Store + expose the full stack trace (token-leakage tradeoff accepted).
- No new endpoint — the stack trace rides on the existing `failure` detail.

## 8. Open Questions

- Retention/size cap on stored stack traces (none for now; a single failure's trace is small).
