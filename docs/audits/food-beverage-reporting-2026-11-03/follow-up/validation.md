# Follow-up validation

Isolated checkout `/tmp/goldys-food-beverage`, branch `codex/food-beverage-reporting`;
reviewed base `e25a6abf`, remote main `6c01de71`. Remote state refreshed again before commit;
no remote branch already uses V110. Production checkout/compose/env files untouched.

## Actually run

- Read-only production probes in the adjacent SQL/CSV files: native site/code/raw observations,
  exact unknown waste lines, source daily totals, receipt adjustments, scope filters and date evidence.
  Initial exploratory SQL errors/timeouts were corrected against the actual schema and rerun.
  Detail 822 proves the small wastage header residual comes from source quantity precision versus
  the retained four-decimal quantity; no money or unit conversion was forced to balance.
- Owner `/api/me` authenticated successfully. Application API read 89 prior revisions, applied ten
  WASTE_LINE decisions, then reread 99 revisions and 107 classified lines. All 89 original revisions
  remained equal. No SQL write, model approval, source-ID equivalence, merge or deployment.
- Focused PostgreSQL reporting tests passed before the full gate, including the source-site boundary,
  effective date, exact gross/quantity, partial/mixed/UNKNOWN precedence and missing-tax safeguards.
- Deferred-preview frontend regression passed. It attempts exact-line, recipe and revision-history
  selection while pending, then verifies changing identity invalidates approval.
- Frontend full gate: `bun run typecheck && bun run lint && bun run test && bun run build` passed;
  **136 files / 738 tests**. Browser-only assertions were subsequently extended to check actual target
  differences and category-specific wastage rates; no production frontend code changed after the gate.
- Playwright desktop/mobile demo/report/mapping review, denied download, and live-adapter retained
  real-week report: **6/6 passed**, including the new CTB_PRODUCT identity preview, available food
  actual, target difference, category rates, waste totals and Excel download. Responses were
  intercepted at the API boundary using the real PostgreSQL-generated report, not a deployed smoke test.
- Independent openpyxl check matched all 79 rows, seven daily values, totals, nulls, statuses, notes
  and summary to that backend JSON. No workbook formulas. After the final backend gate, the real-week browser/download test passed
  again (1/1); the independent check also verified the final generated timestamp/date/timezone
  metadata. `verify-workbook.py` retains this check.
- First full backend `./gradlew test spotlessCheck`: **1,857 tests, zero failures/errors, five skips**,
  success in 6m3s. Preview evidence handling was then simplified and an unobserved-source rejection
  regression added; **final** `./gradlew spotlessApply test spotlessCheck` passed in **5m43s**:
  **1,858 tests, zero failures/errors, five skipped**.
- `git diff --check` passed.

## Structural review

Ripwire before-you-build, quality-delta, edit-check and test-gate ran. Editor and preview public
signatures are unchanged. Extraction removed the preview complexity regression. The final delta
still reports three gating heuristics: short-horizon class churn in ProductReportingMappingService,
and verbosity growth in the two reporting test classes. They are retained as explicit limitations;
the added regression coverage is required by the task. No suppressions or baseline changes were made.
The graph gate reports ten related tests and fifteen inferred untested symbols, with 2,400 ambiguous,
4,415 unresolved edges and 153 unindexed files. Framework/dynamic wiring and SQL semantics are not
proved by this graph; the actual local gates and PostgreSQL integration tests provide runtime evidence.
Neither Ripwire command is reported as a clean static gate.

## CI and release boundary

`gh pr checks 231` was read this session: all three jobs passed for the **existing e25a6abf**,
run 41616178504. This is not CI for the local follow-up. A new push needs explicit approval under
AGENTS.md; CI for that commit cannot run before a push. The prepared PR description must distinguish
applied wastage from source decisions awaiting merge/deployment/API application.

Unverified: production deployment/smoke test, CTB site equivalence/business cause, historical café
membership/closure dates, waste cost GST basis/completeness, and a new external closed-date
source export resolving the daily headline selection. Known remaining unknown sales are 82.71;
headline residual 604.7852 stays visible and unallocated. Saturday waste remains missing, not zero.
