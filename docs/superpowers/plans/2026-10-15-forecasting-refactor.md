# Forecasting Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework Goldy's `/forecasting` into a transparent, future-bookings-aware revenue forecast: TimesFM 3.0 (headline, with `past_future_covariates`) plus an XGBoost twin (feature importance, baseline, backtest, food/bev share), external drivers, and the reference repo's full chart set.

**Architecture:** The Java `forecasting` package (a pure leaf over `semantic` + `auth`) reads new resolved quantities — `reservations.booked_covers` and `sales.food`/`sales.bev` — via `MetricQueryService`, and hands the daily series to the Python `forecast-service`. The service fetches external drivers (weather/holidays/school/university), builds one feature matrix across history + horizon, feeds it to TimesFM as `past_future_covariates` (the headline quantile forecast) and to an XGBoost twin (diagnostics + food/bev share), and returns `{forecast, diagnostics}`. Java persists both; the frontend renders them as tabs.

**Tech Stack:** Java 25 / Spring Boot 3.5.0 / PostgreSQL 16 (Flyway, Hibernate `validate`); Python 3.11 / FastAPI / TimesFM 3.0.2 (pinned git commit) / XGBoost / pandas / numpy / scipy; Next.js 15 / React 19 / TypeScript / Tailwind / shadcn/ui / Recharts / Bun.

**Spec:** `docs/superpowers/specs/2026-10-15-forecasting-refactor-design.md`

## Global Constraints

- `hibernate.ddl-auto: validate` — every schema change is a Flyway migration; never edit an applied migration. Next migrations are **V73**, **V74**.
- The `forecasting` package may depend only on `semantic` + `auth` + JPA + JDK. New metrics live in `semantic`/`classification`/`reconciliation`; `forecasting` reads them only through `MetricQueryService`.
- Gradle wrapper is pinned to 9.5.0 (JDK 25). Run `./gradlew spotlessApply` on edited Java; frontend via Bun (`bun run typecheck && bun run lint && bun run test`).
- Python service keeps its **data-agnostic** contract (numeric arrays in, forecast/diagnostics out); it never knows Goldy's schema or permissions.
- Every new `Api` method needs a **live** and a **demo** implementation.
- Conventional commits, lowercase (`feat(forecasting): …`, `docs(forecasting): …`).
- TimesFM 3.0 weights are non-commercial-licensed (internal use only).

## Review Focus

1. **An empty future book must not read as "quiet night".** A future date with zero booked covers is *immature*, not settled — it must be topped up toward the same-weekday average, never fed to the model as zero. → Task 5 (maturity test).
2. **Same-day labour must never reach the feature matrix** (it is only known after service). Labour is entirely deferred; the leak test asserts no labour column exists. → Task 4 (leakage test).
3. **A metric the caller may not read is denied before the run is created**, and re-checked on read — `booked_covers`/`sales.food`/`sales.bev` carry their domains' permissions. → Task 9 (permission test).
4. **An external API outage degrades to `NA` columns, not a crash**, and surfaces a warning. → Task 3 (degradation test).
5. **An XGBoost failure must not lose the forecast** — TimesFM's quantiles still return, `diagnostics` is null, with a warning. → Task 6 (partial-failure test).

---

### Task 1: `reservations.booked_covers` resolved quantity

**Files:**
- Create: `backend/src/main/resources/db/migration/V73__reservation_booked_covers.sql`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedReservationDay.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ReservationProjector.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedReservationQuery.java`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/ReservationSummary.java`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricId.java`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/ReservationMetricExecutor.java`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricCatalog.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ReservationProjectorTest.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/ReservationMetricExecutorTest.java`

**Interfaces:**
- Produces: metric id `RESERVATIONS_BOOKED_COVERS` with value `"reservations.booked_covers"`; `ReservationSummary.bookedCovers()` (long). Consumers: Task 9 reads it via `MetricQueryService`, and the frontend via the existing reservations API.

- [ ] **Step 1: Write the failing projector test**

In `ReservationProjectorTest`, add `bookedCoversIsSumOfPartySizeExcludingCancelledAndWalkIns`: project a date with a SEATED party of 4, a BOOKED party of 6 (future), a CANCELLED party of 8, and a WALK_IN party of 2; assert the resulting `ReservedReservationDay.bookedCovers() == 10` (4 + 6; cancelled and walk-in excluded).

- [ ] **Step 2: Run it, confirm it fails to compile** (no `bookedCovers` accessor)

Run: `cd backend && ./gradlew test --tests com.goldys.platform.reconciliation.ReservationProjectorTest`

- [ ] **Step 3: Write migration V73**

`ALTER TABLE resolved_reservation_day ADD COLUMN booked_covers BIGINT NOT NULL DEFAULT 0;`

- [ ] **Step 4: Implement the column + accumulation**

Add `bookedCovers` to `ResolvedReservationDay` (field, constructor param, `@Column(name = "booked_covers", nullable = false)`, accessor). In `ReservationProjector.Mutable.add`, accumulate `bookedCovers += v.partySize()` for every status except `CANCELLED` and `WALK_IN`; pass `m.bookedCovers` into both `new ResolvedReservationDay(...)` branches in `project`. Sum `bookedCovers` in `ResolvedReservationQuery.summarize` and add `bookedCovers` to the `ReservationSummary` record.

- [ ] **Step 5: Run the projector test, confirm PASS**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.reconciliation.ReservationProjectorTest`

- [ ] **Step 6: Expose the metric**

Add `RESERVATIONS_BOOKED_COVERS("reservations.booked_covers")` to `MetricId`; add it to `ReservationMetricExecutor.ids()` and the `pick` switch (`case RESERVATIONS_BOOKED_COVERS -> ReservationSummary::bookedCovers`); register it in `MetricCatalog` with permission `reservations.metrics` (source `resolved_reservation_day`).

- [ ] **Step 7: Write the executor test**

In `ReservationMetricExecutorTest`, add `bookedCoversReturnsDailySeries`: seed two dates with booked-covers summaries and assert `evaluate(MetricQuery(RESERVATIONS_BOOKED_COVERS, DAY, range))` yields a single series with those two values.

- [ ] **Step 8: Run both reservation tests, confirm PASS; commit**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.reconciliation.ReservationProjectorTest' --tests 'com.goldys.platform.semantic.catalog.ReservationMetricExecutorTest'`

```bash
git add backend/src/main/resources/db/migration/V73__reservation_booked_covers.sql backend/src/main/java/com/goldys/platform/reconciliation backend/src/main/java/com/goldys/platform/semantic
git commit -m "feat(forecasting): add reservations.booked_covers resolved metric"
```

---

### Task 2: `sales.food` / `sales.bev` / `sales.food_share` metrics

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/FoodBevSplit.java`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/ProductCategoryMetricsQuery.java`
- Modify: `backend/src/main/java/com/goldys/platform/classification/ResolvedProductCategoryQuery.java`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricId.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/FoodBevMetricExecutor.java`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricCatalog.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/FoodBevMetricExecutorTest.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/ResolvedProductCategoryQueryTest.java`

**Interfaces:**
- Produces: `FoodBevSplit(LocalDate date, BigDecimal food, BigDecimal bev)`; `ProductCategoryMetricsQuery.dailyFoodBev(LocalDate from, LocalDate to): List<FoodBevSplit>`; metric ids `SALES_FOOD("sales.food")`, `SALES_BEV("sales.bev")` (base, permission `reconciliation.sales`), `SALES_FOOD_SHARE("sales.food_share")` (derived = food ÷ net). Consumer: Task 9.

- [ ] **Step 1: Write the failing executor test**

In `FoodBevMetricExecutorTest`, add `foodBevDailySeries`: stub `ProductCategoryMetricsQuery.dailyFoodBev` to return two `FoodBevSplit` rows; assert `evaluate(MetricQuery(SALES_FOOD, DAY, range))` returns the food amounts and `SALES_BEV` returns the bev amounts as single daily series.

- [ ] **Step 2: Run it, confirm it fails to compile** (no `FoodBevMetricExecutor`, no `dailyFoodBev`)

- [ ] **Step 3: Implement the daily roll-up**

Create `FoodBevSplit` record. Add `dailyFoodBev(LocalDate, LocalDate)` to `ProductCategoryMetricsQuery`. In `ResolvedProductCategoryQuery`, implement it: iterate `productMetrics.productSales(from, to)` (rows carry `tradingDate`), join `productName` → assignment → category → `groupName`, and sum per day — group `"Food"` → `food`, every other non-null group → `bev`, null group / `"Uncategorised"` → excluded. Use a `FOOD_GROUP = "Food"` constant.

- [ ] **Step 4: Run the executor test, confirm PASS**

- [ ] **Step 5: Register the metrics**

Add `SALES_FOOD`, `SALES_BEV` to `MetricId`; create `FoodBevMetricExecutor` (mirrors `ReservationMetricExecutor`, `ids()` = `{SALES_FOOD, SALES_BEV}`, `pick` over `FoodBevSplit`); add `SALES_FOOD_SHARE` to `MetricId` and `DerivedMetricExecutor` (`ratio(query, SALES_FOOD, SALES_NET)`); register all three in `MetricCatalog` (`reconciliation.sales` for the base pair, derived deps for the share).

- [ ] **Step 6: Write the derived + roll-up tests; run; commit**

Add `foodShareIsFoodOverNet` to a derived-metric test (assert null denominator → null) and a `ResolvedProductCategoryQueryTest` asserting "Food" → food, beverage groups → bev, null/"Uncategorised" excluded.

```bash
git add backend/src/main/java/com/goldys/platform/semantic backend/src/main/java/com/goldys/platform/classification backend/src/test
git commit -m "feat(forecasting): add sales.food / sales.bev / sales.food_share metrics"
```

---

### Task 3: Python external features (`external.py`)

**Files:**
- Create: `forecast-service/external.py`
- Create: `forecast-service/data/university_dates.yaml` (port from reference)
- Create: `forecast-service/data/manual_events.yaml` (port from reference)
- Modify: `forecast-service/requirements.txt` (add `pyyaml`, `scipy`)
- Test: `forecast-service/test_external.py`

**Interfaces:**
- Produces: `build_external_features(config, start, end) -> DataFrame` with columns `public_holiday`, `public_holiday_name`, `public_holiday_eve`, `day_after_public_holiday`, `school_term`, `school_holiday`, `uni_in_session`, `uni_teaching_weeks`, `uni_assessment`, `uni_break`, `service_temp_mean_c`, `service_temp_max_c`, `service_rain_mm`, `service_rain_probability_max_pct`, `service_sunny_hours`, `service_storm`, `service_wind_gust_max_kmh`, `weather_source`. Consumer: Task 4.

- [ ] **Step 1: Write the degradation + aggregation tests**

In `test_external.py`: (a) `weather_aggregates_over_trading_hours_only` — given hourly rows spanning 06:00–23:00, `aggregate_service_weather` drops pre-open hours (weekday open 15:00, weekend 12:00, close 23:00); (b) `holiday_eve_and_day_after_are_derived` — a single holiday date sets `public_holiday` on that date, `public_holiday_eve` the day before, `day_after_public_holiday` the day after; (c) `source_outage_degrades_to_na` — when the fetch raises, `build_external_features` still returns all columns (as NA) and does not raise.

- [ ] **Step 2: Run, confirm FAIL** (no `external` module)

- [ ] **Step 3: Implement `external.py`**

Port the reference's `external.py` verbatim-in-spirit: DataVic `package_show` → CSV resource → public holidays + school terms (term-boundary gap = holiday); `melbourne_cup_day` computed (first Tuesday of November); `manual_events.yaml` gap-fills AFL Grand Final Friday; `university_dates.yaml` teaching/assessment/break windows; Open-Meteo archive + 16-day forecast, chunked by month, cached to `FORECAST_CACHE_DIR`, trading-hours aggregation. Config via env (`FORECAST_VENUE_LAT`, `FORECAST_VENUE_LON`, `FORECAST_TIMEZONE`, `FORECAST_OPEN_WEEKDAY`, `FORECAST_OPEN_WEEKEND`, `FORECAST_CLOSE`, `FORECAST_INCLUDE_WEATHER/SCHOOL/UNIVERSITY`). Every fetcher wrapped so failure → NA columns.

- [ ] **Step 4: Run, confirm PASS; commit**

```bash
git add forecast-service/external.py forecast-service/data forecast-service/requirements.txt forecast-service/test_external.py
git commit -m "feat(forecasting): external drivers (weather, holidays, school, university) in forecast-service"
```

---

### Task 4: Python feature matrix (`features.py`)

**Files:**
- Create: `forecast-service/features.py`
- Test: `forecast-service/test_features.py`

**Interfaces:**
- Produces: `add_calendar_features(frame)`, `add_lag_features(frame, config)`, `assemble_dataset(internal, config, end=None)`, `feature_columns(frame, config)` (leakage + coverage gating), `prepare_matrix(frame, columns)`. Columns include `net_revenue_lag_7/14`, `net_revenue_roll_mean_7/28`, `food_share_roll_mean_7`, `net_revenue_dow_mean_4`, `food_share_dow_mean_4`. Consumer: Task 5.

- [ ] **Step 1: Write the leakage + shift tests**

In `test_features.py`: (a) `lagsAreShiftedNotCentred` — `net_revenue_lag_7[i] == net_revenue[i-7]` and is NaN for the first 7 rows; (b) `rollingWindowExcludesThePredictedDay` — `net_revenue_roll_mean_7[i]` does not include row `i`; (c) `dowMean4IsMeanOfPriorFourSameWeekdays`; (d) `noLabourColumnsAreEmitted` — `feature_columns` never returns a column whose name contains `labour`.

- [ ] **Step 2: Run, confirm FAIL** (no `features` module)

- [ ] **Step 3: Implement `features.py`**

Port the reference's `features.py`: `CALENDAR_FEATURES` (`day_of_week`, `is_weekend`, `is_friday`, `is_saturday`, `day_of_month`, `month`, `week_of_year_sin/cos`, `days_to_month_end`); lag/rolling with `shift` (never centred); `net_revenue_dow_mean_4` = mean of the prior 4 same-weekdays; `feature_columns` builds calendar + external + `booked_covers` (coverage-gated at 0.45) + lag/roll columns, and drops all-null/constant columns. No labour columns exist (deferred).

- [ ] **Step 4: Run, confirm PASS; commit**

```bash
git add forecast-service/features.py forecast-service/test_features.py
git commit -m "feat(forecasting): feature matrix and leakage rules in forecast-service"
```

---

### Task 5: Python XGBoost twin, backtest, and book maturity

**Files:**
- Create: `forecast-service/model.py`
- Create: `forecast-service/forecast.py`
- Test: `forecast-service/test_model.py`
- Test: `forecast-service/test_forecast.py`

**Interfaces:**
- Produces: `train(frame, config) -> TrainedModels` (net + share XGBoost, `metrics`, `warnings`, `trained_rows`, `reliable`); `feature_importance(models) -> DataFrame[{feature, importance}]`; `forecast(internal, models, config, horizon) -> DataFrame` (recursive, per-day `forecast_net_revenue`, `forecast_food_revenue`, `forecast_bev_revenue`, `forecast_food_share`, `baseline_net_revenue`, `bookings_used`, holiday/school/uni/weather driver columns); `backtest(frame, models) -> DataFrame` + summary (`by_weekday`, `residual_mean`, `residual_std`, `worst_days`); `estimate_pickup_curve(daily, snapshot_date)` and `apply_maturity(daily, snapshot_date, curve)` (additive top-up `observed + (1 − maturity) × same-weekday average`). Consumer: Task 6.

- [ ] **Step 1: Write the maturity + baseline + backtest tests**

In `test_forecast.py`: (a) `emptyFutureBookTopsUpToWeekdayAverage` — a future date with `booked_covers = 0` and maturity < 1 yields `bookings_used` equal to the same-weekday average (never 0); (b) `baselineIsMeanOfLastFourSameWeekdays`; (c) `backtestEmitsWorstDays` — the summary's `worst_days` is ordered by `abs_pct_error` descending. In `test_model.py`: `importanceIsSortedDescending`, `holdoutMetricsCompareToBaseline` (both `holdout_net_xgboost` and `holdout_net_baseline` present).

- [ ] **Step 2: Run, confirm FAIL**

- [ ] **Step 3: Implement `model.py`**

Port the reference's `model.py`: `score` (MAE/RMSE/MAPE/n), two `_fit_target`s (`net_revenue` log1p, `food_share` clipped 0.05–0.95), chronological holdout (`test_fraction` 0.25), expanding-window CV (4 folds × 7-day horizon), `min_rows_for_xgboost` 120 → `reliable` flag, `feature_importance` (gain). `requirements.txt` adds `xgboost`.

- [ ] **Step 4: Implement `forecast.py`**

Port the reference's `forecast.py` + `backtest`: recursive forward pass (write net/share back before computing the next day's lags); `baseline_net_revenue = net_revenue_dow_mean_4`; `estimate_pickup_curve` (exponential `floor + (1−floor)·e^(−L/τ)` fitted to the current snapshot, fallback `floor=0.15, tau=7.0`) and `apply_maturity` (additive top-up). No labour-budget output (deferred). The `by_weekday`/`residual_*`/`worst_days` summary matches the reference's shapes.

- [ ] **Step 5: Run, confirm PASS; commit**

```bash
git add forecast-service/model.py forecast-service/forecast.py forecast-service/test_model.py forecast-service/test_forecast.py forecast-service/requirements.txt
git commit -m "feat(forecasting): XGBoost twin, backtest, and book maturity"
```

---

### Task 6: Python `/forecast` contract + `past_future_covariates`

**Files:**
- Modify: `forecast-service/predictor.py`
- Modify: `forecast-service/app.py`
- Modify: `forecast-service/test_app.py`

**Interfaces:**
- Produces: `Predictor.predict(..., past_only_covariates=None, past_future_covariates=None)` (extended); `POST /forecast` request `{dates, net, booked_covers, bookings, food, bev, horizon, quantiles}` → response `{forecast: [{date, median, p10, p90, food, bev, baseline, bookedCovers, publicHoliday, publicHolidayName, publicHolidayEve, dayAfterPublicHoliday, schoolHoliday, uniInSession, serviceTempMaxC, serviceRainMm, weatherSource}], diagnostics: {...}}`. Consumer: Task 8 (Java client parses this exact shape).

- [ ] **Step 1: Write the contract + partial-failure tests**

In `test_app.py`: (a) `forecastReturnsDiagnosticsAndSplit` — with a fake predictor, the response contains `forecast` (with `food`/`bev`/`baseline`) and `diagnostics` (with `importance`, `holdoutNetXgboost`, `backtest`); (b) `xgboostFailureStillReturnsForecast` — when the twin raises, the response still has `forecast` (non-null) and `diagnostics == null` with a warning; (c) `pastFutureCovariatesArePassed` — assert the fake predictor receives a `past_future_covariates` array whose length equals `len(dates) + horizon`.

- [ ] **Step 2: Run, confirm FAIL**

- [ ] **Step 3: Extend `predictor.py`**

Add `past_future_covariates: np.ndarray | None = None` to `Predictor.predict` and `TimesFmPredictor.predict`; pass `past_future_covariates=past_future_covariates` to `forecaster.predict(...)`. Keep `past_only_covariates` for backward compatibility.

- [ ] **Step 4: Implement the new contract in `app.py`**

New `ForecastRequest`/`ForecastResponse` Pydantic models per the interface; build the feature matrix via `features.assemble_dataset` + `external.build_external_features` (history + horizon), feed the future-known columns (`booked_covers`, holiday/school/uni flags, weather) to TimesFM as `past_future_covariates`, train the XGBoost twin, apply the share model to TimesFM's net median for `food`/`bev`, and assemble `diagnostics`. Wrap the twin in try/except so its failure yields `diagnostics=None` + a warning, never a lost forecast.

- [ ] **Step 5: Run, confirm PASS; commit**

```bash
git add forecast-service/predictor.py forecast-service/app.py forecast-service/test_app.py
git commit -m "feat(forecasting): /forecast contract with future covariates and diagnostics"
```

---

### Task 7: `forecast_run` diagnostics + richer series (migration V74)

**Files:**
- Create: `backend/src/main/resources/db/migration/V74__forecast_run_diagnostics.sql`
- Modify: `backend/src/main/java/com/goldys/platform/forecasting/ForecastRun.java`
- Modify: `backend/src/main/java/com/goldys/platform/forecasting/ForecastDay.java`
- Test: `backend/src/test/java/com/goldys/platform/forecasting/ForecastRunTest.java`

**Interfaces:**
- Produces: `ForecastRun.markSucceeded(series, diagnostics, modelVersion, elapsedMs)`; `ForecastRun.diagnostics()` (nullable); `ForecastDay` gains `food`, `bev`, `baseline`, and driver fields (all nullable `BigDecimal`/`Boolean`/`String`). Consumer: Task 9.

- [ ] **Step 1: Write the failing persistence test**

In `ForecastRunTest`, add `markSucceededStoresDiagnostics`: call `markSucceeded(series, diagnosticsJson, "timesfm-3.0", 42L)` and assert `diagnostics()` round-trips the JSON string and `series()` carries the enriched `ForecastDay` fields.

- [ ] **Step 2: Run, confirm it fails to compile**

- [ ] **Step 3: Write migration V74**

`ALTER TABLE forecast_run ADD COLUMN diagnostics JSONB;`

- [ ] **Step 4: Implement**

Add `diagnostics` (a `String`/JSONB-mapped field) to `ForecastRun` and the new nullable fields to `ForecastDay`; extend `markSucceeded` to take and store diagnostics. Keep a 3-arg `markSucceeded(series, modelVersion, elapsedMs)` overload delegating with `diagnostics = null`, so the existing `ForecastService` caller keeps compiling until Task 9 switches it to the 4-arg form. The `series` JSONB column is unchanged — the richer per-day fields are additional keys in the same JSONB.

- [ ] **Step 5: Run, confirm PASS; commit**

```bash
git add backend/src/main/resources/db/migration/V74__forecast_run_diagnostics.sql backend/src/main/java/com/goldys/platform/forecasting
git commit -m "feat(forecasting): persist forecast diagnostics and richer day series"
```

---

### Task 8: Java `ForecastModelClient` port + records

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastModelClient.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastModelHttpClient.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastModelRequest.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastModelResponse.java`
- Test: `backend/src/test/java/com/goldys/platform/forecasting/ForecastModelHttpClientTest.java`

**Interfaces:**
- Produces: `ForecastModelClient.forecast(ForecastModelRequest) -> ForecastModelResponse`; request records the daily series (`dates`, `net`, `bookedCovers`, `bookings`, `food`, `bev` as `List<BigDecimal>`/`null`-aligned) + `horizon` + `quantiles`; response records the per-day forecast and a `diagnostics` payload (JSON string). The existing `TimesFmClient`/`TimesFmHttpClient` remain in place (still used by `ForecastService`); Task 9 swaps to the new client and deletes the old. Consumer: Task 9.

- [ ] **Step 1: Write the HTTP client test**

In `ForecastModelHttpClientTest`, add `non2xxThrows` and `parsesForecastAndDiagnostics`: a stubbed 200 body parses into `ForecastModelResponse` with non-null `forecast` and `diagnostics`; a 500 throws.

- [ ] **Step 2: Run, confirm FAIL**

- [ ] **Step 3: Implement the records + client**

Add `ForecastModelClient` (port), `ForecastModelHttpClient` (JDK `HttpClient`, same shape as `TimesFmHttpClient`), and the `ForecastModelRequest`/`ForecastModelResponse` records matching the Task 6 wire shape. Leave `TimesFmClient`/`TimesFmHttpClient` untouched — `ForecastService` still uses them until Task 9.

- [ ] **Step 4: Run, confirm PASS; commit**

```bash
git add backend/src/main/java/com/goldys/platform/forecasting
git commit -m "feat(forecasting): ForecastModelClient port and richer contract records"
```

---

### Task 9: Java `ForecastService` + `ForecastSeriesAssembler`

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/forecasting/ForecastSeriesAssembler.java`
- Modify: `backend/src/main/java/com/goldys/platform/forecasting/ForecastService.java`
- Test: `backend/src/test/java/com/goldys/platform/forecasting/ForecastSeriesAssemblerTest.java`
- Test: `backend/src/test/java/com/goldys/platform/forecasting/ForecastServiceTest.java`

**Interfaces:**
- Produces: `ForecastService.submit(targetMetric, covariateMetrics, historyFrom, horizonDays, email)` now reads `sales.net` (target) + `reservations.booked_covers`, `reservations.bookings`, `sales.food`, `sales.bev`; `ForecastService.execute(runId)` calls the client and persists series + diagnostics. Consumer: Task 10.

- [ ] **Step 1: Write the assembler + permission tests**

In `ForecastSeriesAssemblerTest`, add `requestCarriesBookedCoversAndFoodBev`: the assembled request includes the `booked_covers`, `food`, `bev` series aligned to the axis (null where missing). In `ForecastServiceTest`, add `submitRequiresMetricPermission`: a caller lacking `reservations.metrics`/`reconciliation.sales` READ is denied before the run is created.

- [ ] **Step 2: Run, confirm FAIL**

- [ ] **Step 3: Implement**

Update `ForecastSeriesAssembler` to build the richer request (add `bookedCovers`, `food`, `bev` aligned series). Update `ForecastService.execute` to read the added metrics via `series(MetricId, range)`, inject `ForecastModelClient` in place of `TimesFmClient`, call `client.forecast(request)`, and `run.markSucceeded(transpose(forecast), forecast.diagnostics(), MODEL_VERSION, elapsed)` (the 4-arg form). Delete `TimesFmClient`, `TimesFmHttpClient`, and their old request/response records. Keep `failInterruptedRuns`, `MIN_HISTORY_POINTS`, and the failure-message sanitisation.

- [ ] **Step 4: Run, confirm PASS; commit**

```bash
git add backend/src/main/java/com/goldys/platform/forecasting
git commit -m "feat(forecasting): assemble and persist future-bookings forecast + diagnostics"
```

---

### Task 10: Java `ForecastingApplicationService` DTOs + endpoints

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/application/ForecastingApplicationService.java`
- Modify: `backend/src/main/java/com/goldys/platform/application/ForecastRunDto.java`
- Create: `backend/src/main/java/com/goldys/platform/application/ForecastDiagnosticsDto.java`
- Modify: `backend/src/main/java/com/goldys/platform/application/ForecastSeriesPointDto.java`
- Test: `backend/src/test/java/com/goldys/platform/application/ForecastingApplicationServiceTest.java`

**Interfaces:**
- Produces: `ForecastRunDto` now carries `diagnostics` (nullable `ForecastDiagnosticsDto`); `ForecastSeriesPointDto` gains `food`, `bev`, `baseline`, `bookedCovers`, holiday/school/uni/weather driver fields; a `GET /api/forecasting/runs/{id}` returns the enriched run (diagnostics included). Consumer: Task 11.

- [ ] **Step 1: Write the DTO mapping test**

In `ForecastingApplicationServiceTest`, add `getReturnsDiagnosticsAndSplit`: a succeeded run with diagnostics + a `food`/`bev`/`baseline` series maps to a `ForecastRunDto` whose `diagnostics` and per-point split fields are populated.

- [ ] **Step 2: Run, confirm FAIL**

- [ ] **Step 3: Implement**

Add `ForecastDiagnosticsDto` (mirroring the §5 diagnostics shape: accuracy, importance, backtest, warnings, features, `reliable`); enrich `ForecastSeriesPointDto`; map the persisted JSONB/`ForecastDay` into the DTOs in `toRunDto`/`toSeriesPoint`.

- [ ] **Step 4: Run, confirm PASS; commit**

```bash
git add backend/src/main/java/com/goldys/platform/application
git commit -m "feat(forecasting): surface diagnostics and food/bev split in the run DTO"
```

---

### Task 11: Frontend `Api` methods + types + demo fixtures

**Files:**
- Modify: `frontend/lib/api/types.ts`
- Modify: `frontend/lib/api/live.ts`
- Modify: `frontend/lib/api/demo.ts`
- Modify: `frontend/lib/api/index.ts` (re-export types)
- Test: `frontend/lib/api/demo.test.ts`

**Interfaces:**
- Produces: types `ForecastDiagnostics`, `ForecastAccuracyMetric`, `ForecastImportanceRow`, `BacktestRow`, `WeekdayErrorRow`, `ForecastDayRow` (extends the current `ForecastSeriesPoint` with `food`/`bev`/`baseline`/`bookedCovers` + driver flags); `Api.getForecastRun` returns the enriched run. Consumer: Tasks 12–13.

- [ ] **Step 1: Write the demo fixture test**

In `demo.test.ts`, add `getForecastRunReturnsDiagnosticsAndSplit`: the demo run has non-null `diagnostics` (with `importance` and `backtest.byWeekday` non-empty) and per-point `food`/`bev`/`baseline`.

- [ ] **Step 2: Run, confirm FAIL**

- [ ] **Step 3: Implement**

Add the types, the live `getForecastRun` mapping, and demo fixtures mirroring the reference's snapshot shapes (a `ForecastSnapshot`-like demo with forecast rows + `backtest_summary` + `importance`).

- [ ] **Step 4: Run `bun run test`, confirm PASS; commit**

```bash
git add frontend/lib/api
git commit -m "feat(forecasting): Api types, live mapping, and demo fixtures for diagnostics"
```

---

### Task 12: Frontend chart components

**Files:**
- Create: `frontend/components/forecasting/forecast-vs-baseline-chart.tsx`
- Create: `frontend/components/forecasting/food-bev-split-chart.tsx`
- Create: `frontend/components/forecasting/feature-importance-chart.tsx`
- Create: `frontend/components/forecasting/backtest-chart.tsx`
- Create: `frontend/components/forecasting/residual-chart.tsx`
- Create: `frontend/components/forecasting/predicted-vs-actual-chart.tsx`
- Create: `frontend/components/forecasting/weekday-error-chart.tsx`
- Test: `frontend/components/forecasting/forecast-charts.test.tsx`

**Interfaces:**
- Consumes: the Task 11 types. Produces: Recharts components (reusing `components/widgets/chart-style.tsx`) taking typed row props and rendering a `ComposedChart` (bars + dashed baseline), stacked `BarChart` split, horizontal importance bars, backtest lines, residual bars, scatter, and weekday-error bars.

- [ ] **Step 1: Write the smoke test**

In `forecast-charts.test.tsx`, render each component with 2–3 typed rows and assert a `role="img"`/`data-testid` renders (no throw on empty/null driver fields).

- [ ] **Step 2: Run, confirm FAIL**

- [ ] **Step 3: Implement the components**

Port the reference's `charts.tsx` shapes to Goldy's Recharts + `chart-style` conventions (existing `AXIS_TICK`, `CHART_TOOLTIP_PROPS`, `compactAxis`, `formatXTick`); `ForecastChart`-style composed chart, stacked split, horizontal `BarChart` importance, lines for actual/model/baseline, residual bars coloured by sign, scatter with a y=x `ReferenceLine`, weekday-error bars.

- [ ] **Step 4: Run `bun run test`, confirm PASS; commit**

```bash
git add frontend/components/forecasting
git commit -m "feat(forecasting): chart components for forecast, split, importance, backtest"
```

---

### Task 13: Frontend tabbed `/forecasting` page

**Files:**
- Modify: `frontend/app/(app)/forecasting/page.tsx` (or split into `forecast-tab.tsx` / `model-tab.tsx` / `backtest-tab.tsx`)
- Modify: `frontend/app/(app)/forecasting/forecasting-page.test.tsx`
- Modify: `frontend/lib/nav.ts` (only if the route/path changes)

**Interfaces:**
- Consumes: `Api` methods from Task 11, components from Task 12. Produces: the tabbed page (Forecast / Model / Backtest / Runs).

- [ ] **Step 1: Write the page test**

In `forecasting-page.test.tsx`, assert: (a) the four tabs render; (b) running a forecast shows the forecast-vs-baseline chart, the food/bev split, and the day-by-day list with driver chips; (c) the Model tab shows feature importance and a "beats baseline" banner when the twin loses to the baseline; (d) the Backtest tab shows error-by-weekday and worst-misses; (e) the food/bev split carries the "approximate — pending category refinement" note.

- [ ] **Step 2: Run, confirm FAIL**

- [ ] **Step 3: Implement**

Restructure the page into tabs using the existing `Tabs` convention (as used elsewhere); wire the run → forecast → diagnostics → backtest data; render the components from Task 12; keep the existing run/poll lifecycle and the compare-to-actuals behaviour; add the split-approximation note.

- [ ] **Step 4: Run the full frontend gate, confirm PASS; commit**

Run: `cd frontend && bun run typecheck && bun run lint && bun run test && bun run build`

```bash
git add frontend/app frontend/components frontend/lib
git commit -m "feat(forecasting): tabbed forecasting page with baseline, importance, backtest"
```

---

### Final gate

- [ ] Run `cd backend && ./gradlew test spotlessCheck` (Docker required for integration tests).
- [ ] Run `cd frontend && bun run typecheck && bun run lint && bun run test && bun run build`.
- [ ] Push `feat/forecasting-refactor`, open a PR, and watch CI.

### Follow-up (not in this plan)

- **Measured pickup curve via `createdAt`** (§7/§13.3 of the spec): v1 ships the *fitted* curve (Task 5). The measured curve requires a new semantic query exposing reservation-level `(createdAt, reservationAt, partySize, cancelledAt)` — a follow-up task.
- **Second-level food/bev classifier** (§8/§13.1): documented outstanding; the ~1%-remainder group mapping ships first.
