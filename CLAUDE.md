# CLAUDE.md

@AGENTS.md

## Claude Code specifics

- `.claude/rules/architecture-invariants.md` loads automatically for `backend/**`.
- The `.claude/settings.json` PostToolUse hook runs `./gradlew spotlessApply` after
  Java edits.
- Repo skills in `.claude/skills/` are shared with opencode; keep them tool-agnostic
  (no Claude-only tool names in their instructions).
