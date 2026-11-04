# Weekly report output baselines

These synthetic outputs were captured from the unchanged calculation implementation
at `678906af`, before the weekly calculation refactor. They characterise existing
behaviour; they are not new financial acceptance criteria or live source evidence.

`WeeklyReportOutputComparisonTest` calls the same `report(...)` interface as its
callers, with a fixed Melbourne clock and mocked reads. It compares the entire
serialised result, including numeric precision, nulls, array order, labels, units,
statuses, qualification wording, targets, wastage reasons, stock counts and metadata.
Object property order/formatting is also fixed by the test's Jackson writer; that
format is a test fixture convention, not an additional HTTP formatting requirement.

| Baseline | Inputs and behaviour exercised |
| --- | --- |
| `complete.json` | Seven supported sales/cost dates, target comparisons, classified wastage, review average, unknown stock-count flags and explicit catalogue weekly totals that differ from summed daily points. |
| `mixed_two_weeks.json` | Signed sales/costs, missing tax, unknown classification, unresolved gross, absent dates versus null points versus observed zero, partial costs, unvalued waste, missing/normalised reasons, trust notes, changed/zero targets, cross-week filtering and a current/future week. |
| `empty.json` | Default last-completed-week selection, absent observations/reviews/targets and withheld values throughout. |
| `cost_tax_withdrawn.json` | Supported theoretical cost amounts retained while withdrawing the tax assumption withholds their rates and changes qualifications. |

Run from `backend/`:

```sh
./gradlew test --tests '*WeeklyReportOutputComparisonTest' --tests '*WeeklyReportServiceTest'
```

Actual outputs are written to `backend/build/reports/weekly-report-output/`.
Tests never update expected files. During a behaviour-preserving refactor, investigate
every difference rather than replacing baselines. An intentional later behaviour
change requires reviewing both its assertions and its expected-output diff.

## Coverage limits before refactoring

- Mocked reads isolate assembly; they do not exercise SQL selection, effective-target
  persistence, permissions in the real database or transaction isolation. Existing
  PostgreSQL integration tests remain necessary, including `WeeklyReportIntegrationTest`
  and `ReportingRealWeekIntegrationTest`.
- Four scenarios are not every input combination. Keep the existing sales, inventory,
  cost and target tests for threshold/denominator cases, request validation, historical
  target edits and invalid target bounds. Report-level permission-denial tests exercise
  each of the seven currently required resources before any read.
- Screen/export rendering is outside this comparison. Existing frontend tests still
  protect it; no frontend code or transport mapping changes belong in this first step.
- The current-week warning is not propagated to every derived row: several calculations
  replace placeholder notes. The mixed baseline preserves that existing wording and
  placement. Improving it needs a separate behaviour change.
