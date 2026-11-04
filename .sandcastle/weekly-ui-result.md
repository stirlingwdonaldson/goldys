# Weekly report UI trial

Local refinement on `codex/sandcastle-weekly-ui-pass`, 4 November 2026. Demo/test data only; browser servers bound to `127.0.0.1:3117`, with all API and monitoring requests intercepted. No backend, API contract, business calculation, field catalogue, demo fixture, export writer or dependency changes.

## Change and rationale

- Selected Monday–Sunday week, venue timezone and exact-snapshot export scope now precede three shared stat cards: sales ex GST, recorded wastage and weekly review rating. Qualifications, units and dates with values accompany the figures.
- Weekly history prioritises those measures. A selected-week target table uses effective settings and existing API sales differences. Assessments require matching units, an effective matching target and seven daily values; unavailable differences also suppress sales assessments. Wastage uses the existing maximum-target meaning. No new daily targets, review thresholds or prior-week growth calculations.
- Focused daily sales/wastage detail replaces the initial 85-row dump. Six labelled field disclosures retain all 85 rows, weekly/daily values, units and keyboard-accessible source-note popovers. Visible counts and notices preserve unavailable states. Wastage reasons and dated stock counts remain in a separate disclosure; dated target editing remains adjacent to comparisons.
- Payments, purchases, theoretical CTB estimates and unavailable actual COGS retain distinct meanings. Existing ink, gold action, shared components, number formatters and status tokens are preserved. Exact comparisons benefit from tables; no chart is needed to imply a trend across qualified periods.

Success condition: review the selected week and supported comparisons first, reach every field and note, preserve missing versus known zero, and complete the existing target/export workflow at desktop and narrow widths without page overflow.

## Commands and observed results

Run from `frontend/` unless stated otherwise:

- `bun install --frozen-lockfile`: passed, no dependency changes.
- Baseline: `NEXT_PUBLIC_DEMO_MODE=false NEXT_TELEMETRY_DISABLED=1 SENTRY_AUTH_TOKEN= bun run build`, then `bun run test:browser tests/browser/weekly-report.spec.ts`: build passed; 3 browser tests passed, 1 optional retained-snapshot test skipped. Before screenshots were inspected before implementation.
- Final sequence: `bun run typecheck && bun run lint && bun run test && NEXT_PUBLIC_DEMO_MODE=false NEXT_TELEMETRY_DISABLED=1 SENTRY_AUTH_TOKEN= bun run build`: typecheck/lint passed; 138 test files and 754 tests passed. Build passed. This complete sequence passed after the final narrow-screen adjustment.
- Final browser run: `bun run test:browser tests/browser/weekly-report.spec.ts`: 5 passed, 1 optional retained-snapshot test skipped (17.8s). Verified both 1280×900 and 390×900 workflows, all 85 field rows, source-note keyboard open/Escape/focus return, incomplete comparisons, target save, Excel download, loading, failure/retry/refresh, permission denial, missing data, URL range controls and no page overflow with detail tables expanded.
- `git diff --check`: passed. `git diff --exit-code` for backend, API/fixtures, export writer, targets utility, package manifest and lockfile: passed (unchanged).
- Independent Python standard-library ZIP/XML inspection of both downloaded XLSX files: valid archives, 7 sheets, both selected weeks, $90,000/$97,000 weekly sales, saved 0.5% wastage target and dated field notes present. Existing export tests also passed in the frontend suite.

Observed intermediate failure: the first refined browser run timed out on an exact-text inventory-disclosure locator because the label now includes counts (1 failed, 1 interrupted, 4 not run). The locator was corrected to select the disclosure with its count text. [Failure details](weekly-ui-evidence/initial-browser-failure.md) are retained locally. The next browser run passed 4 tests and skipped 1, but the recovery assertion matched both the report alert and Next’s route announcer. That assertion was scoped to the report error ([details](weekly-ui-evidence/recovery-browser-failure.md)); the following run passed all 5 fixture tests, with 1 optional skip. The full required frontend sequence and browser suite passed again after the screenshot-led narrow notice/disclosure spacing adjustment. No application failures remain in the observed fixture checks.

Existing tool warnings: Vitest's Vite config-loader notice, Playwright's colour-environment notice and Next's `start`/standalone warning. These are not claimed as fixes in this UI trial.

## Local evidence

Evidence files are retained locally and intentionally excluded from the focused commit.

- Before: [desktop viewport](weekly-ui-evidence/before-desktop.png), [desktop full page](weekly-ui-evidence/before-desktop-full.png), [narrow viewport](weekly-ui-evidence/before-narrow.png), [narrow full page](weekly-ui-evidence/before-narrow-full.png).
- After (inspected): [desktop viewport](weekly-ui-evidence/after-desktop.png), [desktop full page](weekly-ui-evidence/after-desktop-full.png), [narrow viewport](weekly-ui-evidence/after-narrow.png), [narrow full page](weekly-ui-evidence/after-narrow-full.png). Expanded workflow captures: [desktop](weekly-ui-evidence/after-desktop-expanded.png), [narrow](weekly-ui-evidence/after-narrow-expanded.png).
- State screenshots (inspected): [loading](weekly-ui-evidence/loading.png), [error](weekly-ui-evidence/error.png), [denied](weekly-ui-evidence/denied.png), [missing](weekly-ui-evidence/missing.png).
- Downloaded workbooks: [desktop](weekly-ui-evidence/after-desktop.xlsx), [narrow](weekly-ui-evidence/after-narrow.xlsx).

## Skipped checks and remaining limits

- Optional real-data browser test skipped: no retained snapshot was supplied (`GOLDYS_REAL_WEEK_REPORT` absent). No attempt was made to obtain production data.
- Backend tests, other browser suites, production services, deployment and external publication were outside this bounded frontend trial. No push, PR/issue mutation, merge or external communication.
- The required read-only Git preparation fetched successfully; `gh pr list --state all -L 10` could not run because GitHub CLI is unauthenticated. No login or credential inspection was attempted.
- Seven dates with values establish only the presentation guard; they do not certify source capture, accounting basis or operational accuracy. Recorded wastage costs and reviews remain qualified; actual inventory-based COGS remains unavailable. Narrow detail tables require horizontal scrolling inside their containers. The UI conservatively withholds incomplete assessments; the unchanged Excel scorecard and full catalogue retain the API’s supplied qualified values/differences.

## Host review and setup verification

- Sandcastle 0.14.0 ran Codex with a fresh ChatGPT subscription login and Bubblewrap.
  No API key or host login was copied. A typed, assert-checked adapter preserves
  automatic approval review while translating the removed Codex exec `-a` option.
- Tooling typecheck, frozen install, sandbox/toolchain preflight and Git ignore
  checks passed. Host-side read-only `gh issue view 249` and PR-list access passed;
  the sandbox's missing GitHub login is intentional. Optional issue context is
  fetched on the host, without forwarding its credential.
- The host inspected before/after desktop and narrow screenshots and reviewed the
  source. No changed API, financial calculation, permission or export-writer code
  was found. React review found no added fetches, dependencies or charting code.
- The review branch was rebased onto main after PR #252 landed. That intervening
  change affects backend calculations/tests only; the final frontend is identical
  to the frontend tested by the trial. No backend verification is claimed here.
- Ripwire quality-delta on the trial working tree exited 2: short-horizon churn and
  render verbosity increased in WeeklyReportContent; the new comparison guard has
  complexity 16 against a threshold of 15. It also reported component dead-code
  findings despite JSX use. Manual review and the rendering/browser tests confirm
  these callers. The guard's early exits retain explicit basis/completeness checks.
  These structural findings were retained without suppression; the static quality
  gate is not claimed as passing. Ripwire could not index the runner's .mts file;
  TypeScript and the actual sandbox/model run provide its execution evidence.

## Browser-feedback revision: deliberate report controls

4 November 2026. Native disclosure triangles were removed from the weekly report.
Small source explanations now use a circled information button with hover/focus
hints and click/tap access to full notes. Qualifications remain visible beside the
button. Target editing opens in a right-hand sheet from the target-table action.
The full field catalogue, wastage reasons and stock counts use a report-detail
sheet with section navigation, visible counts and unavailable states. Week, panel
and selected section are URL-addressable; Back/Escape, reload and focus return are
covered. No API, export, permission, catalogue or business-calculation changes.

The requested [interface-design skill](https://github.com/Dammyjay93/interface-design)
was installed globally at commit `2f9be3206855bcb2d1d0af262c8bae25cba6658d` after a
clean Ripwire skill scan. Its SKILL.md/frontmatter and Codex agent metadata were
checked; it is available to skill discovery on the next turn. Its guidance was read
and applied now, subordinate to Goldy's approved DESIGN.md direction.

Verification: typecheck, lint, 138 test files / 755 tests, and production build passed.
The final weekly-report browser suite passed 6 tests, covering 1280px, the reported
800px viewport and 390px; the retained-real-data case remains skipped. The separate
static demo bundle also passed rendering, all 85 fields, hover/focus/click notes,
target save, demo-labelled export and overflow checks at 800px. Source suites
continue to cover denial, unavailable values and recovery. Final test/build/browser
logs are retained in `.sandcastle/logs/controls-*.log`.

Browser testing found and fixed a nested Escape bug: closing source notes must
leave the report sheet open and return focus to its information button. The report
sheet explicitly defers Escape to an open notes popover. Earlier attempts also
exposed test timing/locator issues around Wastage versus Wastage reasons and waiting
for URL-based tab selection before counting fields or reloading. A Chromium path
typo and an interrupted check process were corrected before the final passing run.

Ripwire still exits 2 for short-horizon churn in TargetsEditor and WeeklyReportContent,
with a minor target-editor complexity increase and JSX caller false positives.
The inventory panel was split along its two actual tab views to remove the introduced
complexity regression. No static-warning suppression or unrelated cleanup was added.
WeeklyReportContent's callable contract is unchanged. The static gate is not claimed
as passing. Main data/permission/export contracts and design authorities are unchanged.

Visual evidence: [detail entry points](weekly-ui-evidence/controls-main-800.png),
[target editor](weekly-ui-evidence/controls-targets-800.png),
[field browser](weekly-ui-evidence/controls-fields-800.png).
The private demo uses the same components and browser-only fixtures. Production
services and live-data validation remain outside this preview. Goldy's GitHub
branch has not been pushed or merged.
