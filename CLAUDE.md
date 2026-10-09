# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read this first

Goldy's Unified Data Platform ingests a pub's operational systems (Lightspeed POS,
Cooking the Books, OpenTable, Deputy) into an immutable raw ledger, maps them to
bitemporal canonical facts, reconciles disagreements into resolved views, and serves
dashboards, reports and an AI assistant ("Ask Goldy's") from those resolved views.
Phase 1 and most of Phase 2 are built and deployed; see the root `README.md` for what
exists today.

Before writing feature code, read:

1. `docs/system-context.md`: architecture invariants, tech stack, the three-layer
   data model, per-source ingestion reality. **Source of truth for architecture.**
2. `docs/prd.md`: phased requirements, acceptance criteria, non-goals and open
   questions. Scopes the architecture; never overrides it.
3. `docs/architecture/current-state.md`: how the code is organised today (packages,
   allowed dependencies, authorization placement, widget/dashboard runtime).
4. The doc for the area you're touching. `docs/README.md` is the full index
   (connectors, metrics, adding a domain, frontend design, operations).

`docs/PRODUCT.md` is a non-normative summary. Never cite it as the basis for an
acceptance criterion or architectural decision; if it conflicts with the docs above,
it's stale.

`docs/superpowers/specs/` and `plans/` are dated, point-in-time design records
(indexed in `docs/superpowers/README.md`). Use them for the reasoning behind a
feature, not as a description of current code.

For backend work, `.claude/rules/architecture-invariants.md` (loaded automatically)
has the detailed invariants: raw-ledger immutability, bitemporal supersession,
resolved-only read direction, table-driven permissions, connector isolation, the AI
tool boundary. For connector work, use the `new-connector` skill.

**Open questions gate their dependent behaviour** (tracked in `docs/prd.md`): the
field-to-role permission mapping and the full list of department/seniority values.
Until they're answered, seed new permission resources for `ALL × OWNER` only and
don't guess BOH/FOH grants. Entity matching is documented per entity in
`docs/connectors/matching-and-identity.md`; extend that doc when you add a source or
entity, don't invent tolerances in code.

## Build & test

Backend (from `backend/`):
- `./gradlew compileJava`. Use the committed wrapper, not a local Gradle install.
  **The wrapper is pinned to Gradle 9.5.0, not an 8.x line, on purpose**: Gradle
  itself (not just the Java toolchain it targets) must run on a JVM that can parse
  Java 25 class files, and Gradle 8.x fails to even parse the build script on a
  JDK 25-only machine. Don't "fix" the wrapper version down to 8.x.
- `./gradlew test` runs the suite. Integration tests (`*IntegrationTest`) run against
  real PostgreSQL 16 via Testcontainers (`support/PostgresContainerConfiguration`),
  so Docker must be reachable. Don't substitute an in-memory database.
- `./gradlew spotlessApply` formats Java (google-java-format via Spotless). The
  `.claude/settings.json` hook runs it after every Java edit; `spotlessCheck` is the
  CI-side equivalent.
- `ArchitectureBoundariesTest` enforces package dependency rules. Extend it when
  adding a package or domain rather than working around it.
- Schema changes go in `src/main/resources/db/migration` as the next `V<n>__*.sql`
  (Flyway). Never edit an applied migration; add a corrective one.

Frontend (from `frontend/`, Bun only, no npm/yarn lockfiles):
- `bun install`, `bun run dev`, `bun run build`, `bun run typecheck`, `bun run lint`
  (plain ESLint via `eslint.config.mjs`; `next lint` is deprecated and was removed),
  `bun run test` (Vitest). CI doesn't run `bun run test` yet, so run it locally.
- Don't run `build` and `typecheck` concurrently in one checkout; they race on
  `.next/types`.

Local stack: `docker compose up -d` starts Postgres 16 on host port **5433**. Full
details are in `docs/operations/testing.md` and `docs/operations/deployment.md`.

## Code style

Favor conventional, idiomatic Java/TypeScript over clever or terse solutions.
Comment non-obvious decisions in place, especially bitemporal query logic and
reconciliation rule evaluation, rather than relying on the code being
self-explanatory later. This codebase is built primarily by LLM sessions with a
human collaborator joining later.

## Gotchas

- `application.yml` sets `hibernate.ddl-auto: validate` on purpose. Adding a field
  means writing a migration, not letting Hibernate alter the table. A database
  created before Flyway existed has to be dropped and recreated.
- `raw_record.payload` (JSONB, from V1) is derived. The authoritative bytes are
  `payload_bytes` (`BYTEA`) with `payload_sha256` and `payload_byte_length` (V2).
  Never treat JSONB as the audit source.
- Before assuming a source has a clean REST API, check `system-context.md`'s
  "Per-Source Ingestion Reality" table and `docs/connectors/source-access.md`.
  Lightspeed arrives via scheduled-report webhooks, CTB via an internal-AJAX pull
  plus an SFTP drop, OpenTable via a manual CSV drop, and Deputy via a raw-only
  webhook.
- Auth is email + password (`user_account`, V7). Leftover OIDC references
  (`.env.oidc.example`, `/oauth2` rewrites in `frontend/next.config.ts`, some
  comments) are dead and shouldn't be extended.
- The frontend has a demo mode (`NEXT_PUBLIC_DEMO_MODE`, default on) backed by
  fixtures in `frontend/lib/api/demo.ts`. Every new `Api` method needs both a live
  and a demo implementation.
