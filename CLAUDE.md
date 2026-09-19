# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read this first

The implementation has been cleared for a deliberate Phase 1 rebuild. The
repository retains build configuration, application configuration, the V1
Flyway baseline, and product/design context. Follow the approved Phase 1 spec
and implementation plans; previously documented classes and screens no longer
exist unless a later commit restores them.

Before writing feature code, read these documents in order:

1. `docs/PRODUCT.md` — plain-language overview. **Non-normative** — never cite
   it as the basis for an acceptance criterion or architectural decision; if
   it conflicts with the docs below, it's stale.
2. `docs/system-context.md` — architecture invariants, tech stack, the
   three-layer data model. **Source of truth for architecture.**
3. `docs/prd.md` — phased requirements with acceptance criteria. Scopes and
   sequences the architecture doc's decisions; never overrides them.
4. `docs/superpowers/specs/2026-09-19-phase-one-mvp-design.md` — approved
   Requirements 1–7 design.
5. `docs/superpowers/plans/2026-09-19-phase-one-foundation.md` — current
   foundation implementation sequence and verification gates.

**Two open questions gate their dependent behavior** (tracked in
`docs/prd.md`): the entity-matching strategy and the field-to-role permission
mapping. The foundation may implement shared mechanisms, but must not guess
matching tolerances, permission rows, or protected reconciliation fields.

For backend work, see `.claude/rules/architecture-invariants.md` (loaded
automatically) for the detailed invariants — raw-log immutability, bitemporal
supersession, table-driven permissions, connector isolation, restricted AI
tool access. The summary above is enough context for frontend-only or docs
work.

## Build & test

Backend (from `backend/`):
- `./gradlew compileJava` — use the committed wrapper, not a local Gradle
  install. **The wrapper is pinned to Gradle 9.5.0, not an 8.x line, on
  purpose** — Gradle itself (not just the Java toolchain it targets) must run
  on a JVM that can parse Java 25 class files, and Gradle 8.x fails to even
  parse the build script on a JDK 25-only machine. Don't "fix" the wrapper
  version down to 8.x.
- `./gradlew test` runs the current suite. PostgreSQL-specific integration
  tests use PostgreSQL 16 through the harness introduced by the foundation
  plan; do not substitute an in-memory database.
- `./gradlew spotlessApply` formats Java (google-java-format via Spotless).
  The `.claude/settings.json` hook runs it after every Java edit, so the tree
  stays formatted; `spotlessCheck` is the CI-side equivalent.
- Schema changes go in `src/main/resources/db/migration` (Flyway). Do not
  change `ddl-auto` back to `update` — it is `validate` on purpose.

Frontend (from `frontend/`, Bun only — no npm/yarn lockfiles):
- `bun install`, `bun run dev`, `bun run build`, `bun run lint` (plain ESLint
  via `eslint.config.mjs`; `next lint` is deprecated and was removed).

## Code style

Favor conventional, idiomatic Java/TypeScript over clever or terse solutions.
Comment non-obvious decisions in place — especially bitemporal query logic
and reconciliation rule evaluation — rather than relying on the code being
self-explanatory later. This codebase is built primarily by LLM sessions with
a human collaborator joining later.

## Gotchas

- The schema is owned by Flyway migrations, and `application.yml` sets
  `hibernate.ddl-auto: validate`. Adding a field means writing a new migration
  in `db/migration`, not letting Hibernate alter the table. An existing dev
  database created before Flyway existed has to be dropped and recreated.
- The V1 baseline has a JSONB `raw_record.payload`, which cannot preserve
  exact source bytes. The approved foundation adds authoritative `BYTEA`,
  digest, and length fields in a later migration; never treat derived JSONB as
  the audit source.
- Before assuming a source has a clean REST API, check `system-context.md`'s
  "Per-Source Ingestion Reality" table — most in-scope MVP sources
  (Lightspeed, CTB, OpenTable) don't.
