# Issue tracker

Use GitHub issues in `stirlingwdonaldson/goldys`. Prefer the connected GitHub tools;
use `gh` where a capability is missing. Never default to the Matt upstream repo.
PRD/SRS stay in the repository; issues link to requirement IDs and carry outcome
or question, acceptance/evidence, blockers and non-goals. Avoid copied full specs.
No local shadow tracker. PRs are implementation reviews, not a triage request queue.

## Labels and readiness

`ready-for-agent`: bounded work without unresolved human decisions.
`ready-for-human`: a decision or stakeholder/source-access gate.
`needs-triage`: unscoped capability parent. Never mark a blocked proposal ready
merely because a spec was written. `priority:reporting` marks the agreed first area.

Create one issue per approved brief/slice. Read its body and comments before acting;
recheck open issues to avoid duplicates. Only publish within session authorization.
Pushes, merges, deployments and external notifications need their own authorization.

## Wayfinding operations

A map is one issue labelled `wayfinder:map`; decision tickets are native sub-issues
labelled `wayfinder:research`, `wayfinder:prototype`, `wayfinder:grilling` or
`wayfinder:task`. Only `wayfinder:` labels belong on these issues: no readiness or
priority labels. Each decision ticket has only a `## Question` section; the answer
goes in a resolution comment. Implementation issues stay outside the map and use
the `to-tickets` structure, even when blocked by a map decision.

The map has exactly Destination, Notes, Decisions so far, Not yet specified and
Out of scope sections. Do not duplicate open-ticket lists in its body; native
sub-issues are that index. Refer to issues by linked titles in human-facing text.

Use GitHub's native sub-issues and blocking relationships. With older `gh`, use
`gh api --method POST repos/stirlingwdonaldson/goldys/issues/<parent>/sub_issues -F sub_issue_id=<child-database-id>`
and `gh api --method POST repos/stirlingwdonaldson/goldys/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-database-id>`.
These use database IDs, not issue numbers. Fetch IDs from the issue API.
If GitHub reports a capability unavailable, record that and use a `Part of` or
`Blocked by` link in the body; do not create another tracker.

The frontier is the map's open, unassigned children with no open blockers. Claim
by assigning the driving user before work. Resolve with a decision/evidence comment,
close the ticket, and add a short named link to the map's Decisions so far.
Charts and references are loaded on demand; do not fetch every ticket body at startup.

## Resume this effort

Open [Find the path through Goldy's remaining product decisions](https://github.com/stirlingwdonaldson/goldys/issues/249)
and read its Notes first. Invoke `wayfinder` with that map URL. Query the native
frontier as below, preserving child order. Claim the first eligible ticket before
working; resolve at most one non-research ticket per session. Grilling requires
the human; do not answer on their behalf. On resolution, comment, close and add a
short named link to Decisions so far; graduate newly sharp questions and blockers.

Until the documentation PR is merged, use `codex/docs-skills-reconciliation` and
the map's pinned published commit/reference links. Start in a checkout of that
branch; refresh skill discovery or restart the host if the skills do not appear.
If the baseline cannot be fetched, stop dependent work rather than substituting
the older main-branch PRD. After merge, fresh sessions on updated main inherit it.

```bash
gh api --paginate repos/stirlingwdonaldson/goldys/issues/249/sub_issues --jq '.[] | if .issue_dependencies_summary.blocked_by == null then error("Dependency summary unavailable; inspect native blockers") else select(.state == "open" and (.assignees | length) == 0 and .issue_dependencies_summary.blocked_by == 0) | {number, title, type: [.labels[].name | select(startswith("wayfinder:"))][0]} end'
```
