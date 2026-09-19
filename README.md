# Goldy's Unified Data Platform

Data integration and analytics platform for Goldy's, with automated
reconciliation, dashboards, reporting, exports, and AI-assisted insights.

The implementation has been cleared for a deliberate Phase 1 rebuild. The
repository retains build configuration, application configuration, the V1
Flyway baseline, and product/design context. Follow the approved
[Phase 1 specification](docs/superpowers/specs/2026-09-19-phase-one-mvp-design.md)
and [foundation implementation plan](docs/superpowers/plans/2026-09-19-phase-one-foundation.md);
previously documented classes and screens no longer exist unless a later commit
restores them.

Read [`docs/system-context.md`](docs/system-context.md) for architecture
invariants and [`docs/prd.md`](docs/prd.md) for scope and acceptance criteria
before implementing connectors, reconciliation, or reporting behavior.

## Layout

- `backend/` — Java 25, Spring Boot 3.5. The Phase 1 foundation is in place:
  byte-faithful ingestion ledger with append-only raw records and first-class
  run/failure tracking, a vendor-neutral connector port, OIDC-to-staff-profile
  session mapping with a table-driven permission service, and bitemporal
  canonical sale-item/shift persistence with raw provenance. Schema is owned by
  Flyway migrations V1–V4.
- `frontend/` — Next.js (App Router) + TypeScript + Tailwind + shadcn/ui, run
  with Bun 1.4.2. A minimal application shell is restored; reconciliation and
  connector screens land with later vertical slices. See
  `docs/design-system.md` for retained frontend decisions.
- `docker-compose.yml` — local Postgres 16 for dev, published on host port
  **5433** so it does not collide with a native Postgres on 5432.

## Verification

See [`docs/testing.md`](docs/testing.md) for the reproducible commands. Summary
of what was verified on this tree:

- `backend`: `./gradlew test spotlessCheck build` passes. Integration tests run
  against real PostgreSQL 16 via Testcontainers (never H2): byte round-trip and
  append-only rejection, run status derivation (SUCCESS/PARTIAL/FAILED/
  NO_NEW_DATA), connector partial-failure, OIDC NOT_PERMITTED, table-driven
  permissions, and canonical idempotency/as-of/concurrent-supersession.
- `backend`: the schema is owned by Flyway (`src/main/resources/db/migration`)
  with `hibernate.ddl-auto: validate`. `V1__baseline_schema.sql` is immutable
  history; V2–V4 add the ingestion ledger, staff profiles, and canonical
  provenance. Migrations assume an empty rebuild database.
- `backend`: builds run on Gradle 9.5.0 via the committed wrapper, pinned
  deliberately (Gradle itself, not just the toolchain, must parse Java 25 class
  files). `spotlessApply` formats Java; `spotlessCheck` is the CI-side equivalent.
- `frontend`: `bun run typecheck`, `bun run lint`, and `bun run build` all pass
  with Bun 1.4.2; the root route renders with a clean console.
- `docker-compose.yml`: `docker compose config` validates (Postgres 16 on host
  5433). `docker compose up` and live OIDC login were **not** exercised in the
  sessions that built this foundation; confirm both before treating the
  foundation as deployable.

Two inputs gate their dependent Phase 1 behavior: the **entity-matching
strategy** (what identifies "the same event" across sources) and the
**field-to-role permission mapping** (which fields each department × seniority
combination can see). The foundation may implement the mechanisms, but must not
guess matching tolerances, permission seed data, or protected field rendering.
