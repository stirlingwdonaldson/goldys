# Pi production follow-up evidence — 16 November 2026

## Production status and evidence boundaries

[PR #269](https://github.com/stirlingwdonaldson/goldys/pull/269) merged at
`96c9af4db0d2ea059de1183bbfc125a6e97b7f6a`. GitHub was read again for this follow-up:
all PR checks passed; [post-merge CI](https://github.com/stirlingwdonaldson/goldys/actions/runs/43819108614)
is successful after rerunning the failed frontend job. The first attempt's log
shows the focus/session test timing out with the app still rendered and no redirect.
These are observed remote results for the deployed commit, not CI for this follow-up.

The user's deployment handoff reports V123, the production worker on the separate
analysis VM, and Pi replacing Ask Goldy's at `https://platform.swd.sh`. The user
confirmed normal existing-account login and a reporting answer. Files, generated-code
execution and sandbox tools remain disabled; manager grants are unchanged.

No production session, credentials, real environment files, production DB queries,
provider calls, certificate rotation or service operations were used for this
follow-up. The private deployment record and release checkout were not required to
change code. Production follow-ups, queue editing, Stop, retry, refresh persistence,
dashboard saves and public citations have **not** been re-executed here. They require
an authorized production acceptance session; local fixtures cannot replace it.

Earlier “not deployed”, “not implemented” and “remote CI unexecuted” passages in the
[implementation history](../design/pi-workspace-implementation-2026-11-15.md),
[preview evidence](pi-preview-evidence-2026-11-16.md) and
[rollout preparation](pi-workspace-rollout.md) describe the time they were written.
They remain historical evidence, superseded for current status by this section.

## Acceptance coverage

| Behaviour | Automated evidence reused or extended | Production boundary |
|---|---|---|
| Follow-ups and queue edits | Workspace browser fixture; panel edit-conflict test; PostgreSQL ordered/idempotent queue and taken-entry rejection; Pi same-context/same-budget tests | Real account workflow not executed here |
| Stop | Browser durable Stop/cancelled queue; backend stale-fence publication rejection; Pi tool abort and queue clearing | Actual production worker termination not executed here; earlier synthetic VM evidence is separate |
| Retry | Browser new request ID/same thread; uncertain-send same-ID retry; expired-worker explicit retry; no automatic replay | Real provider retry not executed here |
| History after refresh | Browser reload/deep-link; panel unmount/reconnect and cached-content withholding; native snapshots and backend ownership/revocation | User confirmed a reporting answer, not these extra flows |
| Explicit dashboard saves | Existing message tests prove no automatic save, denied save, explicit retry, query-only payload and existing-dashboard update; PostgreSQL draft publication test | Actual production dashboard mutation not authorized here |
| Public citations | Browser persisted link/date; new message test rejects HTML, credentials, ports and suffix attacks; worker tests reject non-public links, malformed URLs, 429 and post-search authorization revocation | No production search or provider invoice reconciliation |
| Authorization/failures | Existing-password session/CSRF/cross-account checks; manager and revoked-history denial; budget and publication fencing | No permission matrix or manager grant inferred |

The new citation regression preserves other valid official sources when one URL is
malformed. It does not broaden the four public research topics or make model prose
a governed business record.

## Authentication timing investigation

The original test waited for `The app` in the DOM and synchronously fired one focus
event. `CurrentUserProvider.useSessionChecks` registers its listener in a passive
effect after the authenticated render. DOM appearance is not a subscription barrier:
the event can occur before the listener exists. The CI failure is consistent with
this ordering hazard; the historical log does not instrument the listener, so its
exact scheduler interleaving cannot be reconstructed from that log alone.

The corrected integration fixture waits for listener registration, explicitly sets
the document visible, dispatches exactly one event and holds the expired-session
response until the new request is observed. It asserts the app stays visible during
the check and disappears on 401. No retry or increased timeout is added. Additional
controlled-promise tests cover hidden tabs, event coalescing and a stale focus
rejection after a new identity has been verified. Production provider/session-expiry
code is unchanged. Existing 403/network-error and session-loss tests remain intact.

## Operations changes and remaining deployment dependencies

The [production guide](../../ops/pi-production/README.md) documents the read-only
health endpoint, analysis-VM monitor, checked worker updates/rollback and staged
certificate renewal. No schema migration is needed. Health reads aggregate state
without changing leases, expiring jobs, claiming work or returning investigation data.

Existing self-hosted Sentry is the alert destination. The root-only monitor DSN file,
monitor activation and established recipient routing must be verified by an authorized
operator. No credential was read and no external notification was sent. Until then,
alert delivery and missed-check-in detection are prepared integrations, not accepted
production monitoring. Worker and tunnel automatic restart remain unchanged.

The update helper checks manifests, immutable image IDs, admission/drain and stopped
units/containers before installation. It preserves token/CA/origin, saves previous
configuration/scripts and leaves the worker stopped. Previous bundles support worker
rollback; the legacy frontend rollback remains. Certificate staging creates a new
pair without changing active TLS; coordinated activation/rollback requires maintenance
approval. The new backend health endpoint must be deployed before monitoring or the
updater's drain check can work.

## Targeted quality review

Analysis was refreshed against main `96c9af4d`, including the deployed Pi change
against parent `fca0b930` and a separate working-tree comparison for this follow-up.
Ripwire's name-based call graph labels Spring endpoints, React-rendered components,
Python HTTP callbacks and test-discovered methods as unused; those are framework
entry points, not removal candidates. Repeated MockMvc authorization assertions and
job fixture setup are boundary tests, not interchangeable business algorithms.

Real structural complexity remains in worker `main`/`runClaim`, the workspace panel
and demo fixture, and disabled file/sandbox processing. The targeted panel reports
worker `main` cognitive complexity 21 and `runClaim` 16; those paths combine lifecycle,
shutdown, fencing and persistence ordering. No new defect justified splitting them
in this operational follow-up. The bounded research parser did have a concrete
malformed-URL failure, now fixed with a focused regression. No broad cleanup, manager
changes, file-policy implementation or legacy removal was performed.

Static findings are not represented as a clean gate. Final working-tree counts and
executed validation are recorded below; framework/test false positives remain visible
rather than being suppressed to improve the metric.

## Local verification

- Backend `./gradlew test spotlessCheck`: **2,072 tests, five skipped, zero failures/errors**;
  427 suites. PostgreSQL 16 Testcontainers; no production DB access.
- Frontend typecheck and lint passed. The first full test run had one unrelated
  weekly-report test exceed its existing five-second limit (850 passed). A complete
  rerun with `bun run test -- --maxWorkers=2` passed **851 tests in 147 files**.
  No retry or timeout setting was added. Auth focus/visibility tests passed in both
  runs and in the focused 26-test auth run.
- Production-mode frontend build passed with workspace enabled, files disabled and
  demo mode off. The workspace Playwright fixture passed on local port 3319,
  including persisted citations and revocation. The first browser invocation could
  not start because port 3117 was occupied; the existing service was left alone.
- Worker typecheck, **23 tests**, compilation and Docker build passed. The built
  image fails closed without configuration with networking disabled.
- **16 offline operations tests** passed, covering independent failing probes,
  bounded/sanitized alerts, TLS expiry threshold, drain/state guards, tampered and
  incomplete bundles, immutable references, update/legacy rollback compatibility,
  configuration/CA preservation and certificate staging.
- Actual packaging with a synthetic certificate passed manifest verification for
  all 11 files and immutable worker image
  `sha256:03bd25b3e234d9022fe9abd6002d9c830be47cec058285d2bed0599b20f5f317`.
  No release was installed. The real isolated TLS-proxy smoke passed.
- Shell syntax and systemd monitor-unit validation passed. Local documentation
  links and six Goldy's recipe discovery passed. PRD/SRS, manager grants and
  production file/sandbox defaults were preserved.
- Final Ripwire working-tree delta against main reports **23 findings, two gating
  clone findings**, both in existing/new authorization and job test patterns.
  The remaining findings are test/framework reachability and size signals; no
  new complexity regression is reported. Findings were reviewed, not suppressed;
  this is not a clean static gate. `publicResults` retains its caller contract.

Not checked: new branch remote CI, fresh vulnerability scan of this worker image,
actual production feature acceptance, production update/rollback/renewal, Sentry
alert delivery/recipient configuration, representative file extraction or actual
provider invoices. Previously observed scans are historical, not a scan of this image.

## Publication and tracker preparation

Open issues were refreshed before implementation. No open Pi implementation ticket
was found. The [decision map](https://github.com/stirlingwdonaldson/goldys/issues/249)
explicitly excludes delivery work; none of its human decisions is resolved by this
release. Manager mapping (#240), customer-balance scope (#247), sensitive-file policy,
retention and real provider-billing evidence remain independent dependencies.

Prepared publication text (not posted):

> Pi core is deployed through PR #269 at 96c9af4d; the user confirms existing-account
> login and a reporting answer. The follow-up adds local acceptance coverage,
> read-only operational probes, guarded worker update/rollback and certificate
> renewal preparation, and deterministic session-focus testing. Files/sandbox and
> manager grants are unchanged. Deployment and production operational acceptance
> remain separate approvals; fixture coverage is not production acceptance.

Prepared separate delivery issue, if publication is authorized:

**Title:** Complete production Pi follow-up rollout and operational acceptance

**Outcome:** Deploy the reviewed follow-up and demonstrate the enabled owner workflows
and operational notifications on the existing installation.

**Acceptance:** With an authorized existing owner session, verify follow-up/queue edit,
Stop/explicit retry, history after refresh, query-only dashboard saves and public
citations. Install and verify all four Sentry monitors, including controlled failure,
missed-check-in and recovery delivery to the established operations recipient. Exercise
the paused/drained worker update/rollback procedure and certificate renewal during an
approved maintenance window; record public fingerprints and observed results privately.

**Dependencies/non-goals:** Requires publication/deployment/maintenance authorization,
existing-account access and the existing Sentry destination/recipient. Do not create
manager grants, enable files/sandbox, settle retention or customer-balance policy,
remove legacy rollback or claim provider-bill reconciliation without billing evidence.
This is a proposed publication payload, not a second local task tracker.
