# Pi workspace rollout and rollback preparation

**Not deployment-ready.** This runbook prepares the local migration; it is not
approval to provision infrastructure, access production, push, merge or deploy.
The [implementation record](../design/pi-workspace-implementation-2026-11-15.md)
contains observed verification. The [accepted architecture](../decisions/0002-pi-agent-workspace.md)
and [proposal acceptance table](../design/ask-goldys-pi-workspace-proposal-2026-11-15.md#verification-plan-and-release-gates)
remain the release gates.

## Activation and admission controls

| Setting | Default | Meaning |
|---|---|---|
| `app.workspace.enabled` | Absent / off | Registers workspace browser and private worker endpoints only when explicitly true |
| `NEXT_PUBLIC_WORKSPACE_PREVIEW` | Absent / off | Builds the additive Investigations drawer only when explicitly true |
| `app.workspace.accepting-jobs` | True | When false, rejects new investigations and new follow-ups while leaving enabled workspace endpoints available |

The admission switch does not enable either activation flag. It is startup configuration,
not a model tool or browser permission. Its change requires the normal approved service
configuration/restart procedure. Accepted jobs may still be claimed and run: pausing
admission is a drain control, not cancellation. Stop is a separate durable operation.

Idempotent resends of already accepted job/follow-up IDs still return the existing
result while admission is paused. Conflicting content still fails. Reads, thread
listing, queue editing/removal and Stop retain their existing ownership and current
policy checks. Rejected new submissions allocate no reservation or durable thread.
The frontend displays the existing explicit conflict/error response; it does not
silently send the request to legacy Ask.

## Controlled rollback during the transition

1. Under explicit operations authorization, pause admission with
   `app.workspace.accepting-jobs=false`, leaving workspace activation on so current
   history and Stop remain accessible. Verify a fresh request is rejected and an
   idempotent resend still resolves to the original job.
2. Use the owning user's authorized Stop operation for active investigations. Verify
   durable cancelled/interrupted state and cancellation of pending follow-ups. A
   cancelled attempt must not accept later checkpoint or reporting publication.
3. Stop the dedicated worker through its service supervisor and verify actual
   termination. A failed heartbeat is final locally; stale attempts are never replayed.
   Lease expiry is a fallback fence, not evidence that an external process terminated.
4. Keep authorized history available. Do not turn off `app.workspace.enabled` merely
   to pause intake: it removes history and cancellation endpoints as well.
5. Preserve the existing legacy Ask route during the accepted transition. Never import
   Pi snapshots into Spring AI, dual-run prompts or automatically retry a failed Pi
   attempt on another engine. Weekly reporting and saved dashboards remain independent.
6. Fix forward with additive migrations. Do not downgrade the schema or restore the
   entire application database just to switch execution engines. Resume admission only
   after the defect's regression and the outstanding release gates are accepted.

The current implementation has no generated-code sandbox or artifact store to drain.
Before those capabilities ship, extend this procedure with independently observed
sandbox termination, orphan cleanup, artifact revocation and byte-store reconciliation.
Do not present the job-state checks above as containment or restore evidence.

## Evidence required before activation

- Finish precise context/history dependency handling and conservative treatment of
  unknown legacy provenance; keep whole-context invalidation until coverage is proven.
- Implement and test the disposable runtime on the authorized separate analysis VM,
  including process termination after worker loss, path/symlink controls, resource
  exhaustion and network denial. This VM's production Docker stack is not that test bed.
- Complete quarantine, bounded extraction (including scans), artifacts/downloads,
  Keep/expiry/deletion, cleanup and coordinated database/byte-store restoration.
- Complete controlled public-only research, citations and search spend accounting;
  reviewed bounded dataset extraction; live/demo failure and feature parity.
- Verify configured provider caps, current price/FX bounds, actual receipt reconciliation
  and data handling against permitted live executions. Synthetic provider tests do not
  establish those properties.
- Record representative real-file extraction quality and an isolated restore rehearsal.
  Approved retention/hold/backup obligations and recovery targets are still needed.
- Obtain the manager permission matrix and sensitive-file reviewer policy before those
  users/capabilities are enabled. Owner product scope is not an implied permission grant.
- Build/scan the worker and analysis images, verify deployment configuration and remote
  CI after an authorized push, then run the accepted owner pilot. Remove the old Ask
  loop only after replacement acceptance; keep Spring AI's other consumers.

No infrastructure credentials, real environment files, production data or provider keys
are required to review this preparation. Their future use requires the corresponding
explicit authorization and actual environment; missing access must be recorded as a
specific unexecuted gate, never a passed mock test.
