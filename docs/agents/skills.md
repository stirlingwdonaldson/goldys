# Agent skills

Matt Pocock is the default planning workflow; Ponytail is the smallest-complete-change
implementation baseline. Clear fixes proceed directly. Use `grill-with-docs` for
unresolved decisions, `wayfinder` for multi-session uncertainty, `to-spec` for an
issue brief, and `to-tickets` for ready end-to-end slices. Use `grill-me` for a
discussion without persistent repo documentation. `codebase-design` and
`improve-codebase-architecture` support deliberate module/interface work.

The 12 Matt skills and dependencies are pinned to
[`4ae2317`](https://github.com/mattpocock/skills/tree/4ae2317ddb22b046e26455e4f9c9b3aaaf62212d).
`matt-skills.lock.json` records upstream and installed file hashes; the adjacent
licence preserves attribution. The six existing Goldy's recipes remain.

## Local integration

- `.claude/skills/` is the single source. `.agents/skills` is a relative symlink
  for Codex; OpenCode also discovers the Claude-compatible source. Native invocation
  metadata is retained. Use the host skill loader, or read the named skill's
  `SKILL.md` directly when no loader exists. Never assume a Claude-only tool exists.
- Tracker: [GitHub](issue-tracker.md). Domain vocabulary/decisions: [domain docs](domain.md).
  Repository PRD/SRS are reference authority; issues hold progress and decisions.
- `AGENTS.md`, architecture invariants and source evidence govern local work.
  Use real PostgreSQL 16/Testcontainers; preserve connector ports and meaningful
  regression tests. Current design and accessibility govern prototypes.
- A skill's publication, research or notification instructions do not authorize
  pushes, merges, production operations or external sends. Follow existing user
  authorization. A planning conversation does not silently implement its result.
- Upstream skill-tool wording, ADR paths, database/test examples and verbose spec
  advice were adapted to these conventions. No upstream hooks or executables were
  installed. Supporting references stay available on demand.

Superpowers was removed from default project wiring; historical designs remain
accessible. No global skills/plugins were uninstalled. Codex `skills/list` discovered all 18 project skills without errors on 4 November 2026.
OpenCode/Claude compatibility follows the shared directory layout; fresh-session
runtime discovery in those hosts is not yet verified. Restart the host if changes
do not appear after updating the checkout.

Ponytail attribution: [DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail).
The root retains distinct assumptions/scope/verification guidance from the
[third-party Karpathy-inspired guidelines](https://github.com/multica-ai/andrej-karpathy-skills),
without loading a duplicate simplicity ruleset.
