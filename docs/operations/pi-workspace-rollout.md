# Pi workspace rollout and rollback preparation

**Not production-ready.** A private synthetic pilot now has separate [preview operations](../../ops/pi-preview/README.md). This runbook governs production activation; it is not
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

## Historical content during transition

V120 leaves existing legacy conversation policy stamps NULL. Such history has unknown
provenance: list entries use a generic restricted title/notice, while message reads,
context reuse, renaming, answer publication and further streamed output fail closed.
New legacy conversations capture the current account/role policy revision and retain
the legacy engine during transition. Revoking and restoring a grant or disabling and
reactivating an account cannot revive older context. This uses the existing
`conversational.threads` resource, not a new manager grant or workspace permission.
Owned deletion retains the existing permission check and remains available.

Do not backfill old stamps with current grants: that would falsely assert historical
access. The consequence at deployment is that pre-V120 conversations cannot be opened
or resumed. No historical bytes are deleted by the migration and none are imported into
Pi. Selective release of unknown legacy content requires proven provenance; the model
must not redact restricted input after receiving it. Already delivered/downloaded bytes
cannot be recalled. The conversation-history screen withholds cached titles, previews
and transcripts until a fresh read completes, hides them on failed reauthorization,
and rechecks visible history every ten seconds. This does not erase previously
delivered content from an already-open legacy Ask drawer or external copies.

## Worker image preparation

`agent-worker/Dockerfile` pins the Bun 1.4.2 registry index by digest. Its allowlisted
build context excludes local environment files, caches, outputs and the application
checkout. Dependency lifecycle scripts are disabled; the build runs typecheck,
synthetic tests and compilation. The runtime contains compiled worker modules and
production dependencies, runs as unprivileged UID/GID `65532`, and disables dotenv
loading. No provider configuration or credentials are baked into the image.

Build with `docker build -t goldys-agent-worker:verification ./agent-worker`. CI now
builds this image and verifies that startup without configuration fails before any
service request, with networking disabled and a read-only root. This smoke check is
not a sandbox/egress acceptance test. The worker still requires the authorized separate
analysis VM and an approved supervisor, secret injection, egress policy and process
termination checks. Do not run a configured worker on the production host.

The dedicated deployment must enforce a read-only root, bounded private temporary
storage, unprivileged identity, dropped capabilities, no privilege escalation and
CPU/memory/process ceilings. Mount no app checkout, environment files or Docker socket
into this worker image. The separate `analysis-sandbox` package supplies the offline gVisor runtime used in the synthetic preview.
CI scans an exported worker image with a digest-pinned Trivy container, without a
Docker socket, and fails on high/critical findings. Database-download or scan failure
is a failed check, never an empty vulnerability result. The [dated local scan](pi-worker-image-scan-2026-11-16.md) records zero high/critical
and 27 remaining lower/unknown-severity findings, without accepting them. Current
scans and the separate-VM deployment rehearsal remain release gates. Updating the pinned base requires repeating the build and acceptance checks.

## Production evidence still requiring acceptance

The later preview record supersedes earlier absence-of-implementation notes. Synthetic containment, live-provider flows and isolated restore checks do not establish representative business-file accuracy, owner acceptance, manager policy or production readiness. See the latest implementation continuation for exact observed results.

## Original activation checklist (reconcile against latest evidence)

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
