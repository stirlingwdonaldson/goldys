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
`wayfinder:task`. Keep these separate from implementation readiness labels.

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
