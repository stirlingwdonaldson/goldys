---
description: Parallel fan-out review (code-reviewer + security-auditor + test-engineer) merged into one go/no-go report
---

Run a ship-readiness review of $ARGUMENTS, or the current uncommitted changes if no target is given. Launch the `code-reviewer`, `security-auditor`, and `test-engineer` subagents in parallel, then merge their findings into a single report: severity-ordered items and a go/no-go verdict.
