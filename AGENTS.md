# Project Instructions

## Project
Goldy's Unified Data Platform: ingests a pub's operational systems (Lightspeed,
Cooking the Books, OpenTable, Deputy) into an append-only raw ledger, maps them to
bitemporal canonical facts, reconciles conflicts into resolved views, and serves
dashboards, reports and the "Ask Goldy's" AI assistant from those views.

`CLAUDE.md` holds the full agent guidance (reading order, build commands, gotchas)
and applies to every agent, not just Claude Code. Read it first.

## Architecture
- `backend/`: Java 25 / Spring Boot 3.5 modular monolith on PostgreSQL 16 (Flyway).
  Package map and dependency rules: `docs/architecture/current-state.md`.
  Invariants: `docs/system-context.md` and `.claude/rules/architecture-invariants.md`.
- `frontend/`: Next.js 15 App Router + TypeScript + Tailwind + shadcn/ui, Bun only.
  Design: `docs/design/`.
- Docs index: `docs/README.md`.

## Development
- Use existing conventions before introducing new ones.
- Run relevant tests after modifications (`./gradlew test spotlessCheck` in
  `backend/`; `bun run typecheck && bun run lint && bun run test` in `frontend/`).
- Do not push, merge, deploy, or delete infrastructure without explicit approval.
- Prefer small, reviewable changes.

## Git
- Never commit directly to main.
- Create a task-specific branch.
- Keep commits atomic.
- Do not force push.

## Investigation
Before major code changes:
1. inspect relevant existing implementation;
2. inspect related tests;
3. use Ripwire when dependency or blast-radius analysis is useful;
4. explain architectural assumptions before making broad refactors.

## Agent tooling in this repo
- `.claude/`: Claude Code settings, the backend invariants rule, and the
  `new-connector` skill.
- `.opencode/`: opencode agents, commands and general-purpose skills (vendored
  engineering-practice skills plus `impeccable` for frontend design). They're
  generic; where they conflict with this repo's docs, the repo docs win.
- Design records produced by the superpowers workflow go in `docs/superpowers/`
  (`specs/` then `plans/`, dated `YYYY-MM-DD-<topic>`).
