# AGENTS.md

Guidance for coding agents working in this repository. This is the canonical file:
opencode reads it directly, and Claude Code reads it through `CLAUDE.md` (`@AGENTS.md`).

## Project

Goldy's Unified Data Platform ingests a pub's operational systems (Lightspeed POS,
Cooking the Books, OpenTable, Deputy) into an immutable raw ledger, maps them to
bitemporal canonical facts, reconciles disagreements into resolved views, and serves
dashboards, reports and an AI assistant ("Ask Goldy's") from those resolved views.
Phase 1 and most of Phase 2 are built and deployed; the root `README.md` lists what
exists today.

```
raw ledger → bitemporal canonical → reconciliation → resolved projections
  → semantic queries → REST API · dashboards · Ask Goldy's tools
```

## Read before writing feature code

1. `docs/system-context.md`: architecture invariants, tech stack, the three-layer
   data model, per-source ingestion reality. **Source of truth for architecture.**
2. `docs/prd.md`: phased requirements, acceptance criteria, non-goals and open
   questions. Scopes the architecture; never overrides it.
3. `docs/architecture/current-state.md`: packages, allowed dependencies,
   authorization placement, widget/dashboard runtime.
4. **For any backend change, `.claude/rules/architecture-invariants.md`**: raw-ledger
   immutability, bitemporal supersession, resolved-only read direction, table-driven
   permissions, connector isolation, the AI tool boundary. (Claude Code loads it
   automatically; other agents must open it.)
5. The doc for the area you're touching. `docs/README.md` is the full index.

`docs/PRODUCT.md` is a non-normative summary. Never cite it as the basis for an
acceptance criterion or architectural decision; if it conflicts with the docs above,
it's stale.

`docs/superpowers/specs/` and `plans/` are dated design records (indexed in
`docs/superpowers/README.md`). Use them for the reasoning behind a feature, not as a
description of current code. New design work goes there as
`specs/YYYY-MM-DD-<topic>-design.md` then `plans/YYYY-MM-DD-<topic>.md`.

## Repo skills

Skills in `.claude/skills/` encode this repo's recipes and are loaded by both opencode
and Claude Code. Use them instead of re-deriving the pattern:

| Skill | Use for |
|---|---|
| `new-connector` | A source integration: pull connector, webhook, CSV/PDF drop, SFTP |
| `add-domain` | A new resolved business domain (canonical → projector → semantic → API/tool) |
| `add-metric` | A new catalogue metric, base or derived |
| `add-reporting-tool` | A new Ask Goldy's tool |
| `add-migration` | Any schema change |
| `frontend-api-method` | A new backend call from the frontend (live + demo) |

Generic engineering and design skills live in `.opencode/skills/` (opencode only).
Where a generic skill conflicts with this repo's docs, the repo docs win.

## Open questions

These gate their dependent behaviour (tracked in `docs/prd.md`): the field-to-role
permission mapping and the full list of department/seniority values. Until they're
answered, seed new permission resources for `ALL × OWNER` only and don't guess
BOH/FOH grants. Entity matching is documented per entity in
`docs/connectors/matching-and-identity.md`; extend that doc when you add a source or
entity, don't invent tolerances in code.

## Build and test

Backend (from `backend/`):
- `./gradlew compileJava`. Use the committed wrapper, not a local Gradle install.
  **The wrapper is pinned to Gradle 9.5.0, not an 8.x line, on purpose**: Gradle
  itself must run on a JVM that can parse Java 25 class files, and Gradle 8.x fails to
  parse the build script on a JDK 25-only machine. Don't "fix" it down to 8.x.
- `./gradlew test` runs the suite. Integration tests (`*IntegrationTest`) use real
  PostgreSQL 16 via Testcontainers, so Docker must be reachable. Never substitute an
  in-memory database.
- `./gradlew spotlessApply` formats Java (google-java-format 1.28.0). Both agents run
  it automatically on edited files (opencode via its formatter config, Claude Code via
  a hook); CI runs `spotlessCheck`.
- `ArchitectureBoundariesTest` enforces package rules. Extend it when adding a package
  or domain rather than working around it.

Frontend (from `frontend/`, Bun only, no npm/yarn lockfiles):
- `bun install`, `bun run dev`, `bun run build`, `bun run typecheck`, `bun run lint`
  (plain ESLint; `next lint` was removed), `bun run test` (Vitest).
- CI doesn't run `bun run test` yet, so run it before pushing frontend changes.
- Don't run `build` and `typecheck` concurrently in one checkout; they race on
  `.next/types`.

Before finishing a task, run the checks for what you touched:
`./gradlew test spotlessCheck` and/or
`bun run typecheck && bun run lint && bun run test`.

Local stack: `docker compose up -d` starts Postgres 16 on host port **5433**. See
`docs/operations/testing.md` and `docs/operations/deployment.md`.

## Code style

Favor conventional, idiomatic Java/TypeScript over clever or terse solutions. Comment
non-obvious decisions in place, especially bitemporal query logic and reconciliation
rule evaluation. This codebase is built primarily by LLM sessions with a human
collaborator joining later.

## Gotchas

- `hibernate.ddl-auto: validate` is deliberate. Adding a field means a Flyway
  migration, never Hibernate DDL. Never edit an applied migration.
- `raw_record.payload` (JSONB) is derived. The authoritative bytes are
  `payload_bytes` (`BYTEA`) with `payload_sha256` and `payload_byte_length`.
- Check `docs/connectors/source-access.md` before assuming a source has an API.
  Lightspeed arrives via scheduled-report webhooks, CTB via an internal-AJAX pull plus
  an SFTP drop, OpenTable via a manual CSV drop, Deputy via a raw-only webhook.
- Auth is email + password (`user_account`). Leftover OIDC references
  (`.env.oidc.example`, `/oauth2` rewrites in `frontend/next.config.ts`, some comments)
  are dead; don't extend them.
- The frontend demo mode (`NEXT_PUBLIC_DEMO_MODE`, default on) uses fixtures in
  `frontend/lib/api/demo.ts`. Every `Api` method needs a live and a demo implementation.

## Safety: this VM also runs production

The agent VM hosts the production Docker stack (`docker-compose.prod.yml`, project
`goldys-prod`). Treat it accordingly:

- Never run `docker compose` against the prod stack (`-f docker-compose.prod.yml`,
  `--env-file .env.prod`) unless the user asks for that exact operation. Never run
  `down -v` on it: that deletes the production database.
- Never read, print or copy `.env.prod` or other real env files.
- Don't switch branches or reset the checkout production deploys from. Do agent work in
  a separate checkout or worktree.
- Production database access is read-only: use `default_transaction_read_only=on`
  sessions, as the production audit did.
- `opencode.jsonc` denies the most destructive of these commands; the rules above apply
  regardless.

## Git

- Never commit directly to main; create a task-specific branch.
- Keep commits atomic. Do not force push.
- Do not push, merge, deploy, or delete infrastructure without explicit approval.

## Investigation

Before major code changes:
1. inspect the relevant implementation and its tests;
2. use Ripwire when dependency or blast-radius analysis is useful;
3. explain architectural assumptions before broad refactors.
