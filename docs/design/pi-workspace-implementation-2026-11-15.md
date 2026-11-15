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
