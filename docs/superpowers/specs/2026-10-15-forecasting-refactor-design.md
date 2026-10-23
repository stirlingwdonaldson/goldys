# Forecasting Refactor — Design

**Status:** Draft
**Date:** 2026-10-15
**Scope:** Rework Goldy's `/forecasting` to the depth and honesty of the external forecast pipeline
(`github.com/stirlingwdonaldson/forecast`): a naive baseline, feature importance, day-by-day
breakdown, error-by-weekday, biggest misses, and a forward forecast driven by **future bookings**.
Design only; the executable plan will follow as `docs/superpowers/plans/YYYY-MM-DD-forecasting-refactor.md`.

This supersedes (and extends) `docs/superpowers/specs/2026-10-12-timesfm-forecasting-design.md`. It keeps
that design's run lifecycle, leaf-package boundary, and TimesFM inference service; it adds a diagnostic
gradient-boosted twin, external drivers, future bookings, and a food/bev split.

---

## 1. Objective

Staff get a forecast they can trust *and interrogate*: a forward revenue forecast with an uncertainty
band, a naive baseline to judge it against, feature importance explaining what the model is keying off,
a per-day breakdown of the drivers behind each day, and an honest backtest (residuals, error by weekday,
worst misses). The strongest forward-looking signal — the bookings already on the book for future dates —
feeds the model.

Six concrete outcomes:

1. **Forward forecast** of **net revenue** (`sales.net`, ex GST) with a **p10–p90 band** (TimesFM),
   split into **food / beverage**, over a 7/14/30/90-day horizon.
2. **Seasonal-naive baseline** shown alongside the forecast (bars + dashed line), and an explicit
   "if the model isn't beating the baseline, roster to the baseline" posture.
3. **Feature importance** and model-vs-baseline accuracy (chronological holdout + expanding-window CV),
   from a diagnostic gradient-boosted model.
4. **Day-by-day breakdown** — every forecast day with the drivers known at forecast time: future
   booked covers, public holiday (+ eve / day-after), school holiday, university session, weather.
5. **Backtest** — fitted vs actual vs baseline, residuals, predicted-vs-actual scatter, error by
   weekday, and the ten **worst misses**.
6. **Future bookings** — the covers already on the book for upcoming dates, adjusted for how far out
   they are (book "maturity"), as the model's leading feature.

## 2. Decisions

| # | Decision | Choice |
|---|---|---|
| A | Forecast engine | **TimesFM 3.0 stays** as the forward net-revenue quantile forecast. TimesFM 3.0 conditions on **future-known covariates** via `past_future_covariates` (booked covers, holidays, weather, school/uni), so future bookings *do* drive the headline number. TimesFM is the more accurate model; the twin is the more transparent one. |
| B | Transparency model | A **diagnostic XGBoost twin** (in the same Python service) provides feature importance, baseline comparison, backtest, and the **food/bev share** model. It is *not* the headline forecast — TimesFM is. It shares the same feature matrix TimesFM receives. |
| C | External drivers | **All of them now**: public holidays + school terms (DataVic), university terms (manual YAML), trading-hours weather (Open-Meteo). Fetched and disk-cached in the Python service (Approach A — see §3). |
| D | Target | **Net revenue + food/bev split.** TimesFM forecasts net; the XGBoost share model splits net into food/bev (the reference's "forecast net, then split" finding: bev is ~3× as volatile as food, so modelling them separately drags the stable series around). |
| E | Future bookings | A new resolved quantity **`reservations.booked_covers`** (Σ party size over non-cancelled, non-walk-in reservations for a date) feeds the model. For future dates it is the book right now; for past dates the settled book. See §7. |
| F | Food/bev derivation | Derive from the **product classification** (category `group` = second level), mapped to food/bev by a small configurable rule. **Approximate by design** — documented as an outstanding task and flagged in the UI. See §8. |
| G | Service boundary | Extend the existing **`forecast-service`** (Python) from "TimesFM inference" into a full forecasting runtime (external features + XGBoost twin). The Java `forecasting` package keeps its current role: read resolved series via the semantic layer, orchestrate, persist. |
| H | Page structure | **Tabs on the one `/forecasting` page** (Forecast / Model / Backtest / Runs), matching Goldy's tab convention, not five routes. |
| I | Labour | **Fully deferred** — no lagged-labour feature, no labour-budget output. |
| J | Persistence | Extend `forecast_run` with a richer per-day series and a **`diagnostics` JSONB** column (§5). |
| K | Authorization | Unchanged: `forecasting` resource READ/WRITE, plus each metric's own `requiredPermission` READ on create and read. New metrics (`sales.food`/`sales.bev`/`reservations.booked_covers`) reuse their parent domains' permissions (`reconciliation.sales`, `reservations.metrics`). |

## 3. Architecture — where the pieces live

**Approach A** (chosen over full-ledger connectors and over a second Python service): the Python
`forecast-service` owns external-feature fetching and the XGBoost twin, because (a) it already has the
ML stack, (b) it mirrors the reference's single-pipeline shape, and (c) external drivers are *reference
data* (weather, calendars), not *pub operational facts*, so they deliberately bypass the raw ledger →
canonical → reconciliation pipeline. This deviation is recorded in §10, alongside the `classification`
package's existing canonical read.

```
semantic layer (resolved, permission-checked)
   └── MetricQueryService.query(...)         # net, booked_covers, bookings, food/bev, ...
        │
        ▼
forecasting (Java, pure leaf: semantic + auth + JPA)
   ForecastService ──► ForecastModelClient (port) ──► forecast-service (Python)
                                                      ├─ external features (weather/holidays/school/uni)
                                                      ├─ feature matrix (calendar + lags + external)
                                                      ├─ XGBoost twin (net + share) ── diagnostics
                                                      └─ TimesFM ── net quantiles
        ▲
        │  {forecast: net q + food/bev + baseline + drivers, diagnostics}
   forecast_run (series + diagnostics JSONB) ──► ForecastingApplicationService ──► REST ──► /forecasting (tabs)
```

The Java side reads resolved data **only** through `MetricQueryService` (day grain, no dimensions),
exactly as today. External features never enter the Java side at all — the service fetches them and
returns them as part of the diagnostics/per-day drivers.

## 4. Python service changes (`forecast-service`)

Port, in spirit, the reference's `external.py`, `features.py`, `model.py`, `forecast.py` (and its
`opentable.py` maturity logic):

- **External features** — `external.py`: DataVic important dates (public holidays + school terms),
  university YAML, Open-Meteo archive + 16-day forecast, all disk-cached. Weather aggregated over
  **trading hours only** (Mon–Thu 15:00, Fri–Sun 12:00 → 23:00). Each fetcher degrades to `NA` columns
  (schema stable) on failure. Both endpoints verified live against the venue coords (2026-10-15).
- **Feature matrix** — `features.py`: calendar features (`day_of_week`, `is_weekend`, `is_friday`,
  `is_saturday`, `day_of_month`, `month`, `week_of_year_sin/cos`, `days_to_month_end`), lag/rolling
  features (revenue lags 7/14, rolling 7/28, `net_revenue_dow_mean_4`), and `booked_covers` (with
  future maturity top-up). **Leakage rules preserved**: same-day labour never a feature; lags/rolling
  always shifted; `booked_covers` allowed same-day (it is on the book before service).
- **Model** — `model.py`: two XGBoost targets, `net_revenue` (`log1p`) and `food_share` (clipped
  0.05–0.95). Chronological holdout (`test_fraction` 0.25) + expanding-window CV; score vs
  seasonal-naive baseline (mean of last 4 same-weekdays); `reliable` flag at `min_rows_for_xgboost`;
  feature importance (gain) from the net model.
- **Forecast** — `forecast.py`: recursive forward pass; TimesFM produces the net quantiles; the share
  model splits each day's net median into food/bev; the baseline is computed per day. **Book maturity**
  (§7): the feature is "covers on the book as of the forecast origin", and its future incompleteness is
  handled by a **measured** pickup model (Goldy's `createdAt` timestamps), not a guessed curve.
- **Contract** — a single `POST /forecast` replacing the thin TimesFM payload: request = internal daily
  series (`dates`, `net`, `booked_covers`, `bookings`, `food`, `bev`) + `horizon` + `quantiles`;
  response = `{forecast: per-day rows, diagnostics}` (§5 shapes). `GET /health` unchanged. The service
  builds the full feature matrix (calendar + external + `booked_covers`) across **history + horizon**,
  feeds the future-known columns to TimesFM as `past_future_covariates` (extending `predictor.py`, which
  today only passes `past_only_covariates`), and feeds the same matrix to the XGBoost twin as tabular
  features.

Config lives in env vars (mirroring the current `forecasting.timesfm.*` keys): feature toggles
(`include_weather`/`include_school`/`include_university`), coverage bars for `booked_covers`, labour
ratios are removed (labour deferred), `horizon_days`, venue coordinates/timezone, trading hours.

## 5. Persistence (one migration)

Extend `forecast_run` (Flyway migration; `hibernate.ddl-auto: validate` untouched — never DDL):

- **Richer series** — each day now carries, in addition to the quantiles:
  `net {median,p10,p90}`, `food`, `bev`, `baseline`, `bookedCovers`, `publicHoliday`,
  `publicHolidayName`, `publicHolidayEve`, `dayAfterPublicHoliday`, `schoolHoliday`, `uniInSession`,
  `serviceTempMaxC`, `serviceRainMm`, `weatherSource`.
- **`diagnostics` JSONB** — present only on `SUCCEEDED`:
  ```
  {
    modelVersion, trainedRows, reliable, warnings: [],
    features: [], shareFeatures: [],
    holdoutNetXgboost: {mae,rmse,mape,n}, holdoutNetBaseline: {…},
    holdoutFood: {…}, holdoutBev: {…},
    cvNetXgboost: {…}, cvNetBaseline: {…}, beatsBaseline: bool|null,
    importance: [{feature, importance}],
    backtest: { rows: [{date,day,actualNet,predictedNet,error,absPctError,
                        actualFood,predictedFood,actualBev,predictedBev,baselineNet}],
                byWeekday: [{day,n,mae,mape,meanActual,meanPredicted}],
                residualMean, residualStd, worstDays: [backtestRow…] }
  }
  ```
  Diagnostics are read-only run projections, queried as a whole (like the series), so JSONB is
  appropriate — no separate diagnostic tables.

## 6. Java changes (all within `forecasting` leaf + `semantic`)

| Unit | Change |
|---|---|
| `ForecastService` | Read the added internal metrics (`reservations.booked_covers`, `reservations.bookings`, `sales.food`, `sales.bev`); pass them to the service; persist the returned series + diagnostics. |
| `ForecastSeriesAssembler` | Build/parse the richer request/response; still pure and unit-tested. |
| `ForecastModelClient` | Broaden the `TimesFmClient` port (renamed) to the new `POST /forecast` contract; same port + JDK `HttpClient` impl + fake-for-tests pattern. |
| `ForecastRun` | Carry the richer `ForecastDay` fields and a `diagnostics` accessor. |
| `ForecastingApplicationService` | New DTOs (`ForecastDiagnosticsDto`, richer `ForecastSeriesPointDto`); the run DTO now includes diagnostics (or a sibling `GET /api/forecasting/runs/{id}/diagnostics` — TBD in plan). |
| ArchUnit | `forecasting` remains a pure leaf (semantic + auth + JPA only). New semantic metrics are added, not new leaf dependencies. |

## 7. Future bookings — `reservations.booked_covers`

Verified: `OpenTableApiConnector` pulls reservations through `today.plusMonths(FUTURE_MONTHS)`, and
`ReservationProjector` projects by trading date **including future dates**. The gap is semantic:

- `reservations.bookings` = reservation **count** (party size ignored).
- `reservations.covers` = party-size sum for **attended** reservations only → **0 for future dates**.

The reference's leading feature is "covers on the book" (Σ party size of booked reservations). To expose
it:

1. **Migration**: add `booked_covers BIGINT` to `resolved_reservation_day`.
2. **`ReservationProjector`**: in `Mutable.add`, also accumulate `bookedCovers += partySize` for every
   reservation whose status is neither `CANCELLED` nor `WALK_IN` (future `BOOKED` + past
   `SEATED`/`COMPLETED`/`NO_SHOW` — a no-show was still booked, a walk-in was not). Recompute-all
   backfills existing rows.
3. **`ReservationSummary`** + `ResolvedReservationDay`: expose `bookedCovers`.
4. **Metric** `reservations.booked_covers` (count, `reservations.metrics` permission) in the catalogue
   + `ReservationMetricExecutor` + `MetricCatalog`.
5. **Forecasting** reads it as a same-day covariate.

### Book maturity — measured, not guessed

The reference's OpenTable CSV lacks a booking-created timestamp, so it *fits* a pickup curve to a single
snapshot (one observation per lead time, R²≈0.3). Goldy's has the timestamp — `canonical_reservation.created_at`
(`createdDate` from GuestCenter / `Created Date` from the CSV) plus `cancelledAt`, `seatedAt`, `noShowAt`,
`finishedAt`, `lastUpdatedAt`. That lets the pipeline **reconstruct the book as of any past date**:

> `booked_covers(D, L) = Σ partySize` over reservations with `reservationAt = D`,
> `createdAt ≤ D − L`, and not cancelled before `D − L`.

Consequences, which the reference itself lists as its highest-value fix:

- The feature the model trains on and predicts from is the **same quantity**: "covers on the book as of
  the forecast origin". For historical rows the origin is the service date (settled book); for future
  rows it is the run's forecast date (partial book). The model learns to price in immaturity directly.
- A **measured pickup curve** — fraction of final covers on the book at lead `L`, aggregated by weekday
  and lead time over thousands of `(createdAt, reservationAt)` pairs — replaces the reference's single-
  snapshot fit, and drives the additive top-up `observed + (1 − maturity) × same-weekday average` for
  future dates (and `simulate_maturity_in_backtest` for honest backtesting).
- **Caveat to verify during implementation**: `createdAt` is the *original* booking time; a reservation
  whose party size changed after booking (`lastUpdatedAt` > `createdAt`) is reconstructed at its final
  size, so the measured curve is slightly conservative for late-modified large parties. Confirm
  `created_at` is actually populated for historical reservations (not null) before relying on it — if
  coverage is thin, fall back to the reference's fitted curve.

## 8. Food / beverage split (approximate, by design)

Derive `sales.food` / `sales.bev` from resolved product sales joined to the classification:

- `semantic.ProductCategoryMetricsQuery.salesByCategory(from, to)` already returns
  `CategorySales {group, category, quantity, amount}`, backed by `ResolvedProductCategoryQuery`
  (resolved product sales → `product_category_assignment` → `product_category`).
- **New daily-series metrics** `sales.food`, `sales.bev` (base) and `sales.food_share` (derived,
  `food ÷ net`) in the semantic layer, reading a **daily** variant of the category roll-up.
- **Mapping rule** (configurable): category `group` `"Food"` → food; every other non-null group
  (Beer, Wine, Spirits, …) → beverage; null group / `"Uncategorised"` / `"cash-out"` / `"merch"`-like →
  **excluded from the split** and reported. GST is 10% on both, so the food *share* is identical whether
  computed on gross or net product sales.
- **Measured coverage (trailing 12mo 2025-10-01 → 2026-09-30, total $887,469)**: food $399,446 + bev
  $478,751 = **$878,197 = 99.0%** of sales (food 45.5% / bev 54.5% within food+bev). The excluded
  (Uncategorised / cash-out / merch) remainder is ~1% — so the split is near-complete, not structurally
  approximate. This de-risks §13.4.

This is **deliberately approximate** — the classification's second level is not yet a canonical
food/bev flag (Lightspeed's own categories are messy: uncategorised, cash-out, merch, …). Two explicit
acknowledgements, per the owner's direction:

- **Docs**: an "outstanding task" note (in this spec and a follow-up in `docs/metrics/catalog.md`) that
  a proper second-level food/bev classifier is the eventual fix.
- **UI**: the food/bev split chart carries a small note ("food/bev split is approximate — pending
  category refinement"), so no one reads it as exact.

## 9. Frontend changes

`/forecasting` becomes a tabbed page (Forecast / Model / Backtest / Runs). New Recharts components
reusing `components/widgets/chart-style.tsx` and the shared Data Table:

- **Forecast** — run control (horizon), forecast bars vs baseline dashed line (`ComposedChart`),
  food/bev stacked split (`BarChart` stack), day-by-day list with driver chips (booked covers, public
  holiday + name, school hols, uni, temp/rain), and the full forecast table.
- **Model** — holdout/CV accuracy vs baseline table, "beats baseline?" banner, feature-importance
  horizontal bars (top-20 / all toggle), feature list, warnings.
- **Backtest** — actual vs model vs baseline lines, residual bars (over/under colour), predicted-vs-
  actual scatter with y=x, error-by-weekday bars + table, **worst misses** list.
- **Runs** — existing list, plus "View" to open a run's tabs.

New `Api` methods (live + demo, per the repo's `frontend-api-method` skill): `getForecastDiagnostics`,
and the extended forecast-result shape. Demo fixtures mirror the reference's snapshot shapes so the
page is fully browsable in demo mode.

## 10. Deviations & constraints

- **External features bypass the ledger** — weather/holidays/school/university are reference data, not
  pub facts needing reconciliation. Fetched + cached in the Python service. Documented like the
  `classification` package's canonical read.
- **TimesFM 3.0** remains non-commercial-licensed (internal use only) — unchanged from the prior spec.
- **Second runtime** — already accepted (prior decision K); this spec broadens, not adds, that runtime.
- **Two models, one forecast** — TimesFM is the headline number; XGBoost is explanatory. Where they
  disagree, the UI shows TimesFM as the forecast and XGBoost as the diagnostic, and the baseline is the
  tie-breaker for "should we trust the model".

## 11. Error handling & degradation

- External feature fetch failure → `NA` columns, schema stable, warning surfaced (reference behaviour).
- TimesFM down → run `FAILED` (as today); `failInterruptedRuns` keeps restarting from stranding a row.
- XGBoost failure → still return the TimesFM forecast, `diagnostics` null + a warning (never lose a
  forecast because the twin failed).
- Fewer than `min_rows_for_xgboost` history rows → `reliable=false`, "prefer baseline" warning.

## 12. Testing

- **Python `pytest`** (mirroring the reference's `tests/`): leakage assertions (same-day labour never a
  feature; lags/rolling shifted), booking-maturity behaviour, feature gating/coverage bars, baseline
  correctness, backtest shape.
- **Java**: `ForecastService` (mocked client), `ForecastSeriesAssembler` alignment, `ForecastingApplicationService`
  permission re-check, `ReservationProjector` booked-covers accumulation, ArchUnit (`forecasting` stays
  a leaf; new semantic metrics don't cross boundaries).
- **Frontend**: component tests for the new charts + page test (mocked `Api`), matching the existing
  `forecasting-page.test.tsx` pattern.

## 13. Open questions / outstanding tasks

1. **Second-level food/bev classifier** — the durable fix for §8. Outstanding, documented; approximate
   group-mapping ships first.
2. **Diagnostics endpoint shape** — resolved: diagnostics fold into the run DTO
   (`GET /api/forecasting/runs/{id}` carries a nullable `diagnostics`), no sibling endpoint.
3. **Book-maturity curve** — now *measured* from `canonical_reservation.created_at` (§7) rather than
   ported as a single-snapshot fit. Two things to confirm during implementation: (a) `created_at` is
   actually populated across historical reservations (not null), and (b) how much late party-size
   modification (`lastUpdatedAt` > `createdAt`) distorts the reconstruction. If either is thin, fall
   back to the reference's fitted exponential curve.
4. **Uncategorised products** — resolved: the excluded bucket is ~1% of sales (measured 2026-09-30,
   §8), so classification coverage is effectively complete for food/bev. The UI note should still say
   the split is pending a proper second-level classifier, but accuracy risk is low.

## 14. Out of scope

- Labour (feature or budget) — deferred by the owner.
- AFL/MCG fixtures, gigs, street events (the reference's known-limitation #3).
- Daily OpenTable book snapshots (would replace the maturity guess).
- Export to workbook/Excel; Ask Goldy's exposure of the forecast.
- Macro features (ABS/CPI/RBA) — near-useless at daily granularity.
