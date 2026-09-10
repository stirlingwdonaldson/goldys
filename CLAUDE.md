# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read this first

This is an early-stage scaffold (raw log, bitemporal canonical layer, and
permission model only — no connectors, reconciliation UI, or Phase 2 modules
implemented yet). Before writing real feature code, read `docs/` in order:

1. `docs/PRODUCT.md` — plain-language overview. **Non-normative** — never cite
   it as the basis for an acceptance criterion or architectural decision; if
   it conflicts with the docs below, it's stale.
2. `docs/system-context.md` — architecture invariants, tech stack, the
   three-layer data model. **Source of truth for architecture.**
3. `docs/prd.md` — phased requirements with acceptance criteria. Scopes and
   sequences the architecture doc's decisions; never overrides them.

**Two open questions block real feature work** (tracked in `docs/prd.md`'s
Open Questions): the entity-matching strategy (what identifies "the same
event" across sources) and the field-to-role permission mapping (which
fields each department × seniority sees). Don't implement the reconciliation
UI (PRD Requirement 5) against guessed answers to either — surface the gap
instead.

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
- `./gradlew test` requires a live Postgres — run `docker compose up` from
  the repo root first (`docker-compose.yml` defines it). Without it, the one
  existing test (`PlatformApplicationTests`) fails at context-load with a
  Hibernate dialect error, not a real failure.
- No Java linter/formatter is configured yet (no Checkstyle/Spotless).

Frontend (from `frontend/`, Bun only — no npm/yarn lockfiles):
- `bun install`, `bun run dev`, `bun run build`, `bun run lint`.

## Code style

Favor conventional, idiomatic Java/TypeScript over clever or terse solutions.
Comment non-obvious decisions in place — especially bitemporal query logic
and reconciliation rule evaluation — rather than relying on the code being
self-explanatory later. This codebase is built primarily by LLM sessions with
a human collaborator joining later.

## Gotchas

- `application.yml` sets `hibernate.ddl-auto: update` — local dev only, per
  its own comment. Don't rely on it beyond that; a schema-stable phase needs
  Flyway/Liquibase instead.
- Before assuming a source has a clean REST API, check `system-context.md`'s
  "Per-Source Ingestion Reality" table — most in-scope MVP sources
  (Lightspeed, CTB, OpenTable) don't.
