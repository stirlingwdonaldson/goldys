---
name: new-connector
description: Scaffold a new SourceConnector implementation (Lightspeed, Cooking the Books, OpenTable, Deputy) following this repo's connector-isolation pattern. Use when asked to build, add, or start a connector for one of the PRD's Phase 1 sources.
---

Goldy's platform ingests from four in-scope Phase 1 sources (PRD Requirement
4): **Lightspeed**, **Cooking the Books (CTB)**, **OpenTable**, **Deputy**.
Each becomes one class implementing `com.goldys.platform.connector.SourceConnector`.

## Before writing code

1. Read `docs/system-context.md`'s "Per-Source Ingestion Reality" table for
   the source you're building. Do not assume a clean REST API exists —
   confirm the actual working path:
   - **Lightspeed**: authenticated back-office scrape of server-rendered
     pages (working path today). Only use the REST API if the paid tier is
     confirmed unlocked — don't block on it.
   - **Cooking the Books (CTB)**: self-serve Custom Invoice Export
     (CSV/XLSX) via scheduled SFTP or email — the standard export only.
     Do not build against CTB's internal-endpoint scrape for production
     ingestion (schema-discovery use only). CTB is owned by Quantaco, a
     competitor — don't request fields beyond the standard export.
   - **OpenTable**: scripted browser pull from GuestCenter reporting. Must be
     automatable — not a manual export step.
   - **Deputy**: standard OAuth REST API. Confirm the OAuth client is
     registered (handled by the project owner) before starting — but don't
     chase ownership of that task.
2. Check `docs/prd.md` Requirement 4's acceptance criteria for this
   connector before considering it done.

## Implementation pattern

1. Add a new class in `backend/src/main/java/com/goldys/platform/connector/`
   (or a source-specific subpackage) implementing `SourceConnector`:
   - `sourceSystem()` returns the matching `SourceSystem` enum value (add one
     to `raw/SourceSystem.java` only if it's genuinely missing — the four
     Phase 1 sources plus `MANUAL_ENTRY` already exist; don't add `TENZO`,
     it's explicitly out of scope for both phases).
   - `fetch()` pulls new/changed records since the last successful run,
     writes each one to `RawRecordRepository` via a `RawRecord` using the
     correct `FetchMethod` (`SCRAPE`, `CSV_EXPORT`, or `API_JSON`), and
     returns an `IngestionRunResult`.
2. **Every raw payload goes through the shared envelope** — source system,
   fetch method, content type, fetched-at, fetcher identity all non-null. No
   source-specific staging table, no bypassing `RawRecordRepository`.
3. **Report failures, don't swallow them.** Any auth failure, timeout,
   partial fetch, or schema mismatch writes an `IngestionFailure` (via
   `IngestionFailureRepository`) with a `failureType`, `detail`, and
   `occurredAt` — then still return `IngestionRunResult.failure(detail)`.
   A failed run must never look identical to "no new data."
4. Don't map raw records into canonical entities as part of the connector
   itself unless the task explicitly asks for that — check whether that
   mapping step already exists or is a separate concern before adding it
   here.
5. Follow `.claude/rules/architecture-invariants.md`'s connector section —
   if implementing this connector would require changing the
   `SourceConnector` interface itself, stop and flag it rather than widening
   the port for one vendor's quirk.

## After implementing

- Confirm against Requirement 4's acceptance criteria: scheduled success
  writes correctly-tagged raw records; failure writes a failure state, not
  silence; the source's native format passes through the same envelope as
  every other source.
- If this is one of the first two connectors producing overlapping canonical
  entities, note that Requirement 5 (reconciliation UI) still can't be built
  against them until the entity-matching strategy (open question in
  `docs/prd.md`) is resolved.
