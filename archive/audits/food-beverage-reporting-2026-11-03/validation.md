# Validation — 3 November 2026

Base `6c01de71`, worktree `/tmp/goldys-food-beverage`, branch `codex/food-beverage-reporting`.

- Refreshed fetch/status/main log/PR list before creating the worktree. Production main remained
  untouched, including its untracked `docs/requirements/`.
- Existing read-only probe ran the retained SQL under `evidence/`; no database write session.
- Verified owner session via `/api/me`; category and mapping writes used supported application
  endpoints with session authentication and CSRF. No credentials are included in artifacts.
- Reread categories, manual overrides and reporting revisions after all curation. Counts:
  18 described categories; two MANUAL porterhouse assignments in Mains; 38 PRODUCT_NAME and
  51 CTB_RECIPE reporting revisions effective 26 October, one authenticated actor.
- Review-only ASSIGN completed for 2,659 candidates. Both manual corrections preserved;
  219 ambiguity holds retained. Selected evidence/proposals are saved; no automatic approval.
- Selected completed-week CTB product net/gross and quantities matched source tax for all 1,034
  resolved product/day rows. Source priorities and the independent Lightspeed report still differ;
  see README for exact unallocated discrepancies. No fresh live independent source export acquired.
- CTB wastage header comparisons: 10/11 completed-week records exact, record 161 has a
  -0.0019666667 residual. Older each/kg record 149 exact. No source-completeness/tax certification.

## Runtime gates

Initial focused report/migration tests exposed the old fixed row-count assertion; the typed report
contract was updated from 61 to 79 rows. The first complete frontend gate passed typecheck, lint,
737 tests in 136 files and production build. First complete backend gate passed **1,852 tests,
zero failures/errors, five skipped**, plus Spotless (6m10s).

The real-week regression subsequently added all 1,034 CTB source observations, 107 waste lines,
89 reviewed mapping revisions and the seven resolved headline observations. A CSV reader compile
error was corrected to the installed Commons CSV Builder.build() API. Its targeted runtime test
and category target-comparison tests passed, emitting `backend/build/reports/real-week-report.json`.
The regression confirms the exact accounting in README and preserves unavailable actuals/rates.

Final frontend gate passed again: typecheck, lint, **136 files / 737 tests**, production build.
Local production-build Playwright checks passed **6/6**: desktop/mobile report and mapping review,
explicit denied download, and the backend-generated real-week snapshot/download. The browser used
intercepted API responses, including the exact PostgreSQL test-generated JSON; this is not a
production deployment smoke test. Screenshots confirmed the mobile mapping editor fits the viewport.
Independent `openpyxl` validation matched all 79 real-week rows, each seven-day array, weekly totals,
nulls and statuses to that backend JSON; workbook contains no formulas.

The next full backend run had **one failure**, PlatformMapCompletenessTest: the new catalogue source
domain lacked freshness/source metadata. Added LIGHTSPEED/CTB to its application.yml declaration.
Also tightened source-tax qualification to require a non-null site observation and tested it.
**Final source backend gate:** `./gradlew spotlessApply test spotlessCheck` passed in **5m40s**:
**1,855 tests, zero failures/errors, five skipped**. The real completed-week regression and stricter
site guard passed. A subsequent added exact-line preview regression passed in the focused
`ProductReportingIntegrationTest` run (28s), proving impact 3 × 4.13 = 12.39 without a saved revision;
Spotless passed again. The frontend count-unit union was aligned with the two observation-count rows;
its final `bun run typecheck` passed. The added preview test class passed all seven tests.

## Structural review

Used Ripwire before-you-build, quality-bar and change-check. The working-tree quality delta reports 135 findings (67 gating) and the test-gate lists 77 tests
and 260 inferred untested symbols, with framework/dynamic graph limitations. It flags new complexity in demo calculations and numerous inferred duplication/dead-code findings, including
framework methods, thin controllers, DTO factories and repeated test assertions. These are not
reported as a passing static gate. Catalogue/weekly allocation was consolidated into
`ClassifiedSalesTotals` to avoid two tax/coverage policies. No blanket baseline or suppression added.

The initial qualified method selector was unresolved. `--edit-check=WeeklyReportService` resolved
and correctly identified the constructor changing from seven to eight parameters, with zero inferred
incompatible callers (graph limits: 2,399 ambiguous/4,310 unresolved edges, 146 unindexed files at
that measurement). Updated direct test construction and Spring wiring were separately compiled and
run. PR context is a bounded structural read, not data-flow or exhaustive coverage proof.

## Release boundary

Taxonomy and the 89 reviewed mapping decisions are already applied. Report/editor code and V109
remain local; no push, PR/CI, merge or deployment claimed before explicit approval. No ingestion,
source mutation, production configuration, reconciliation policy or historical fact replay.
