# Agent personas

Three review personas live in `.opencode/agents/` and run as subagents (`mode: subagent`).
They exist for one reason: independent, fresh-context review that the implementer cannot
self-grade. Invoke them with a slash command or the `subagent` tool.

| Persona | File | Purpose |
|---|---|---|
| `code-reviewer` | `.opencode/agents/code-reviewer.md` | Five-dimension review — correctness, readability, architecture, security, performance — with severity-labelled findings |
| `security-auditor` | `.opencode/agents/security-auditor.md` | OWASP Top 10 (+ LLM Top 10) pass; classifies exploitable vs theoretical risk |
| `test-engineer` | `.opencode/agents/test-engineer.md` | Test strategy, coverage analysis, Prove-It tests for bugs |

## Commands

| Command | What it does |
|---|---|
| `/review [target]` | Single-perspective review with `code-reviewer`. |
| `/test [target]` | Test design or coverage analysis with `test-engineer`. |
| `/ship [target]` | Parallel fan-out of all three, merged into one go/no-go report. |

With no target, each reviews the current uncommitted changes.

## Rules

- Reviewers report; they never edit files or act on their own findings. Orchestration —
  launching them, deciding what to address — belongs to the parent session or a slash
  command, never to another persona.
- Every persona reads `.claude/rules/architecture-invariants.md` and
  `docs/architecture/current-state.md` and treats a violated invariant as Critical.
- Pin each reviewer to a different model than the implementer when budget allows; a
  reviewer on the same model shares the implementer's blind spots.
