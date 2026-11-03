# Validation and release boundary

Isolated checkout `/tmp/goldys-reporting-review`, branch `codex/reporting-review-queue`.
Main and PR state were refreshed twice; main remained `28751470365d6011276c4fb340f0cb5dce9aabea`.
V111 follows main's V110. No production branch switch, environment-file read, Compose operation,
production SQL write, source-system change, push, PR, merge or deployment.

## Actually verified

- The private owner session authenticated. All fifteen explicit decisions in `decisions.json`
  were previewed, appended via API with CSRF, and read back. Original 293 revisions remained
  equal; final history contains 308. `api-application.log` retains non-secret command results.
- Read-only production SQL was scoped by the established read-only probe and transaction.
  `review-new.*`, `review-evidence.*`, `review-pos*`, and `review-aioli.*` retain real native
  identities, source references, categories, ingredients and history. Initial exploratory column
  errors were corrected against the actual schema, and the complete queries reran successfully.
- The deployed weekly report was read after curation. The adjacent before/after JSON files retain
  every row and qualification. `review-final-recheck.*` independently confirms affected dates,
  unchanged historical values, exact recipe decisions and historical costs/reasons. No positive
  unclassified exposure remains on 2 November; 22 zero-value identities remain UNKNOWN.
- Focused PostgreSQL tests pass for queue ranking/aggregation, negative sales exposure,
  site/source separation, partial/mixed/UNKNOWN policies, effective-date revisions across a range,
  source basis mismatch, missing tax, unresolved sales, missing costs/reasons, empty dates,
  page bounds and permission-before-read. Existing write authorization and approval-race tests
  remain intact. The real 154-alias/154-native-observation/nine-wastage-line sample reproduces
  87.284597% / 2,107.5662 before and 100% / zero after, without changing the reporting threshold.
- Local browser checks use PostgreSQL-generated `backend/build/review-queue-before.json` and
  `review-queue-after.json` through the live HTTP adapter with intercepted responses. They are
  local browser/data-contract checks, not a deployed queue smoke test. Desktop/mobile verify
  ranking, exact identity and effective date, explicit preview/approval, refresh and no horizontal
  overflow. The first live fixture omitted existing browsing data and hid the tabs; it was
  corrected to include a category. No application change was needed for that fixture error.
- Demo/live API tests preserve nulls and bounded query parameters. Demo effective dates and
  same-day UNKNOWN withdrawals are tested; classified wastage with missing cost remains held.

## Final gate results

- `./gradlew spotlessApply test spotlessCheck`: **passed**, 6m23s; **1,862 tests, zero failures/errors, five skipped** (402 JUnit result files). Includes migration and architecture gates, PostgreSQL integration and existing mapping authorization/race safeguards.
- `bun run typecheck && bun run lint && bun run test && bun run build`: **passed** on final frontend code; **137 files / 743 tests**. Build and typecheck were sequential.
- Playwright mapping/queue tests: **4/4 passed** at desktop 1280px and mobile 390px; repeated against the final build after the demo revision-order fix.
- `git diff --check`: **passed**.

The first full backend run exposed a null-vs-zero queue summary for fully classified wastage;
it was corrected and the focused/full gates reran. An undated-wastage test initially tried to
insert a state prohibited by the actual schema; it was corrected to verify missing costs and
empty dates, with rejected-date handling documented explicitly. No schema constraint was weakened.

## Structural review and limits

Ripwire before-you-build, quality-delta, edit-check and test-gate ran. The application service
constructor adds the review and sales semantic interfaces; its direct test constructor was
updated, and Spring wiring is exercised by PostgreSQL integration tests. Public editor and
mapping preview signatures remain unchanged.

The precommit quality report retains thirteen gating heuristics: duplication of conventional
controller/JDBC/permission/row-mapping patterns and verbosity growth in the review service and
reporting tests. No baseline or suppression was added. The graph reports ambiguous/unresolved
edges and infers broad untested frontend reach through the Api interface. These are not clean
static gates and do not prove runtime behavior; the local full gates and focused PostgreSQL/
browser tests are the verification evidence.

The canonical schema rejects undated wastage. Such source records remain ingestion failures
for Data health review, rather than appearing in the date-bounded financial queue. The queue
does not infer dates or turn missing report days/costs into zero. Production-scale queue latency
has not been measured: its new views/API/UI are not deployed. Background notifications are not
implemented; below-threshold dates in the owner workflow are the deliberate minimal alert.

Unverified/out of scope: deployed queue behavior, new CI (no authorized push), historical café
membership/lifecycle, historical classification backfill, stock variance, wastage GST certification,
daily target allocation and explanation/allocation of the 604.7852 headline residual. The new
2 November residual 1.9538 also remains visible and unallocated.
