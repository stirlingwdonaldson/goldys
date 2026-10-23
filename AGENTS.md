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

## Real data before design

Before writing a spec or connector for any ingestion/integration, get a real sample
(or its exact schema) and prove the access path end-to-end in a few calls. If you
can't, say so and stop — never invent a fixture and call it "discovery". A
parser/extractor task is done only with a hit-rate report on real samples. Where each
source is reached is documented in `docs/connectors/source-access.md`.

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

Generic methodology (brainstorming, TDD, spec/plan, review) comes from the pinned
Superpowers plugin, not from repo skills. Where a skill conflicts with these docs, the
docs win.

## Ponytail (lazy senior dev mode)

Every agent in this repo also follows the Ponytail ruleset: write the smallest change
that fully solves the task, and end each reply with what you skipped or did not check,
and any risk the user must know.

### Before you write

Read the task and the code it touches. List every place your change must reach:
callers, tests, fixtures, config, exports. Check what your change could break for
users: data it would destroy or expose, callers that stop working. That is scope.
Extra features are not.

### The smallest complete change

Take the first option that fully works:

1. Does it need to exist? Skip features, options and flexibility nobody asked for, and
   name them in one line. A vague request gets the smallest version that does the core
   job.
2. Already in this codebase (a helper, component, service, pattern)? Use it the way the
   surrounding code does.
3. Standard library or a platform feature? Use it, unless the project has its own.
4. An installed dependency? Use it. Never add a dependency for a few lines.
5. Can it be one line a reader gets at a glance? One line.
6. Otherwise: the minimum code that works.

- Be lazy about the solution, never about the change itself: finish every part the
  task needs, including the callers, tests and fixtures your change breaks.
- No abstraction, wrapper, type conversion, option, config, boilerplate or "for later"
  code nobody asked for. Keep values in the form the platform already gives you.
  Deletion beats addition. Keep the structure the codebase already has.
- The shortest working diff wins. A one-liner that needs decoding is not short.
- Comment only the why the code cannot show, in one line.
- New non-trivial logic (a branch, a loop, a parser, money or security, or a whole new
  script or app) leaves one small test or an assert-based self-check. Trivial changes
  need none.
- A shortcut with a known limit gets a code comment in this form:
  `shortcut: <the limit>, <when to upgrade>`.

Never cut: validation at trust boundaries, error handling that prevents data loss,
security, accessibility, anything the user asked for.

### Precedence

Ponytail takes precedence. Where this file's guidance (the Superpowers methodology, the
"read before writing feature code" list, build/test breadth) conflicts with Ponytail's
smallest-complete-change rule, Ponytail wins.

Exception: the hard rules under "Safety: this VM also runs production" and the
architecture invariants (`.claude/rules/architecture-invariants.md`) still bind.
Ponytail never cuts security, trust-boundary validation, or data-loss handling, so
those do not conflict with it.

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
  in-memory database. For a Docker-free fast lane while iterating, use
  `./gradlew fastTest` (see "Fast inner loop vs. full gate" below).
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

### Fast inner loop vs. full gate

The full gates are slow (backend `test` ≈2 min; frontend `build` ≈80 s), so don't run
them on every edit — running them repeatedly is the main tax on an agentic loop. While
iterating, run the fast lane for the side you touched:

- Backend: `./gradlew fastTest` (unit tests only — no Docker, ~15 s vs ~2 min), or a
  targeted run such as `./gradlew test --tests 'com.goldys.platform.<package>.*'`.
- Frontend: `bunx vitest run path/to/file.test.tsx`, or `bun run test:watch`.

Gradle's build and configuration caches are enabled, so repeating `./gradlew test`
with no source change is a no-op (under 2 s); only an actual change re-runs the full
suite.

Before finishing a task, run the full gate for what you touched:
`./gradlew test spotlessCheck` and/or
`bun run typecheck && bun run lint && bun run test && bun run build`.

Done means verified: run those gates locally, push, then `gh pr checks --watch` until
green. The completion report lists only what you actually ran and saw — branch, SHA,
PR, CI result, and untested areas. Never say "all green" or "everything merged"
without the command output from this session.

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
- Auth is email + password (`user_account`); OIDC was removed by `V7`/`V8` — don't reintroduce it.
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

## Session start and state

This repo is a shared, multi-agent checkout; its git state moves between sessions.
Before branching, merging, or answering any "what's the current state" question, run:

```bash
git fetch --prune && git status -sb && git log --oneline -5 origin/main && gh pr list --state all -L 10
```

## Git

- Never commit directly to main; create a task-specific branch.
- `main` requires a pull request before merging; all merge types are allowed. Never
  offer or run "merge locally".
- Keep commits atomic. Do not force push.
- Do not push, merge, deploy, or delete infrastructure without explicit approval.

## Investigation

Before major code changes:
1. inspect the relevant implementation and its tests;
2. use Ripwire when dependency or blast-radius analysis is useful;
3. explain architectural assumptions before broad refactors.
