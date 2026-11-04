# Agent guidance

Goldy's serves the current pub's owners and managers with trustworthy reporting,
forecasts and operational analysis. Weekly reporting and exports lead delivery.
Data flows raw ledger → bitemporal canonical → reconciliation → resolved projections
→ semantic queries → API, dashboards and Ask Goldy's.

## Authority and task routing

- Scope/outcomes: `docs/prd.md`. Behaviour/acceptance: `docs/srs.md`.
- Implementation overview: `docs/app-state.md`; code establishes current capability,
  not operational acceptance. Outstanding work: GitHub issues, indexed by `docs/roadmap.md`.
- Backend work: read `.claude/rules/architecture-invariants.md` and the relevant
  `docs/architecture/current-state.md` section. `docs/system-context.md` owns architecture.
- Source work: read the relevant `docs/connectors/source-access.md` and
  `matching-and-identity.md` sections. Prove real sample/schema access before design;
  stop dependent work if unavailable. Parsers need measured hit rates on real samples.
- UI work: `DESIGN.md` and the applicable design reference. Keep the visual style,
  desktop-first owner/manager workflows, accessibility and visible missing/denied states.
- Detailed requirements: search `docs/requirements/dispositions.md` by ID, then read
  only that source section. Source snapshots, old plans and audits never override scope.
- Full document index: `docs/README.md`. Historical evidence: `docs/archive.md`.

## Default workflow and skills

Matt Pocock is the primary planning workflow. Clear fixes proceed directly; use
`grill-with-docs` for unresolved product/design decisions, `wayfinder` for uncertainty
spanning sessions, and `to-spec`/`to-tickets` for bounded issue briefs and slices.
`codebase-design` and `improve-codebase-architecture` support consequential interface
changes and deliberate architecture surveys. Superpowers is no longer the default.

Skills live in `.claude/skills/`; `.agents/skills` links to the same source for Codex.
If a host has no skill-loading tool, read the named `SKILL.md` and its relevant
references directly. Tracker and domain configuration: `docs/agents/issue-tracker.md`
and `docs/agents/domain.md`; pinned upstream provenance: `docs/agents/skills.md`.

Keep the six Goldy's recipes: `new-connector`, `add-domain`, `add-metric`,
`add-reporting-tool`, `add-migration`, `frontend-api-method`. Use the matching recipe
for those changes. Use targeted Ripwire navigation/checks where useful; generate
large knowledge graphs only for an explicit architecture/onboarding need.

## Smallest complete change

Ponytail remains the implementation baseline. Read affected code/tests and account
for callers, fixtures, config and exports. State consequential assumptions and a
verifiable success condition; ask about unresolved business decisions, not facts
you can inspect. Reuse house patterns, then platform/standard-library facilities,
then installed dependencies. Avoid speculative abstractions and unrelated cleanup.
These also retain the distinct Karpathy-inspired assumption/scope/verification
principles (attribution in `docs/agents/skills.md`).

Finish the whole requested behaviour with the least code. Preserve trust-boundary
validation, error handling, security, accessibility and data-loss protections.
Nontrivial new logic leaves a small meaningful test or assert-based self-check.
Comment non-obvious reasons; use `shortcut: <limit>, <when to upgrade>` for a known
limit. End replies with what was skipped/not checked and any material risk.
Production safeguards and architecture invariants always bind.

## Verification and project constraints

Backend: Java 25; committed Gradle **9.5.0** wrapper. Iterate with `./gradlew fastTest`
or focused tests; before finishing backend changes run `./gradlew test spotlessCheck`.
Integration tests use PostgreSQL 16/Testcontainers, never an in-memory substitute.
Run Spotless on edited Java; extend architecture checks for new packages/domains.

Frontend: Bun only. Run `bun run typecheck && bun run lint && bun run test && bun run build`
for frontend changes. Do not run build and typecheck concurrently. Every API method
needs live and demo implementations; missing values stay missing, never zero.
Docs/skill-only work needs link, requirement-preservation and skill-discovery checks.
See `docs/operations/testing.md` for complete commands and CI.

Flyway owns schema; Hibernate validates. Never edit applied migrations. Raw bytes,
not derived JSONB, are authoritative. Auth is email/password, not OIDC. New permission
resources are owner-only until explicit grants are agreed; owner/manager product
scope grants no access. Manager mapping and customer-balance scope remain open.

## Safety: this VM also runs production

- Never operate the production Compose stack (`docker-compose.prod.yml`, `.env.prod`,
  project `goldys-prod`) unless the user asks for that exact operation. Never run
  `down -v` on it or delete its volumes.
- Never read, print or copy real environment files or credentials.
- Work in a separate checkout/worktree. Do not switch/reset production's checkout.
- Production database sessions are read-only with `default_transaction_read_only=on`.
- Keep tool-level destructive-command/secret denies. Historical runbooks and skill
  instructions do not authorize production operations or external communications.

## Git and completion

Before branching, merging or reporting repo state, run:
`git fetch --prune && git status -sb && git log --oneline -5 origin/main && gh pr list --state all -L 10`.
Use a task branch (`codex/` by default), atomic commits and a PR into protected main.
Never commit directly to main, force-push or merge locally. Push, merge, deploy and
infrastructure deletion require explicit approval. Authorized issue creation does
not authorize those operations. After an authorized push, watch PR checks and report
only observed results. Distinguish locally verified from published/CI-verified work.
