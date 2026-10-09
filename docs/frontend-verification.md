# Frontend verification and baseline

## Status

Phase 0 source audit and local baseline; migration not implemented. Audited commit:
`f6140b8e2fa13d0c26f1e168c2dfe0bc689e0a18`, 2026-10-09. Proposed architecture requires review.
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

Recommendation: review identity/transport/query ownership first, then implement the first
small foundation slice. Subsequent business-widget/entity exploration work should build on
validated documents, explicit provenance, supported query semantics and measured pagination;
drag/drop layout, SQL-like querying and additional global state are not justified by this audit.
