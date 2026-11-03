# Frontend verification and baseline

## Status

Phase 0 source audit and local baseline, followed by PR 1 implementation evidence below. Audited commit:
`f6140b8e2fa13d0c26f1e168c2dfe0bc689e0a18`, 2026-10-09. The architecture and first-slice plan
were approved for native execution.
No improvement is claimed. Local checks are not GitHub CI results.

## Environment and reproducibility

- Linux workspace `/srv/ai/projects/goldys`, Bun 1.4.2, Node 22.22.1.
- Existing frozen frontend lock: Next 15.5.25, React 19.3.0, TypeScript 5.9.3, Vitest 5.0.1.
- Existing node_modules lacked declared Geist, Sonner, cmdk and several Radix packages.
  Restored with `bun install --frozen-lockfile`; no manifest/lock change intended.
- Initial typecheck/build/test failures were retained as environment diagnostics. The first
  post-install typecheck raced Next build deleting/regenerating `.next/types`. Sequential
  typecheck after build passed. **Do not run build and typecheck concurrently in one checkout.**
- Existing untracked root package-lock makes Next infer the workspace root and emit a warning.
  It was not deleted; it is pre-existing user/environment work.
- Logs and temporary browser harness: `/tmp/opencode/goldys-frontend-audit/`.

Reproduce from `frontend/`: frozen install, typecheck, lint, test, build sequentially. Run a
production server on a free port with `bun run start --hostname 0.0.0.0 --port 3100`.
Current output is standalone; Next warns that standalone server.js is the deployment entrypoint.
The audit used next start for local measurements, not a production deployment.

## Executed checks

| Command | Final result | Wall time | Notes |
| --- | --- | --- | --- |
| `bun install --frozen-lockfile` | exit 0 | not measured | Restores existing declarations |
| `bun run typecheck` | exit 0 | 7.23 s | sequential after successful build |
| `bun run lint` | exit 0 | 7.73 s | no lint diagnostics |
| `bun run test` | exit 0; 43 files, 186 tests passed | 19.68 s (Vitest 18.92 s) | after restoration; chart/jsdom dimension warnings and Vite config warning remain |
| `bun run build` | exit 0 | 78.22 s | compiled in 25.9 s; includes lint/type validation |

Wall times are `/usr/bin/time -v` on this machine; build/test overlap makes these diagnostics,
not isolated performance benchmarks. Initial outcomes: typecheck exit 2 (missing modules),
build exit 1 (missing modules), tests exit 1 (4 suites could not import; 171 tests passed).
Parallel build/typecheck race produced TS6053 generated-file errors, not a product regression.

No backend tests executed: backend inspection was source-only. No remote workflow run created
or inspected. Current CI omits frontend tests (`.github/workflows/ci.yml:43–45`). No coverage
instrumentation run; **coverage percentage unknown**.

## Production build size baseline

Next's route report (not total download size or a runtime latency measure):

| Route | Route size | First Load JS |
| --- | --- | --- |
| `/dashboard` | 4.44 kB | 341 kB |
| `/dashboards` (includes editor) | 9.52 kB | 363 kB |
| `/data` | 6.99 kB | 268 kB |
| `/data-health` | 9.68 kB | 408 kB |
| `/reconciliation` | 16.4 kB | 269 kB |
| `/resolution-rules` | 7.66 kB | 269 kB |
| `/conversations` | 5.24 kB | 225 kB |
| Shared JS | — | 186 kB |

Source: `build-restored.log:25–49`. No `next/dynamic` call found in TSX. Evaluate graph/editor
split against actual chunk/interaction measurements, not route size alone.

## Browser baseline methodology and limitations

Desktop browser tool was disconnected; Playwright MCP could not find its configured Google
Chrome. Used already-installed cached playwright-core and Chromium 1243 through a temporary
Node harness, without adding frontend dependencies. Headless Chromium, 1440×900, fresh isolated
context for each of three runs per route, local HTTP production build on 3100, no CPU/network
throttling, server/build cache warm after build. PerformanceObservers record LCP, layout shifts,
long tasks and event durations. Harness/screenshots/raw JSON remain in the audit temp folder.

Demo mode uses adapter delays of 300–400ms and local memory; business reads therefore produce
no HTTP requests. `/api/me` still makes a real request and returns proxy 500 because no backend
listens at localhost:8080. This is a reproducible **demo-with-auth-outage** baseline, not live
user performance, authorization or API compatibility verification. Console is not clean:
every run records the failed profile request. No live API deduplication measurement is possible
from these runs.

The harness waits for a route marker, then samples after 1200ms to let demo adapters settle;
its `readyMs` includes that fixed wait and must not be called time-to-interactive. Editor timing
starts after that sample, opens templates, creates Daily Management, and waits for the Title
input. It measures the whole template-create/editor-entry workflow, including demo delays,
not an isolated render cost. Fresh context prevents saved demo dashboards leaking across runs.
Observer CLS here is the sum of non-recent-input layout shifts in the observation window,
not a field session-window score. Event durations are lab observations, not field INP.

All nine runs completed with harness exit 0. Playwright-core 1.63.0; installed Chromium build
directory `chromium-1243`. Per-run observations (milliseconds unless noted):

| Route / run | TTFB | FCP | Observed LCP | Long tasks (individual durations) | Sample elapsed including wait | Template → editor entry |
| --- | ---: | ---: | ---: | --- | ---: | ---: |
| Overview 1 | 215.9 | 368 | 1140 | 88, 148 | 1817.01 | — |
| Overview 2 | 34.9 | 152 | 868 | 76, 108 | 1542.78 | — |
| Overview 3 | 29.4 | 152 | 868 | 74, 123 | 1547.30 | — |
| Explorer 1 | 30.2 | 148 | 148 | 74 | 1536.53 | — |
| Explorer 2 | 23.5 | 144 | 144 | 73 | 1542.47 | — |
| Explorer 3 | 24.7 | 180 | 180 | 65, 67 | 1577.40 | — |
| Library/editor 1 | 24.3 | 144 | 692 | 81, 64 | 2346.58 | 1710.90 |
| Library/editor 2 | 17.7 | 160 | 656 | 69, 99 | 1840.68 | 1676.74 |
| Library/editor 3 | 34.5 | 140 | 684 | 85, 68 | 2338.09 | 1656.08 |

Median observed LCP: Overview 868ms, explorer 148ms, library 684ms. Median template-create/
editor-entry flow: 1676.74ms. LCP is a paint candidate in this short window, not proof that
all data/controls are ready; explorer's heading paints before delayed rows. Editor entry is
an interaction after initial load and has no independently measured navigation LCP.
Observed non-input layout-shift sum: 0.00004503 on every run. Maximum recorded event duration
in library/editor workflows: 64/56/56ms (three runs), not field INP. No event-duration samples
on Overview/explorer because the harness did not interact with those routes.

Each run recorded exactly one HTTP API response: `/api/me`, status 500; zero HTTP business
reads in demo mode. Encoded script resource bytes were 402,046 / 434,750 / 417,153 for
Overview/explorer/library respectively, identical across each route's three runs. These can
include prefetched script resources and are not the same metric as Next's First Load JS.
The raw browser JSON includes resource paths and observer entries; screenshots capture each
route's first run (library screenshot after entering the editor).

Live authenticated baselines remain an outstanding Phase 0 item; use approved fixtures/server
and matching roles, never production destructive actions for benchmarking. Rerun after
implementation under the same mode, viewport, server entrypoint and observer window before
claiming gains. Use the deployed standalone server for the subsequent production-like baseline.

## Required regression matrix for implementation

| Area | Evidence required |
| --- | --- |
| Transport | HTTP status/correlation/cause, all HeadersInit shapes, CSRF, empty bodies, malformed JSON/envelopes, redirects, network vs abort |
| Query scope | same-key dedupe, cancellation, demo/live hydration, logout/login, same-role sessions, focus revalidation, no stale publication |
| Actions | pin/template/delete/restore/rules/connectors/overrides/conversations pending/success/failure; retry=false; no unhandled rejection |
| Dashboard | historical document compatibility, denied widget, dirty refetch/leave/save races, explicit preview/history errors |
| Explorer | URL round-trip/back-forward, filter/page reset, current-page sorting label, absent/zero/empty distinction, keyboard details/provenance |
| Chat/widgets | cancel/reset/replacement/unmount, split SSE frames, malformed answer diagnostics, supported versions, trusted registry |
| Monitoring | safe user messages, single-owner reporting, header correlation, no sensitive payload/query values, preserved relay |
| CI/browser | full Vitest + high-value Playwright, screenshots/artifacts, matched cold/warm production measurement protocol |

## Completion metrics — audit-only checkpoint

| Requested metric | Current evidence |
| --- | --- |
| Custom infrastructure removed/simplified | 0 product lines; documentation-only audit |
| Duplicated patterns consolidated | 0; seven candidate families in audit |
| Dependencies added/removed | 0 / 0; 45 direct declarations retained |
| Requests eliminated/deduplicated | 0 measured; source shows shell/page overlap and pipeline fanout |
| Before/after performance | build/demo baseline only; no after implementation |
| Error paths corrected | 0; failures inventoried and planned |
| Tests/coverage/CI | 186 passing local tests; coverage unknown; remote CI unverified |
| Remaining debt | all migration slices, stable identity limitations, no rich explorer schema/sort contract, concurrency semantics, live regression evidence |

Recommendation after the first foundation slice: proceed to scoped Query and Overview through
their separate plan. Subsequent business-widget/entity exploration work should build on
validated documents, explicit provenance, supported query semantics and measured pagination;
drag/drop layout, SQL-like querying and additional global state are not justified by this audit.

## PR 1 — transport and authentication implementation

Branch `fix/frontend-transport-auth`, based on approved planning commit `7f6acd2`.
The original audit-only checkpoint above is historical; the following records this slice.

### Problem, solution and preserved behavior

- Headers objects/tuples lost values; aborts became network failures; HTTP status, parse causes
  and header correlation disappeared. Native fetch now preserves these, with explicit response
  contracts and one internal response parser (`lib/api/response.ts`).
- Empty successful deletes/upload/logout are accepted only in void mode. Reservation 204 is
  explicitly optional; required JSON 204/empty bodies remain diagnosable protocol errors.
- Error envelopes retain valid codes even when optional metadata is malformed; diagnostics
  contain paths/codes. Unknown/5xx backend messages do not become user-facing copy.
- Zod validates all profile-returning auth responses; CurrentUser is inferred from that schema.
  Future role strings and extra backend fields remain compatible; no subject id is invented.
- Profile verification aborts/fences previous requests, clears stale presentation identity,
  and consumes late success/rejection. 401/recognized same-origin auth redirect is sign-out;
  403, integrity, proxy and network failures are persistent errors with retry/correlation.
- Logout, login and signup have explicit pending/failure outcomes and duplicate-submit guards.
  Successful signup followed by failed auto-login preserves confirmed creation and offers
  sign-in rather than another account-creation request. Network-uncertain signup says so.
- Three auth error/correlation blocks share AuthFeedback; confirmed-creation and signed-in
  presentation are focused components. Local form state stays local. No Query/cache migration,
  arbitrary-widget execution, authorization change or new global state manager is included.
- Frontend CI now runs Vitest and pins Node 22.22.1 alongside Bun 1.4.2. Native fetch was kept;
  Axios, global-state wrappers and migrating simple forms to RHF were rejected for this slice.

### Regression evidence

Observed RED runs: errors 2 failing; request handling 8 failing; response boundary 35 failing;
decoder/prototype-key edge cases 3 failing; auth response validation 4 failing plus missing
schema module; identity/logout 16 failing with unhandled rejections; auth forms 9 failing.
Tests were then made green; no original tests were deleted or disabled.

Complete suite before final review: **53 files / 290 tests passed** (baseline 43/186;
104 additional tests). Signup recovery and SDK privacy fixes expand this to **54 files / 297
tests** (111 added), with typecheck/lint/build passing after the fixes.
Typecheck and ESLint pass without suppressions. Production build
passes after scoping the standalone trace root; all 21 static pages were generated and
the standalone server starts successfully with copied public/static assets. Native audit exits 1
with **16 pre-existing advisories (9 high, 7 moderate)**, also reproduced on the untouched
baseline. Browser results are below; final independent review is recorded separately after completion.

MSW tests use real adapters/HTTP handling; race tests deliberately use promises that ignore
AbortSignal. Multipart tests use Node's native File/FormData because jsdom's File serialization
fails inside Node Request. React 19 StrictMode is enabled at the RTL root; Vitest 5 setup hooks
do not accidentally return mock functions as cleanup callbacks. Test-only setup is named
setupApiServer, not a React hook. Coverage percentage remains unknown; remote CI is not run.

### First Load JS after PR 1

| Route | Audit baseline | PR 1 | Difference |
| --- | ---: | ---: | ---: |
| Overview | 341 kB | 367 kB | +26 kB |
| Custom dashboards | 363 kB | 388 kB | +25 kB |
| Data explorer | 268 kB | 293 kB | +25 kB |
| Login | 201 kB | 226 kB | +25 kB |
| Signup (after review fix) | 201 kB | 227 kB | +26 kB |

Next/React versions are unchanged; shared framework/Sentry JS is 187 kB (baseline 186 kB). The auth schema
foundation adds approximately 25 kB to affected route loads. This is a measured bundle cost,
not a performance improvement. Selective/lazy schema loading can be evaluated in the later
performance slice. No comparable before/after runtime latency measurement was collected here.

### Dependency audit triage and release prerequisite

| Group | Existing affected path / assessment | Follow-up |
| --- | --- | --- |
| brace-expansion, braces | Build/lint/Sentry bundler glob processing; current inputs are repository files/patterns | Review compatible transitive fixes; not automatically exempt from CI/build risk |
| Next 15.5.25 | Two self-hosted SSG/ISR cache-poisoning advisories; patched range starts 15.5.27 | Separate tested Next patch update before production release |
| PostCSS 8.4.31 | Next's older nested version; direct/Vite PostCSS 8.5.28 is newer | Review Next/transitive source-map fixes against actual CSS build inputs |
| postcss-selector-parser 6.1.4 | Tailwind CSS selector processing | Review compatible fix; keep frontend-generated CSS free of untrusted documents |
| sharp 0.35.4 | Next image optimizer transitive runtime; no next/image consumers found, but framework endpoint remains available | Do not declare unreachable solely from imports; upgrade/review optimizer path before release |
| source-map-js 1.2.1 | PostCSS/build and jsdom/test source-map processing | Review fixed compatible version and verify build/test |

No forced audit remediation or major upgrades were applied. This is an unresolved release
prerequisite, not a passing security check or permission to deploy.

### Structural review and rulings

Ripwire's explicit `7f6acd2..HEAD` comparison identified auth UI growth. After extracting
actual repeated feedback and presentation, SignupPage complexity fell from 26 to 10 and
UserMenu's major verbosity finding disappeared. LoginPage remains 67 lines vs 60; this
bounded growth implements guarded submissions and accessible feedback without another
form/state wrapper. The tool still reports that finding; no thresholds/acks were weakened.
New parser complexity and JSX-only dead-code candidates are visible review signals, not
proof of runtime failure. Full suite/typecheck/browser checks verify affected consumers.

Rulings: response parsing is a focused internal module; installed MSW 3 uses onUnhandledFrame;
native Node multipart fixtures supplement browser checks; root StrictMode/block-bodied test
hooks preserve lifecycle semantics; test setup naming/cancelActive resolve lint without
suppression; pre-existing audit findings need a separate release remediation; auth feedback/
presentation extraction responds to measured duplication; bounded LoginPage/parser growth
is retained for explicit ownership. Next trace root is explicitly this independent frontend
package: the parent lockfile widened worktree output and the first 120s pipeline timed out
after compilation during tracing. Future external workspace imports need explicit trace inclusion.

### Migration, rollback and remaining debt

No wire endpoint, database or saved document format changes. Revert response policies and
void caller changes together; schema/type changes together; auth provider/UI as their unit.
Remove Zod/MSW only after checking imports and restoring the prior lock. Trace-root rollback
must restore the deployment's intended standalone layout, not delete parent lockfiles.

Query/cache isolation, remaining mutations, dashboard/widget schemas, editor dirty-state,
stream cancellation and monitoring completion remain in the roadmap. Current profile has no
stable subject; external same-profile session replacement is not identifiable. Original
redirect status/correlation is hidden by followed browser fetch. Shared Sentry beforeSend
sanitizes linked API cause messages while preserving causes locally; future custom reporting
must preserve that boundary. Live backend/test credentials remain unavailable; controlled
HTTP fixtures are not proof of production authorization. No API deduplication or latency
improvement is claimed by this slice.

### Built-browser regression results

Production standalone server, cached Playwright-core 1.63.0/Chromium build 1243, fresh contexts
at **1440×900 and 320×900**, same synthetic route handlers. Twelve scenarios completed (six per
viewport): profile outage/retry, login failure/success, partial signup, logout success, logout
failure/reverification, including malformed-success signup recovery. Captured screenshots preserve the current typography/cards/tokens;
partial-signup at 320px has no horizontal document overflow.

| Scenario (each viewport) | Observed HTTP sequence | Verified outcome |
| --- | --- | --- |
| Profile retry | `/api/me` 500 → 200 | Demo Overview remains available; Owner control absent until verified; retry restores profile |
| Login | login 500 → 200; `/api/me` 200 | Safe alert/correlation, then navigation to Overview |
| Partial signup | signup 201; login 401 | One creation, confirmed account state, sign-in link; no create button |
| Malformed signup success | signup 201 with invalid JSON | Uncertain recovery with diagnostics/correlation, no repeated creation or auto-login |
| Logout success | `/api/me` 200; logout 204 | Pending button, one logout, identity cleared and success toast; no redundant profile GET |
| Logout failure | `/api/me` 200; logout 500; `/api/me` 401 | Failure consumed, uncertainty toast, reverified signed-out state |

No page exceptions/unhandled promise failures were observed in any scenario. Success logout
has zero console errors; negative scenarios contain only the expected browser “Failed to load
resource” messages for deliberately injected 401/500 responses. This is controlled HTTP/browser
evidence, not live Spring authorization verification or a clean-console claim for outages.
The harness scopes alerts to the application's paragraph: Next also supplies its own route
announcer with role=alert. Screenshots and raw test/build/browser logs are archived in
`/tmp/opencode/goldys-auth-verification/`; they contain synthetic fixtures, not live credentials.

### Independent review and final fixes

The fresh reviewer examined `7f6acd2..91299eb`, found no critical issues, one important
signup-recovery gap and one minor custom-abort-reason limitation. Incomplete/malformed/body-read
failure after signup 201 was reproduced (three failing tests), then fixed without accepting
the invalid profile or repeating creation. A fourth test preserves confirmed creation when
auto-login returns invalid success. Browser recovery checks run on both viewports.

An additional actual SDK reproduction proved that default LinkedErrors copies cause messages
into events. Three reporting-boundary tests were observed failing, then passed with shared
beforeSend applied to browser/server/edge configs. Original causes remain available locally;
linked API cause text is withheld from telemetry. Safe code/status/correlation tags retain
diagnostic linkage; invalid metadata is not reported. Unrelated exception reporting and the
self-hosted relay remain intact; no duplicate capture path was added. Final complete suite:
**54 files / 297 tests passed**; typecheck/lint/build pass. No second reviewer was dispatched.

Deferred minor: cancellation with an explicit non-AbortError reason can be classified as
network/protocol failure. Current controllers use default AbortError; cover supplied-signal
state before introducing cancellation variants in later Query work.

### Slice completion accounting

- Infrastructure mechanisms removed: **0**; useApiData remains for its own migration. The
  transport entrypoint is 16 lines shorter while a 113-line response module adds reliability;
  this is responsibility separation, not a claimed net infrastructure reduction.
- Pattern families consolidated: **2** — three auth feedback blocks and three profile
  invalidation sites. CurrentUser's handwritten interface is replaced with schema inference.
- Dependencies: **2 added / 0 removed / 45 retained** (Zod runtime, MSW development).
- Requests: successful logout avoids the old unconditional profile refresh (source comparison
  plus controlled post-change request counts). No shared-query deduplication or live before/after
  request reduction is claimed.
- Bundle cost: +25–26 kB on primary routes; shared JS +1 kB. Runtime latency/coverage percentages
  remain unmeasured. All original 186 tests retained; 111 added; CI includes tests but remote
  workflow results remain unverified.
- Next work: scoped Query/Overview with identity-aware cache clearing, then library/actions,
  high-risk dashboard/widget schemas, editing, exploration and stream lifecycle. Dependency
  advisory remediation and live verification are prerequisites before a production release.
