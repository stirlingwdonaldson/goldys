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
