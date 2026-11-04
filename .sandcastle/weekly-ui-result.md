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
