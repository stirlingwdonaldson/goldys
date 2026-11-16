# Pi workspace implementation evidence — 15 November 2026

Implementation starts from proposal commit `98b8eef9`, following [ADR 0002](../decisions/0002-pi-agent-workspace.md). Local implementation and commits are authorized by the implementation request; production access, provisioning, push, merge and deployment are not.

## Released SDK compatibility

`agent-worker/` pins Bun 1.4.2 and `@earendil-works/pi-coding-agent@1.5.2`, with a committed transitive Bun lockfile. Installation used `--ignore-scripts`. The tests execute the installed npm package, using Pi's synthetic provider with an empty in-memory credential store and no catalog refresh. No real model or data is used.

Observed: four tests pass, covering explicit empty tools, no project AGENTS/extension discovery, native `message_end`/`agent_settled`, complete native tree restoration including branches/null leaf/context edits/compaction, tool AbortSignal propagation, clearing queued follow-ups before abort, and manual compaction through the synthetic provider. Typecheck and Bun build pass. `session.ts` stores detached native snapshots rather than a separately writable transcript.

This establishes a usable released SDK seam, not sandbox containment or production readiness. The worker process still needs a sanitized environment, restricted filesystem/network and Spring admission/authorization/billing boundaries. Snapshots are trusted server records, never a browser-supplied import format. Compaction retains original entries and is not a revocation mechanism. No built-in script tool is exposed.

## Outstanding acceptance

The pinned SDK spike is implemented and tested. Persisted-job and governed-bridge foundations are implemented below, but those delivery slices are not complete end to end. Sandbox execution, attachments/artifacts, controlled web research, live/demo UX, reviewed dataset extraction and operational transition remain unimplemented. Actual separate-VM containment, provider pricing/data handling, representative extraction quality, restore/retention and owner pilot are unverified. Manager access remains blocked by the unapproved permission matrix. No production operation or live provider request has been performed.

## Persisted job slice (local, disabled)

The additive V114 migration introduces private workspace threads, one active job per thread, unique submission IDs, fenced leases and venue-day admission reservations. `WorkspaceJobs` stores native snapshots with compare-and-set revisions. Stop fences publication; expiry records interruption without re-queueing; submitting a new request ID explicitly retries against the last saved snapshot. Reads are polling/reconnect-safe and never depend on a browser stream remaining connected.

`WorkspaceAuthorization` reads current active account state and calls `PermissionService`. Its conservative policy fingerprint covers the account and all current grants; any change denies the old context rather than attempting to redact it. New workspace grants are ALL × OWNER only. This is not precise dependency revocation and does not migrate legacy threads.

`WorkspaceReporting` accepts a fixed `ToolId` and strictly deserializes its existing input record, then delegates to `ToolDispatcher`; the worker cannot supply a role or SQL. The lease and current policy are checked before and after execution. The private worker routes require a configured token of at least 32 characters, checked in constant time. Browser routes retain session authentication and CSRF. All workspace controllers are absent unless `app.workspace.enabled=true`; leave this disabled until release gates pass.

The TypeScript job adapter serializes snapshot publication, heartbeats independently of the browser, aborts on persistence/lease failure and enforces the job deadline. HTTPS is mandatory for the configured Spring origin; redirects are rejected. There is no automatic retry/replay of failed publication or model work.

Observed focused checks: six PostgreSQL 16 lifecycle tests plus migration and architecture checks passed; six worker tests/typecheck/build passed. Additional worker-auth/reporting checks passed subsequently; final full-run results are recorded below.

Limitations: admission reserves A$5 against A$50 and retains reservations after cancellation; this is **not actual provider/search charge enforcement**, including cross-midnight charging and provider-specific output caps. A single-slot worker entrypoint exists but was not run against a provider or Spring service. Follow-up queue editing, typed UI projections/events, precise evidence dependencies, legacy-history revocation, end-to-end governed model runs and explicit failure publication remain work. A lost worker is marked interrupted on the next status/claim access. Existing Ask execution remains the current user path. The new reporting transaction holds ownership/policy locks through a bounded query; Stop waits for that query to finish and the stale worker cannot then publish after cancellation.


## Governed reporting and request guards

The bridge generates schemas from existing Java input records with the installed Victools/Jackson
schema libraries, now declared directly. A regression test caught Java enum names being emitted
instead of Jackson wire IDs; generation now respects `@JsonValue` (`sales.net`, not `SALES_NET`).
The worker binds every invocation to the fixed job/fence and HTTPS Spring origin. Model arguments
cannot replace the URL, headers, role or tool registry. It retains original tool result details.

A mandatory model-request guard in `runClaim` reauthorizes and reserves a reviewed worst-case charge
before each Pi model request, including compaction. Unknown prices/models fail closed; retries and
failed requests retain their reservation. Provider-specific price/cap verification and actual
billing reconciliation remain unverified. Twelve worker tests, typecheck and build pass. Empty-env
startup fails before service access. No real worker/provider invocation was performed.

The first complete backend regression run passed 1,997 tests with five skipped, before the final
schema/revocation additions. Subsequent focused PostgreSQL worker-auth/reporting tests passed after
fixing the enum contract. The final complete run must be recorded below when finished.

Ripwire's edit check identified a new workspace boundary with no incompatible callers. The quality
delta is not clean: it reports Spring-discovered methods as dead code and lexical clone matches
between short controller delegation/constructor bodies and unrelated existing services. The broad
clone families were inspected as structural/template matches; no unrelated business code was
changed to suppress them. This tool output is not a claim of a clean merge-safety gate.


The released SDK emits `message_end` before `SessionManager.appendMessage`. A failing test
reproduced a snapshot one message behind; publication now captures on the next microtask, after
Pi's synchronous append. The regression asserts that completed text appears in a nonterminal
checkpoint, not only the final snapshot. Worker shutdown during a provider request also passes
without terminal success publication. These are synthetic runtime observations, not provider or
sandbox guarantees.


## Final local verification for this checkpoint

`./gradlew test spotlessCheck` passed on the final backend code: 2,002 tests, five skipped,
zero failures/errors. The preceding full run exhausted the test JVM heap while constructing
an extra MockMvc application context. Token-controller checks were moved to a small unit test,
and the PostgreSQL tests now reuse the existing application context; the subsequent complete
run passed without increasing the test heap. Twelve worker tests, frozen-lockfile install,
typecheck and build pass. Fifty local documentation links resolve; scope documents and
architecture invariants remain unchanged, and recipe discovery was checked.

No frontend code changed or frontend checks ran. No live provider, real-file extraction,
containment, production, backup/restore or pilot test ran. CI was configured but not run remotely.
The final fetch observed `origin/main` at `fca0b930` (two commits newer than this proposal base);
V114 remains unused there. This checkpoint has not been rebased or tested with those newer commits.

This is an incomplete migration checkpoint, not completion of the implementation request.
The next work remains completion of slices 2–3 and then slices 4–9 in the proposal: actual
sandbox/runtime isolation, attachments/extraction/artifacts/retention, controlled web egress,
workspace UI/live-demo/history, reviewed dataset extraction and operational transition. Keep
workspace activation disabled and manager onboarding blocked. Do not retire Ask's current
execution path until the replacement and its reporting/permission regressions are accepted.


## Continuation: durable follow-ups and browser projections

Continued from `fc4e3aff` in the existing implementation worktree. The initial fetch still
observed `origin/main` at `fca0b930`; V114/V115 were unused there. No rebase or integration with
those two newer main commits has been performed.

V115 adds durable follow-up commands under the existing job. Authenticated APIs support FIFO
queue reads, idempotent enqueue, edit and removal until taken. Commands share the investigation's
original deadline and charge ceiling. Atomic settlement saves the native snapshot and either takes
the next command or completes the job under the thread lock, preventing accepted messages from
being stranded by concurrent completion. Stop/interruption/expiry cancel queued commands; a taken
command remains visibly taken and is never automatically replayed after failure. Twenty total
follow-up submissions per investigation bound queue storage, including cancelled submissions.

The worker feeds settled follow-ups into the same Pi-native session and preserves its model-request
budget across them. Failure reports a fenced interruption without exception content; unreachable
Spring falls back to lease expiry. Completed/cancelled states cannot be overwritten by stale failure
reports. Native snapshots remain worker-only: browser job responses derive active-branch messages,
typed reporting widgets/notices/provenance, and explicit unavailable-result notices. They exclude
reasoning, compaction internals, inactive branches and arbitrary tool payloads. No second transcript
is stored. Current-policy authorization still runs before the projection is returned. Reporting
denials retain an explicit “Not permitted” result without upstream response bodies. Expected
queue/admission/lease conflicts use HTTP 409 with the stable `WORKSPACE_CONFLICT` envelope.

These changes advance slices 2–3; they do not complete either slice or the migration. Queue UX,
thread discovery/legacy history, precise dependencies, live/demo migration, sandbox execution,
files/retention, controlled research, reviewed dataset extraction and operational transition remain.
The read projection is not a context reconstruction engine and does not change Pi compaction or
model context. Keep activation off and the existing Ask path in service.

The frozen-lockfile worker install, typecheck, 15 synthetic tests and build pass. Tests cover
same-session follow-ups, shared request budgets, persistence rejection and shutdown. PostgreSQL
tests cover FIFO/edit/remove/idempotency, enqueue-versus-completion concurrency, expiry without
replay, stale failure reports, and ownership/current-policy checks. Projection tests cover branch
selection, exclusion of reasoning/internal metadata, malformed branches, unsupported widget types,
and preservation of missing widget values and notices.

Ripwire remains non-clean at `fc4e3aff+dirty`: the final quality check reports 22 gating findings,
including `runClaim` complexity increasing from 13 to 16, class-size growth, lexical controller
clones and Spring/JUnit-discovered methods reported as dead. The targeted `WorkspaceJobs` edit
check reports no incompatible callers, but does not establish that the changed browser `Job`
record is compatible; it intentionally replaces native `snapshot` with `messages`. The broader
test gate exits 4 with 19 test files and 140 statically untested symbols (name-based graph with
2,480 ambiguous and 4,713 unresolved edges); these findings are not a clean safety verdict.
The full backend regression result for the final code is recorded below.


Final continuation verification: `./gradlew spotlessApply test spotlessCheck` passed with
2,012 tests, 5 skipped, zero failures/errors. The preceding two full runs also passed;
they preceded the last denial/conflict fixes. No test heap increase was made. Worker frozen install,
typecheck, 15 tests and build passed. Documentation links, recipe discovery and diff whitespace
checks pass. No frontend, live provider, separate-VM containment, extraction, backup/restore,
production operation or remote CI test was performed. Nothing was pushed, merged or deployed.
This remains an incomplete implementation, with workspace activation off and manager onboarding
blocked; the remaining delivery slices and acceptance gates above still apply.

## Continuation: rediscovery and browser API contract

Continued from `9e9db6cf` in the same implementation worktree. Fetch still observes
`origin/main` at `fca0b930`; its highest migration remains V113. This continuation adds
no migration and does not incorporate the two newer main commits.

`GET /api/workspace/threads` now provides private, bounded keyset pagination (default
25, maximum 50), ordered by creation time and UUID. It returns thread IDs, creation
timestamps and the latest job ID, with no prompt, title, snapshot or result content.
Unknown and foreign cursor IDs are denied identically. Current account/workspace
permission checks apply to every page. Policy-invalidated threads remain visible as
`NOT_PERMITTED` entries with no job ID; callers must start a new thread. Discovery does
not read native snapshots or weaken the existing policy fingerprint. Authorized job
reads now include the already-persisted admission prompt, so a browser can offer an
explicit retry even if the worker failed before its first native checkpoint. This is
not a new stored transcript. Latest-job reads retain expiry/interruption handling.

Focused PostgreSQL tests pass for equal-timestamp pagination, ownership/cursor denial,
page bounds, revoked/deactivated/manager access, fresh threads after policy changes,
latest-attempt recovery and recovery of the prompt before the first checkpoint.

The frontend `Api` has matching live/demo workspace operations for discovery,
submission, polling/reconnect, Stop and queue read/enqueue/edit/remove. Live responses
are validated with Zod, including the existing widget and provenance schemas; native
snapshots are not a browser contract. Demo calls remain entirely local, retain
idempotency and queue conflict behavior, cancel pending messages on Stop, and include
interrupted/missing-result and restricted-history fixtures. Demo responses are detached
copies. The demo adapter does not execute Pi or simulate model progress, expiry or
provider billing. These are API foundations, not completed queue UX: no workspace
screen is wired into the current Ask drawer and existing Ask remains operational.

Remaining work still includes complete slices 2–3 (UI reconnect/queue behavior, legacy
history and precise dependency/revocation handling), followed by sandbox execution,
files/extraction/retention, controlled research, full UX/demo, reviewed dataset extraction
and operational acceptance. Activation remains off; manager rollout remains blocked.

Observed frontend verification: frozen-lockfile install with lifecycle scripts disabled,
then sequential `bun run typecheck`, `bun run lint`, `bun run test` (140 files, 813 tests)
and `bun run build` passed. Tests exercise duplicate submission/follow-ups, conflicts,
Stop cancellation, offline-only demo behavior, missing widget values, restricted history,
explicit retry preserving prior results, detached fixture responses and rejection of
native-snapshot/malformed-widget browser responses.

Ripwire at `9e9db6cf+dirty` remains non-clean: quality delta reports 18 findings with one
gating lexical clone (`WorkspaceThreadController.list` versus a short `restore`
controller delegate). Inspection found generic controller delegation, not shared domain
logic to extract. It also flags the new demo factory's complexity (25), Spring/JUnit
entry points as dead code and existing class size/churn. No unrelated controller was
rewritten to suppress a lexical match. The broad test gate lists 26 test files and 254
statically untested symbols with 2,480 ambiguous and 4,743 unresolved graph edges; it
is not a clean safety verdict. The targeted `WorkspaceJobs` edit check finds no
incompatible callers but does not validate the browser record shape; frontend schema
and contract tests cover that boundary separately.

Four local links in the changed documentation resolve; all six Goldy's recipes remain
discoverable from canonical and Codex paths. Scope, accepted decisions, permission
seeds and migration files are unchanged. No worker source changed, so worker checks
were not repeated. No browser flow, live provider, real-file extraction, separate-VM
containment, retention/restore, production operation or remote CI check ran.

Final backend verification for this continuation: `./gradlew test spotlessCheck`
passed in 6m 48s with 2,015 tests, five skipped and zero failures/errors. The focused
workspace integration run also passed, and edited Java was formatted with Spotless.
Nothing was pushed, merged or deployed. This remains an incomplete migration, with
workspace activation off and the remaining delivery/acceptance gates listed above.

## Continuation: persisted investigation drawer

The optional Investigations entry point now mounts a right-hand workspace Sheet when
`NEXT_PUBLIC_WORKSPACE_PREVIEW=true` at frontend build time. The flag defaults off;
no production/build configuration was enabled. The existing Ask button, command
shortcut and legacy conversation page remain available. The preview is owner-only in
the UI; Spring remains the authorization boundary and its workspace feature flag is
still off by default. This is an additive implementation preview, not pilot readiness.

The selected investigation lives in the `investigation` URL parameter while preserving
other page filters. Reload/deep links read the saved job; closing the Sheet unmounts
polling without calling Stop. Opening from a page pushes history, Back/Escape closes
the Sheet, and focus returns to its trigger. New/history lists authorized threads with
pagination and explicit restricted entries. Active jobs poll every two seconds; terminal
history is rechecked every ten seconds while open. Cached history is withheld on
remount until a fresh job read completes and is hidden after read/authorization failure.
Identity/demo switches inherit the existing query-provider reset.

The composer queues messages while a job is active. Queued messages can be edited or
removed until taken; server conflicts refresh their actual state. Stop targets the durable
job and preserves committed output. Interruption offers an explicit new attempt using the
original question; taken follow-ups are labelled and never automatically replayed. An
uncertain send retains its original command ID/payload for explicit resend, with editing
locked until acceptance is resolved. Mutation retries are disabled. No user text is
written to browser storage, and no browser transcript is persisted separately from Pi.

The view renders text, existing validated widgets, missing values, notices and source
provenance. It does not yet support files, citations from public research, generated
artifacts or dashboard-draft actions in workspace results. Existing Ask/dashboard paths
remain available; their preservation does not establish replacement feature parity.
The offline demo adapter can exercise queue/Stop/interrupted states without live calls,
but does not run a simulated model or advance queued jobs automatically.

A UI review found that Stop could scroll out of view during queue editing; the status
and Stop row now remain sticky within the scrolling panel. A separate regression found
that disabling queue polling at terminal job state could retain a stale editable queue.
The queue query now includes the job state, forcing a final refresh on interruption,
expiry/completion observed through polling, or Stop. No server semantics changed.

Nine component regressions cover reconnect without replay/Stop, withholding stale cached
history until fresh authorization, hiding content after revocation, no late reopening
after an unmounted send, explicit idempotent resend after uncertain delivery, follow-up
routing, explicit interrupted retry, terminal queue refresh and taken-message edit races.
The fixture browser flow intercepts all application APIs and checks saved reconnect,
queue edits/removal, Stop, explicit Retry, revoked history and responsive rendering;
it does not establish real provider/worker execution or containment.

Ripwire's final incremental quality delta against `acad1a55` reports zero gating findings,
with eleven advisory findings: complexity of 17 in the new message renderer and panel,
and nine JSX components classified as dead code despite their imports/render paths. The earlier panel
complexity/nesting findings were reduced by separating commands, picker and composer.
The targeted AppHeader edit check reports an unchanged contract and no incompatible
callers. The broad test gate reports zero impacted tests/symbols for this primarily
new/dynamically imported JSX surface, with 2,480 ambiguous and 4,756 unresolved edges.
That is a static graph limitation, not evidence that tests are unnecessary or that prior
migration-wide static findings have been resolved.

Final local UI verification: sequential frontend typecheck, lint, 141 test files / 822
tests and the production build with the preview flag passed. The nine workspace component
tests are included in that total. Playwright passed both the existing Ask regression and
the new workspace fixture flow (two tests), including Back/Escape, preserved URL filters,
focus return, visible Stop after scrolling, direct-link reload, manager entry-point
exclusion and desktop/narrow screenshots. Screenshots were inspected locally. An initial
focused Vitest run failed before collecting tests because its inherited temporary path
was unavailable; subsequent runs used `TMPDIR=/tmp` and passed. No dependency or test
heap change was required. Five local documentation links, six-recipe discovery and diff
whitespace checks pass.

Backend and worker source did not change, so their suites were not rerun in this UI
continuation. No live provider, real-file extraction, actual worker/sandbox cancellation,
separate-VM containment, billing reconciliation, backup/restore, production operation or
remote CI check ran. Nothing was pushed, merged or deployed. Basic queue/reconnect UI
now exists, but precise dependency/revocation and complete legacy-history handling,
workspace dashboard-draft parity, files/artifacts/retention, controlled research, reviewed
dataset extraction and operational transition remain. Keep both preview/backend activation
off for normal builds, and keep the manager rollout blocked on its approved matrix.


## Continuation: durable policy invalidation and checkpoint integrity

V116 adds retained per-role policy revisions and database-maintained account revisions.
The previous hash covered current grant values, allowing revoke/regrant or grant addition
followed by deletion to recreate a historical stamp. The new revision changes on permission
insert/update/delete and account update, including changes that leave `updated_at` untouched.
Role revision rows survive grant deletion. The account and role revision are locked before
`PermissionService` authorization, and existing thread/lease/revision fences still apply.
This remains conservative whole-context invalidation, not precise dependency tracking.
Pre-V116 workspace stamps intentionally fail closed; existing experimental threads require
a new investigation. No source-ledger data or legacy Ask records are modified.

Checkpoint publication now rejects duplicate IDs, invalid/missing parents, forward/self
references, repeated session headers and invalid leaf IDs before replacing committed state.
It checks structure only; Pi still owns native context and session semantics. Rejected
publication preserves the last committed revision and running state. No Java transcript or
context reconstruction engine was added.

Regression coverage exercises restored grants, add/delete grant cycles, disable/reactivate
without changing the account timestamp, stale worker publication, explicit fresh threads,
and malformed checkpoints preserving committed output. Final verification is recorded below.


## Continuation: workspace dashboard-draft parity

The governed bridge retains the existing `CreateDashboardDraftTool` validation and
`ToolDispatcher` authorization. Its result now includes the validated query-only draft
alongside the existing widget/notices/provenance fields. Pi retains that result in its
native snapshot. The browser projection exposes the typed draft only after current
thread authorization; denied/error results expose no draft. Older snapshots without
a draft remain readable. No dashboard is written by the worker or reporting tool.

The workspace uses the existing `AnswerBlock` confirmation/save behavior and the active
live/demo API. Create and update proposals use existing dashboard endpoints and their
mutation-boundary authorization. The save payload contains title, filters, layout and
query configuration, never rendered metric values. Denied/failed saves remain visible
and require an explicit retry. Demo history contains a query-only weekly-sales draft;
its saves stay in the offline demo adapter. No new browser storage is introduced.

A PostgreSQL regression invokes the real reporting bridge, serializes the worker result,
commits it as a native tool-result entry and reads the draft back through authorized
job history. UI regressions cover reconnect rendering, no automatic save, denied-save
feedback, explicit create and update, and the exact query-only save payload. Existing
legacy Ask and dashboard tests remain part of the full regression suites.

Remaining implementation is still substantial: precise dependency labels/revocation,
legacy-history handling, separate-VM script runtime, quarantine/extraction/artifacts,
retention/deletion/restore, controlled public research, reviewed dataset extraction,
actual billing reconciliation and cross-midnight accounting. Missing operational
access does not block all local implementation; this continuation must not be treated
as completion of the user's full request. Owner pilot and manager onboarding remain
unapproved/unverified; both activation flags stay off by default. Main's two newer
commits are still unincorporated and untested with this branch.


Final combined-tree backend verification passed: `./gradlew spotlessApply test spotlessCheck`
completed in 6m 27s with 2,019 tests, five skipped, zero failures/errors. A preceding
full run passed before the draft/checkpoint additions and is not the final evidence.
Worker frozen install with lifecycle scripts disabled, typecheck, all 15 synthetic tests
and build passed. Frontend typecheck, lint and all 824 tests across 142 files passed.
The first frontend full run had one five-second timeout in an unchanged weekly-report
UI test while backend tests were running; the unchanged suite passed when run without
backend-test contention. No timeout or heap limit was increased.

The incremental Ripwire quality delta against `b5c42d0f` remains non-clean:
22 findings, five gating (four lexical lifecycle-test clone matches and integration-test
class growth). Other findings include complexity 18 in `WorkspaceMessages`, complexity
11 in snapshot validation, class size/churn and JUnit/JSX entry points classified as dead.
Targeted class edit checks found no incompatible callers but do not prove result-record
wire compatibility; the new serialization and schema tests exercise that separately.
The broad test gate exits 4 with 20 test files and 145 statically untested symbols,
2,480 ambiguous and 4,759 unresolved edges, and 134 unindexed files. This is not a clean
static safety verdict. Changed local links and all six recipe discovery paths resolve;
diff whitespace checks pass. Scope, owner-only grants and default-off activation remain.


The preview production build passed. Playwright passed both workspace and legacy Ask
fixture flows (two tests); desktop/narrow screenshots were inspected. The first sandboxed
browser attempt could not start its local server; the approved rerun on port 3117 passed.
All application APIs were intercepted. The browser fixtures cover lifecycle regressions;
the new draft create/update/error behavior is covered by the component and PostgreSQL tests.
No provider request, production operation, separate-VM containment, real-file extraction,
actual billing reconciliation, backup restoration or remote CI check ran. Nothing was
pushed, merged or deployed. This turn reached its execution budget with independently
actionable implementation remaining; it does not close the full migration request.

## Continuation: admission identity and midnight reservations — 16 November 2026

Continued from `6874c3d9` in `/tmp/goldys-pi-workspace-proposal`. The initial fetch
still observed main at `fca0b930`, highest migration V113. The two newer main commits
remain unincorporated; no local merge, push, production access or deployment occurred.

Admission now obtains its start instant from PostgreSQL and uses that same instant
for the 15-minute deadline and venue-day reservations. `WorkspaceBudgets` atomically
reserves A$5 on every Melbourne date touched by that window, in ascending date order.
If either day lacks capacity the whole admission rolls back. Cancellation and unknown
charges retain the reservations. This deliberately over-reserves near midnight; it is
not a provider receipt ledger or actual billing reconciliation. The worker's existing
per-investigation request ceiling remains unchanged. Provider-specific price/cap and
in-flight billing verification are still operational gates.

Reusing a job submission ID with different prompt text now returns `WORKSPACE_CONFLICT`.
V117 records a SHA-256 digest of the original follow-up prompt; editing its queued text
does not alter its admission identity. Resending the original payload returns the
current command, including edited/taken/cancelled state, without replay. Different
payloads conflict. Original payloads of pre-V117 commands cannot be established, so
re-enqueueing those IDs fails closed; their existing edit/remove/take behavior remains.
The digest is worker-internal persistence metadata, not a second text transcript or a
browser field. The offline demo implements matching conflicts and retains original
fixture input only in memory.

New PostgreSQL tests cover Melbourne midnight, daylight-saving transitions, concurrent
reservations (only ten of twenty admissions succeed), and transaction rollback when
the next day is exhausted. Existing lifecycle tests now check duplicate-content
conflicts and retries after queue edits/removal. An initial focused run passed the
new budget cases but failed two older assertions that assumed exactly one daily row:
the run occurred just before Melbourne midnight. Those assertions now validate all
reserved days. Final verification is recorded below after completion.

This does not complete slices 2–3 or the migration. Precise dependency labels/revocation,
legacy-history treatment, separate-VM scripts, quarantine/extraction/artifacts,
retention/deletion/restore, controlled public research, reviewed dataset extraction,
actual billing reconciliation and remaining live/demo UX are still independent work.
Owner pilot/operational acceptance and the manager permission matrix remain outstanding.
Both activation flags remain default-off; existing Ask remains operational.

The worker also now treats local lifecycle cancellation as irreversible. If a periodic
heartbeat fails while an earlier authorization is still pending, a later success cannot
start a prompt or provider request. The guard checks the local abort signal before and
after model authorization, and the runner checks it before each prompt. A synthetic
regression with failed then recovered heartbeat delivery makes one provider call against
the old runner (expected failure), and zero with the fix. The native session tree,
compaction ownership, checkpoint microtask and durable queue semantics are unchanged.
This is not a network-partition/containment acceptance test.

Final backend checks passed: `./gradlew spotlessApply test spotlessCheck`, 6m 19s,
2,022 tests, five skipped, zero failures/errors. Worker frozen install with scripts
disabled, typecheck, all 16 synthetic tests and build passed. Frontend typecheck, lint
and all 824 tests across 142 files passed, without simultaneous backend-suite load.

Ripwire against `6874c3d9` reports 24 incremental findings and five gating findings:
two broad lexical constructor/delegation clone families and three short-horizon churn
findings. Advisories include demo factory complexity increasing from 25 to 29 and
Spring/JUnit entry points marked dead. No unrelated clone-family code was rewritten.
The WorkspaceJobs constructor gained its budget dependency; targeted edit checks show
no incompatible callers. `runClaim` retains its signature and caller compatibility.
The broad test gate lists 17 test files and 132 statically untested symbols, with
2,482 ambiguous edges, 4,762 unresolved edges and 135 unindexed files. This remains
non-clean static evidence, not a clean safety verdict. Both changed-document local
links resolve, all six recipes are discoverable through canonical and Codex paths,
and whitespace checks pass.

Continuation boundaries: V117 is unused on fetched main (still V113). Worker fix is
local commit `8f7f8cae`; the admission fixes and this evidence are committed separately.
No live provider, actual billing reconciliation, real uploaded-file extraction,
separate-VM containment, restore rehearsal, browser flow or remote CI verification ran
in this continuation. No production operation, push, merge or deployment occurred.
There is no blocker preventing all remaining local work: this remains an incomplete
implementation continuation, not completion of the authorized migration. Continue in
this same worktree with precise dependency/history handling before the dependent
sandbox/file/research slices. Do not infer pilot readiness from these regression results.

The preview production build also passed with `NEXT_PUBLIC_WORKSPACE_PREVIEW=true`.
The turn ended before completing the remaining authorized implementation; passing
these checks and making local commits does not close the migration request.


## Continuation: main integration and observed reporting dependencies — 16 November 2026

Reused `/tmp/goldys-pi-workspace-proposal` and rebased the unpublished task branch onto
`origin/main` at `fca0b930`. The only conflicts were adjacent imports in `demo.ts`,
`live.ts` and `types.ts`; both the main-branch sorting APIs and workspace APIs were
retained. The rebased pre-change head is `3694f6bd`. Earlier local commit IDs in this
record describe the pre-rebase history. No production checkout switch, merge, push
or deployment occurred. Main's highest migration was still V113; this continuation
adds V118 after the branch's V114–V117.

V118 stores reporting authorization evidence before a successful governed response
leaves Spring. `PermissionService.observe` captures the actual synchronous checks,
including metric operands and denied discovery probes, using a Java 25 scoped value.
It neither grants access nor replaces the permission lookup. Duplicate checks are
collapsed; exceptions unwind the scope; concurrent requests cannot share evidence.
The reporting transaction stores a job-bound record and returns an opaque evidence ID
in native tool-result details. No prompt, metric values or second transcript are stored
in that evidence table. Its job FK cascades with job deletion.

A new assertion exposed that `CREATE_DASHBOARD_DRAFT` authorized `dashboards` but did
not declare the metrics in its query-only proposal. The tool now supplies all referenced
metrics to the dispatcher's existing operand gate, including derived metric dependencies
through `MetricCatalog.requiredPermissions`. No queries run just to authorize metadata;
no worker dashboard save is introduced. This applies to both the legacy and Pi bridge.

These are reporting dependency labels, **not complete precise revocation**. Whole-context
policy invalidation remains unchanged. Captured checks do not establish lineage for
arbitrary user prose, uploads, scripts, legacy messages or summaries. Old snapshots and
unknown dependencies must remain conservatively restricted; do not loosen the policy
stamp based only on the new evidence IDs. No asynchronous reporting operation may
claim coverage from the synchronous observation scope.

Verification and the wall-clock lease regression are recorded below after their runs.


An independent startup control, `app.workspace.accepting-jobs=false`, now pauses new
jobs and follow-ups without unregistering history/Stop endpoints. Existing idempotent
submission IDs still resolve, and conflicting content still conflicts. The pause runs
before reservations or new durable commands; failed new-thread admission rolls back its
thread insert. Reads, queue edits/removal and Stop retain normal current authorization.
The [rollout preparation](../operations/pi-workspace-rollout.md) documents the distinction
between admission pause, cancellation and complete endpoint deactivation. Existing jobs
may continue while admission is paused; this is not an emergency process kill switch.
Both workspace activation flags remain default-off.

The first full rebased backend run with reporting evidence and dashboard operand checks
passed in 6m 22s. It predates the admission-pause and transaction-time additions and is
not final verification for this continuation. A previous focused run correctly exposed
the missing draft operand gate; a subsequent test compilation failure was corrected by
using the existing mock seam for package-private `PermissionLookup`.


Lease/deadline comparisons and renewal now use PostgreSQL `clock_timestamp()`, not
transaction-start `now()`. Admission also obtains its start from database wall time;
initial and renewed leases are capped by the original deadline. Four PostgreSQL
regressions hold a transaction open across the job deadline or lease expiry and then
attempt heartbeat or snapshot publication. All four failed against the old code
because the expired operation succeeded. The final full run includes those cases.
This closes the transaction-time loophole at the reporting post-query/checkpoint
fences; it is not evidence of physical process termination in a sandbox.

Incremental Ripwire evidence at `3694f6bd8+dirty` is **not clean**: 29 findings, seven
gating. These include two broad lexical constructor/delegation clone families, one
lifecycle-test clone match, three short-horizon churn findings and growth of
`PermissionServiceTest` from 43 to 116 measured lines. The new tests exercise denied
checks, exception unwinding and concurrent scope isolation; no unrelated constructor
family was rewritten to suppress lexical matches. The test gate lists 25 test files,
157 statically untested symbols, 2,483 ambiguous edges, 4,857 unresolved edges and
136 unindexed files. `WorkspaceJobs` has an intentional constructor change for the
admission configuration; the targeted check finds no incompatible callers. The Java
compiler and integration tests verify actual construction. These results do not
resolve earlier migration-wide findings or prove runtime wire compatibility.


Continuation scope remains incomplete. Use this same worktree/branch; current main is
now incorporated, so do not repeat the old "two commits behind" handoff. Continue with
complete context/legacy provenance and resource-level revocation (including revoke/regrant
cycles), keeping the conservative stamp until every relevant dependency is accounted for.
V118 evidence is not sufficient to relax it. Then complete the separate-VM sandbox,
quarantine/extraction/artifacts and retention, public-only research and citations,
reviewed dataset extraction, actual usage reconciliation and remaining live/demo UX.
The rollout runbook is preparation, not completed packaging or operational acceptance.

No independent implementation blocker was established for all that work. Operational
acceptance separately needs an authorized analysis VM/runtime and containment exercises,
approved provider execution/secret injection and verified receipts/pricing, representative
authorized files and a restore environment/rehearsal. Those were not supplied or exercised
within this authorization. Manager mapping, sensitive-file reviewer grants and
issued-report/hold/backup retention decisions remain genuine policy gates. Neither the
legacy Ask loop nor the other Spring AI consumers have been retired. No live provider,
real-file extraction, separate-VM containment, actual billing, restore, production operation,
remote CI, push, merge or deployment occurred in this continuation.


Final backend verification for all changes in this continuation passed:
`./gradlew spotlessApply test spotlessCheck`, 6m 49s, 2,050 tests, five skipped,
zero failures/errors. All 27 workspace lifecycle cases passed, including the four
transaction-time regressions and admission pause. Worker frozen install with lifecycle
scripts disabled, typecheck, all 16 synthetic tests and build passed. Frontend typecheck
and lint passed; its heavy tests/build run after the backend suite. Final frontend and
browser outcomes are recorded below.


Frontend verification on the rebased tree passed typecheck, lint and all 843 tests
across 146 files. The test count includes main's newly incorporated UI coverage.
The preview production build and fixture browser results follow below.

The preview production build passed with `NEXT_PUBLIC_WORKSPACE_PREVIEW=true` set
only for verification. Playwright passed both existing Ask and workspace fixture flows
(two tests, 17.5s). All application APIs were intercepted. Desktop and narrow screenshots
were inspected; visible Stop, queue controls and explicit missing data remain intact.
These are fixture browser results, not live provider, server-to-sandbox cancellation or
containment evidence. No persistent activation setting was changed.

All 43 local links in the changed documentation resolve; all six Goldy's recipes remain
discoverable through canonical and Codex paths; diff whitespace checks pass. Scope
requirements and architecture invariants are preserved. Current main was fetched again
at `fca0b930` with highest migration V113, so V118 does not collide with main.
The draft operand fix is local commit `c8d68f6f`; the evidence/lifecycle/runbook work is
committed with this record. Nothing was published or CI-verified remotely.

This turn reached its execution budget with substantial independently actionable work
remaining. It is a handoff, not completion of the requested entire migration. Continue
from this worktree's latest local commit, following the remaining implementation and
operational/policy distinctions above; do not discard the rebased implementation or
mistake passing local checks for deployment readiness.

## Continuation: response-bound evidence, legacy history and worker image — 16 November 2026

Reused `/tmp/goldys-pi-workspace-proposal`, branch `codex/pi-workspace-proposal`,
starting at `b030e0379`. Required fetch/status/main-log/PR inspection confirmed clean
state, fourteen commits ahead and none behind `origin/main` (`fca0b930`), highest
main migration V113. This continuation adds V119 and V120 without modifying an
existing migration. Nothing is pushed, merged, provisioned or deployed.

V119 binds new reporting evidence to the registered tool name and a SHA-256 digest
of the server response, excluding only its generated evidence ID. Object key order
and numeric scale are normalized across JSON/JSONB round trips. No second response
body or transcript is stored. Before snapshot publication, Spring validates newly
introduced/changed reporting results against evidence from the current job. Both
Pi's model-visible text and its browser-visible details must match. Altered values,
unknown/unbound IDs, another job's evidence and mismatched tool names are rejected
before the committed snapshot/revision is replaced. Unchanged native entries already
committed to this thread may survive explicit next attempts; copying an older job's
result into a new entry does not confer a new evidence binding.

This is response integrity and reporting provenance, **not complete dependency
tracking or selective revocation**. Unchanged old entries keep their unknown status;
V118 evidence is not retroactively bound. Prose, user-supplied figures, summaries,
compaction, scripts and files still require conservative dependency treatment.
Whole-context account/role policy invalidation remains mandatory. No Pi-native
context reconstruction, automatic replay or browser-supplied snapshot is introduced.

V120 adds nullable legacy conversation policy stamps. New legacy conversations record
the same retained account/role generations used by workspace authorization, checked
under their existing `conversational.threads` permission resource. New reads, list
previews, context reuse, rename, answer persistence and every outgoing SSE event are
checked. Revoke/regrant and disable/reactivate cycles permanently invalidate earlier
context. Unknown pre-V120 conversations are restricted, including their titles and
previews, and cannot be resumed or silently relabelled with today's grants. Migration
does not delete their bytes. Existing authorized owned deletion remains available.
Legacy Ask can create fresh conversations; its engine and other Spring AI consumers
remain present. No legacy transcript is imported into Pi.

The legacy conversation-history screen now forces fresh authorization on mount,
withholds cached titles/previews/transcripts until that read finishes, hides retained
query data on any read failure, and rechecks visible history every ten seconds.
Denied transcript views retain a Back action. The same component/API selection serves
live and offline demo; no new endpoint or live call from demo is introduced. Already
delivered content in an open legacy Ask drawer or external copies is not erased by
this change. That distinction remains relevant to full transition acceptance.

The worker now has a multi-stage image with an allowlisted build context, frozen
production dependencies, disabled lifecycle scripts, compiled runtime modules,
unprivileged UID/GID `65532` and disabled dotenv discovery. The Bun 1.4.2 build stage is pinned to
registry index `sha256:9114c058aeae42162ee16dd5084b95fe9473970bb6bcb5b232ab1630f0546895`.
The local linux/amd64 build ran typecheck, all sixteen synthetic tests and compilation.
The empty-configuration image smoke run, with network disabled/read-only root/no host
mounts/dropped capabilities/resource caps, exited 1 on missing
`GOLDYS_SPRING_ORIGIN` before service access. This is packaging evidence, **not an
analysis sandbox, configured-worker run or containment evidence**. CI includes the
image build and smoke check but has not run remotely. No host-installed scanner was available; a pinned container scan and runtime
remediation are recorded below. The rollout runbook records the
separate-VM, supervisor, secret injection and egress requirements.

Verification results and continuation state follow below after final checks.


Final backend verification passed `./gradlew test spotlessCheck` in 6m 34s:
2,060 tests, five skipped, zero failures/errors. This includes the evidence binding,
PostgreSQL legacy policy/regrant and SSE late-output regressions. The earlier full
7m 2s run predates the final legacy changes and is not the final evidence. One focused
compile failed on a missing Mockito test import; it was corrected before the final run.
Frontend typecheck, lint, all 846 tests across 146 files and the preview production
build passed sequentially; the heavy frontend suite ran after backend completion.
Three Playwright fixture flows passed in 29s (legacy revocation, existing Ask and
workspace). All application APIs were intercepted. Desktop/narrow denied-history
screenshots were inspected. Early cache tests used the wrong nested query provider;
correcting the fixture made the tests exercise the actual screen cache.

The final worker runtime is Bun 1.4.2 **distroless**, pinned to registry index
`sha256:1a0c31c7c5f9d193aedf60fe1cebdeb76ac8f6e29f24be8dd8cbd6df72df26ec`.
Its production dependencies passed sixteen synthetic tests again on that exact base;
the final image excludes those test sources. The original Debian runtime scan found
three critical/64 high findings. The final distroless scan found zero critical/high,
16 medium, eight low and three unknown findings. Remaining findings are not waived;
see the [dated scan evidence](../operations/pi-worker-image-scan-2026-11-16.md).
CI now builds, smoke-tests and scans the worker; remote CI has not run.

Static evidence remains **non-clean**. At `b030e0379+dirty`, Ripwire reports 53
incremental findings, 20 gating: one dead-code classification on an overloaded
factory still used by repository tests, twelve lexical clone groups, four
clone-of-helper findings, two churn findings and SSE-test class growth. Constructor/
controller/permission-check templates and short test fixtures dominate the clone
families; no unrelated business code was rewritten to suppress them. This does not
resolve earlier migration-wide findings. The test gate at that baseline reports 102
test files, 247 statically untested symbols, 2,489 ambiguous and 4,880 unresolved edges,
and 138 unindexed files. After the evidence commit, the remaining-diff test gate at
`4ece80895+dirty` reports 52 test files and 169 untested symbols. Those are different
comparison scopes, not a claim that static coverage improved. The intentional
ConversationService constructor change has no incompatible callers in the targeted
check; actual construction is verified by compilation and integration tests.

Local source commits so far: `4ece8089` (response-bound evidence), `10ed8752`
(legacy policy/history/cache handling). Packaging is local commit `cc820f61`; this combined evidence is committed separately. Both activation flags remain default-off. No live-provider
request, authorized real-file extraction, separate-VM containment, actual billing
reconciliation, restore rehearsal, owner pilot, production operation or remote CI ran.

### Exact continuation boundary

This execution reached its implementation/context budget before completing the entire
requested migration. This is an incomplete handoff, not deployment readiness. Reuse
this worktree and its latest commit; main is already incorporated. V119/V120 are now
used locally, while the last fetched main still ends at V113. Do not discard the
existing fourteen-commit implementation or repeat the pre-rebase handoff.

Continue with complete dependency labels and conservative unions across context,
prose, summaries and future files/scripts, keeping whole-context invalidation until
coverage is proven. Legacy server history is now stamped/restricted, but previously
delivered output in an already-open legacy Ask drawer is not erased. Then implement
the still-missing separate-VM script runtime, upload quarantine/bounded extraction
including scans, artifact/download/Keep/retention/deletion/restore paths, controlled
public research/citations, reviewed dataset extraction, durable usage/receipt
reconciliation and remaining live/demo UX. Worker packaging now exists; analysis
packaging and coordinated operational supervision/observability are still missing.
Review/apply the remaining available image patches and rescan before release.

These are remaining implementation tasks, not merely unavailable operational tests.
No blocker preventing all local implementation was established. Operational acceptance
separately needs the authorized analysis VM/runtime, approved provider secret injection
and actual receipts/pricing/caps, representative authorized files, isolated coordinated
restore and owner pilot. Manager mapping, sensitive-file reviewer grants and
issued-report/hold/backup obligations remain policy decisions. Keep existing Ask
operational and do not retire it until replacement acceptance permits transition.

Final documentation checks resolved 20 local links, confirmed all six recipes through
both discovery paths, and passed whitespace, CI YAML and embedded-shell syntax checks.
Scope/decision documents, permission seeds, activation defaults and applied migrations
were not changed. The local work is not published or remotely CI-verified.

## Continuation — live synthetic website pilot, 16 November 2026

This section supersedes earlier statements that sandboxing, files, research and live
integration are wholly absent. It does not supersede production activation gates.
The user directly approved Docker/gVisor installation and service configuration on
192.168.5.42, selected DeepSeek, and installed the dedicated credential in the
root-owned file we created. Its value was never printed or committed. No subagent,
production operation, push, merge or public ingress change was used.

### Implemented integration

- `analysis-sandbox` now provides a single-slot gVisor broker, immutable analysis
  image selection, offline Python, bounded staging/output, active-content checks,
  extraction/OCR, cancellation, descendant/orphan cleanup and real VM acceptance.
- The trusted distroless Pi worker receives only dedicated credentials, its fixed
  Spring origin, public TLS CA and broker socket. UID-specific IPv4/IPv6 egress
  allows Spring, DeepSeek and existing DNS resolvers. Scripts receive no network,
  database credentials, host checkout, provider key or Docker socket.
- V121 stores private original/artifact bytes and manifests transactionally, with
  ownership/policy checks, a 1 GiB retained-byte admission ceiling, thirty-day
  expiry, Keep, private downloads and deletion. Every file inherits the whole
  thread's conservative policy; arbitrary sensitive uploads require owner-only
  file access plus wages/users permission. This is not selective declassification.
- V122 distinguishes failed and budget-exhausted attempts. V123 records reviewed
  model/search request bounds before dispatch. Automatic SDK/agent retries are
  disabled. Completed attempts release only unused admission capacity; unknown
  requests and interrupted attempts retain reservations. Native provider token
  usage persists in Pi snapshots. Provider invoice/FX reconciliation is still open.
- Governed dataset export covers daily net/gross/GST sales for 1–31 days through
  the existing semantic reporting tools. Missing cells stay missing. Files become
  available for subsequent offline exploration; no generic SQL is exposed.
- Public research uses four closed topics and official `.gov.au` citations. Search
  receives no user text, figures, filenames, private context or arbitrary URL.
  Free-form public queries and other datasets remain explicitly unsupported.
- Live and offline-demo file APIs/UI are separate. The preview offers upload,
  extraction, generated artifact download, Keep/delete, exploratory/partial states,
  citation links and conversation deletion. Drafts require an explicit browser
  save. Whole-context revocation remains conservative across files, summaries,
  scripts and prose; precise dependency declassification remains a production gate.
- Dashboard tool guidance now includes accepted chart names and omits optional
  null fields, following an observed live model validation failure.

### Actual verification

The detailed record is [preview evidence](../operations/pi-preview-evidence-2026-11-16.md).
The private test entry point, account retrieval, scenarios and operations are in
[preview operations](../../ops/pi-preview/README.md).

Observed real integration: governed A$9,100 synthetic sales answer; upload/extract/
Python sum 42 and private CSV download; browser Keep; governed dataset artifact;
public research with persisted clickable citations; explicit dashboard save;
history/reconnect; browser Stop terminated active code in 4.2 seconds and cancelled
queued input; worker kill produced interruption and explicit Retry completed;
actual budget exhaustion preserved output and stopped further dispatch; browser
conversation deletion denied stale access. These are live Spring/Pi/DeepSeek/gVisor
paths, not mocked evidence. Missing/denied permission cases also have backend and
browser fixture regressions; those remain distinct from live provider evidence.

Separate-VM checks covered filesystem/identity/environment isolation, IPv4/IPv6,
DNS/private/metadata/loopback denial, memory and file limits, symlink/hardlink
rejection, cancellation/deadline/descendants and broker-loss orphan cleanup (0.25s).
100 MiB input and output passed both the broker and actual worker round trip under
their configured memory caps. Five synthetic document/image formats recovered 42;
CSV formula output was rejected. Representative business-file accuracy is unmeasured.

An isolated preview PostgreSQL backup was restored into
`goldys_pi_restore_verified` in the same dedicated preview container. File-byte and
native-snapshot hashes and reservation totals matched. The earlier count-only
rehearsal also passed. This does not establish production backup, legal hold or
issued-report policy. Both small restore databases remain isolated test resources.

Full backend verification: 2,067 tests, five skipped, no failures/errors; Spotless.
Frontend: 847 tests, type checking, lint and live-preview build. Worker: 20 tests,
type checking/build and the same tests inside the exact runtime image. Four Python
boundary tests and real VM checks passed. Three browser fixture tests passed
(workspace plus legacy Ask); actual browser flows above were checked separately.

Exact worker/analysis image scans found zero high/critical findings. Worker retains
16 medium/eight low/three unknown; analysis retains 78 medium/ten low. These are not
waived. Ripwire remains non-clean: final application-diff quality check reported
73 gating findings and its name-based test gate 275 untested symbols; framework
hook ambiguity and actual complexity/size findings both remain. Contract check
identified the controller constructor change; callers/tests were updated. No
static-clean, remote-CI or full production-readiness claim is made.

### Runtime and continuation boundary

- Development VM 192.168.4.50: private runtime files `/tmp/goldys-pi-preview`,
  PostgreSQL `goldys-pi-preview-db` on loopback 5548 with its own volume, Spring
  HTTPS loopback 18443, Next.js loopback 3117, dedicated user-systemd units and
  private SSH reverse tunnel. Real production resources were not inspected/changed.
- Analysis VM 192.168.5.42: `goldys-sandbox` and `goldys-pi-worker` services;
  `/opt/goldys-agent` fixed scripts/images, `/etc/goldys-agent` root credentials.
  The worker's model is reviewed `deepseek-flash` via `goldys-deepseek` overlay.
- User access: `ssh -N -L 3117:127.0.0.1:3117 stiring@192.168.4.50`, then
  `http://127.0.0.1:3117`. Dedicated account is `pi-owner@example.test`; retrieve
  its random password privately from `/tmp/goldys-pi-preview/test-account.txt`.
- `ops/pi-preview/preview.sh` documents/implements existing-preview status,
  logs, stop/start/restart. It reapplies synthetic resolved fixtures after Spring
  startup. `/tmp` and transient services are disposable, not reboot-proof hosting.
- At the accounting checkpoint, A$30.852 of the A$50 venue-day capacity remained
  reserved, including stopped/unknown work. This is a conservative reservation,
  not actual billed spend. It was not reset to manufacture acceptance.
- Production gates remain manager grants, sensitive-file/retention/hold policy,
  representative extraction, precise dependency declassification, actual invoice/
  FX reconciliation, owner acceptance, outstanding static/image findings and
  remote CI. Default activation remains off and legacy Ask remains available.

Reuse this worktree and the local commits. Do not reset to main, publish without
approval, treat these synthetic results as production acceptance, or read production
credentials/data. The documented stop/start procedure was rehearsed successfully; after restart Spring health was UP, the login page returned 200, both analysis services were active, and browser sign-in/history/citations/deleted-history denial passed again. The startup helper waits for projection rebuilding before reapplying synthetic fixtures. The next product step is the user's private website pilot.


Local implementation commits for this continuation: `59f3b6d0` (analysis runtime)
and `3a047e98` (Spring/Pi/frontend integration). Operations/evidence are committed
separately. No commit was pushed. The last fetched main remains `fca0b930`; local
migrations V121–V123 do not collide with its V113 maximum.

### Ask entry-point completion — 16 November 2026

The interrupted UI correction is now complete and verified: the preview's single
Ask Goldy's button, Ctrl/Cmd+J and command-palette action open Pi, while the
activation-off build retains legacy Ask. Pre-stream legacy errors accept JSON.
Frontend verification passed (848 tests, type checking, lint, build), as did two
preview browser fixtures and a fresh live A$9,100 reporting answer with persisted
history after reload. See the latest section of the
[preview evidence](../operations/pi-preview-evidence-2026-11-16.md).

The existing private preview is running. Sign-in still belongs to the isolated
Spring backend on the main VM; production accounts/data are not connected.
Unresolved manager/sensitive-file/retention decisions remain excluded from the
synthetic owner pilot. No production rollout, push or merge was performed.


### Existing production login/domain preparation — 16 November 2026

The user's next requested state is Pi replacing Ask inside the normal production
website with existing credentials. `ops/pi-production/README.md` now provides the
one-time activation procedure. It preserves the existing Spring accounts/session,
DB and public domain; a loopback-only TLS bridge and persistent reverse SSH tunnel
connect the existing separate analysis VM. The production worker is installed with
an immutable image ID and a dedicated service token, and conflicts with the preview
worker. No production setup command has been run.

The shared Compose activation flag enables Spring and the Pi frontend build without
a synthetic banner. Files are disabled independently in Spring and the browser;
no script sandbox is available to the production worker. Existing manager grants,
private history and conservative legacy-history restrictions remain unchanged.
A token-authenticated readiness endpoint checks connectivity without claiming work.
PostgreSQL integration tests exercise the ordinary password login through Pi job
ownership, CSRF enforcement, signed-out/cross-account denial and disabled files.

Local frontend, browser, worker, deployment-contract and private-TLS smoke evidence
is in the [rollout runbook](../operations/pi-workspace-rollout.md). Production setup,
remote CI and owner acceptance with actual accounts/data remain unexecuted. The
private synthetic preview remains running. The branch is prepared for review and
user-controlled publication; do not infer push, merge or deployment authorization.

Final backend verification passed: `./gradlew test spotlessCheck` completed in
6m 6s; 2070 tests, 0 failures, 0 errors and 5 skipped. The full suite needed
a 2 GiB test heap instead of Gradle's 512 MiB default. Its reservation fixture
cleanup now acquires locks in the scheduled projector's resolved-to-canonical
order, fixing the deadlock exposed by the earlier run. These are test-runtime
changes, not production JVM or database settings.
