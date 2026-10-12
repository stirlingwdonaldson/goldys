# TimesFM Forecasting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Forecasting page that predicts a chosen resolved metric (target) with Google's TimesFM 3.0, using selectable past-only covariate metrics, persisting each async run and comparing forecasts to actuals.

**Architecture:** A self-hosted Python (FastAPI) inference service serves TimesFM 3.0 behind a data-agnostic HTTP contract. The Java backend (a new `forecasting` package, a pure consumer of `semantic` + `auth`) fetches resolved daily series via `MetricQueryService`, aligns them, calls the service asynchronously, and persists each run to a new `forecast_run` table. The frontend adds a `/forecasting` page, five new `Api` methods (live + demo), and a nav entry.

**Tech Stack:** Java 25 / Spring Boot 3.5.0 / PostgreSQL 16 (Flyway, Hibernate `validate`); Python 3.11 / FastAPI / PyTorch / `timesfm>=3.0.0`; Next.js 15 / React 19 / TypeScript / Tailwind / shadcn/ui / Recharts / Bun.

**Spec:** `docs/superpowers/specs/2026-10-12-timesfm-forecasting-design.md`

## Global Constraints

- Java 25, Spring Boot 3.5.0; Gradle wrapper **9.5.0** (never downgrade); schema owned by Flyway migrations, Hibernate `ddl-auto: validate`, never edit an applied migration.
- Read resolved data **only** through `MetricQueryService.query(MetricQuery(...))` with `TimeGrain.DAY`, `Set.of()` dimensions, `Calendar.CALENDAR`. Never read `canonical`, `reconciliation`, or the raw ledger from `forecasting`.
- Metric ids are `MetricId` enum values, serialized via `MetricId.value()` and parsed via `MetricId.fromValue(String)`.
- Model: TimesFM 3.0 (`google/timesfm-3.0-pytorch`), quantiles `["0.1","0.2","0.3","0.4","0.5","0.6","0.7","0.8","0.9"]` (median = index 4, band = 0.1–0.9).
- Forecastable set = every `MetricCatalog` metric except `MetricId.PRODUCT_TOP_SELLERS` (the sole ranked-list result).
- Permission resource `forecasting` seeded `ALL × OWNER` (`can_read` + `can_write`); the create path requires `READ`.
- Feature flag `forecasting.timesfm.enabled` (default false); base URL `forecasting.timesfm.base-url`.
- New package `forecasting` may depend only on `semantic` + `auth` + JPA (+ JDK). `application` may depend on `forecasting` (extend the arch test).
- Frontend demo mode (`NEXT_PUBLIC_DEMO_MODE`, default on) needs fixtures for every new `Api` method; every method has a live + demo implementation.
- Commit per task on branch `feat/timesfm-forecasting`; lowercase conventional messages. Run `./gradlew spotlessApply` on edited Java and `bun run typecheck && bun run lint && bun run test` before finishing frontend work.
- TimesFM 3.0 open weights are non-commercial-licensed (internal use only — recorded in the decision doc, Task 9).

## Review Focus

1. **A metric day with no resolved value must be `null`, never zero** — a gap is not a zero. Pinned by `ForecastSeriesAssemblerTest.alignNullsMissingDays` (Task 3).
2. **Too little history (fewer than 14 resolved target points) is rejected up front, not forecast as noise** — pinned by `ForecastServiceTest.submitRejectsInsufficientHistory` (Task 4).
3. **The Python service being down or slow fails the run, never hangs the app or leaves a stuck RUNNING row** — pinned by `TimesFmHttpClientTest.non2xxThrows` (Task 2) and `ForecastServiceTest.executeMarksFailedOnClientError` + `failInterruptedRunsMarksRunningFailed` (Task 4).
4. **A target/covariate metric the caller may not read is denied before creating the run, and re-checked on read** — pinned by `ForecastingApplicationServiceTest.createRequiresMetricPermission` and `getRequiresMetricPermission` (Task 5).
5. **Horizon and date boundaries: history ends today, forecast dates start tomorrow, horizon is bounded 1–90** — pinned by `ForecastServiceTest.submitComputesHistoryToToday` and `horizonValidated` (Task 4).

---

### Task 1: Forecast persistence (migration + entity + repository)

**Files:**
- Create: `backend/src/main/resources/db/migration/V44__forecast_run.sql`
- Create: `backend/src/main/resources/db/migration/V45__seed_forecasting_permissions.sql`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastRunStatus.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastDay.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastRun.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastRunRepository.java`
- Test: `backend/src/test/java/com/goldys/platform/forecasting/ForecastRunTest.java`
- Test: `backend/src/test/java/com/goldys/platform/forecasting/ForecastRunRepositoryIntegrationTest.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces:
  - `enum ForecastRunStatus { QUEUED, RUNNING, SUCCEEDED, FAILED }`
  - `record ForecastDay(LocalDate date, Map<String, BigDecimal> quantiles)` — keys `"0.1"`…`"0.9"`.
  - `class ForecastRun` with `static ForecastRun create(String targetMetric, List<String> covariates, LocalDate historyFrom, LocalDate historyTo, int horizonDays, String createdBy, Instant now)`, `void markRunning()`, `void markSucceeded(List<ForecastDay> series, String modelVersion, long elapsedMs)`, `void markFailed(String message)`, plus accessors `id()`, `status()`, `targetMetric()`, `covariates()`, `historyFrom()`, `historyTo()`, `horizonDays()`, `createdAt()`, `createdBy()`, `series()`, `errorMessage()`, `modelVersion()`, `elapsedMs()`.
  - `interface ForecastRunRepository extends JpaRepository<ForecastRun, UUID>` with `List<ForecastRun> findAllByOrderByCreatedAtDesc()` and `List<ForecastRun> findByStatus(ForecastRunStatus status)`.

- [ ] **Step 1: Write the failing unit test `ForecastRunTest`**

`src/test/java/com/goldys/platform/forecasting/ForecastRunTest.java` — assert: `create(...)` yields `QUEUED` with the given fields; `markRunning()` → `RUNNING`; `markSucceeded(series, "timesfm-3.0", 42L)` → `SUCCEEDED`, `series()` equals the passed list, `modelVersion()` `"timesfm-3.0"`, `elapsedMs()` `42`; `markFailed("boom")` → `FAILED`, `errorMessage()` `"boom"`; `ForecastDay` round-trips `date` + `quantiles` map.

- [ ] **Step 2: Run the test, confirm it fails to compile** (no `ForecastRun`)

Run: `cd backend && ./gradlew test --tests com.goldys.platform.forecasting.ForecastRunTest`
Expected: compilation failure (missing `ForecastRun`).

- [ ] **Step 3: Implement `ForecastRunStatus`, `ForecastDay`, `ForecastRun`**

Model `ForecastRun` on `SavedDashboard` (package `com.goldys.platform.forecasting`): `@Entity @Table(name="forecast_run")`, `@Id UUID id`, `@Enumerated(EnumType.STRING)` for status, `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb")` for `covariates` (`List<String>`) and `series` (`List<ForecastDay>`). `created_by` is `String` (caller email). Guard constructor with `Objects.requireNonNull` and copy `List` fields (`List.copyOf`). `markSucceeded` stores `elapsedMs` and `modelVersion`.

- [ ] **Step 4: Run the unit test, confirm it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.forecasting.ForecastRunTest`
Expected: PASS.

- [ ] **Step 5: Write the migrations**

`V44__forecast_run.sql`: the table from spec §5 (with `created_by varchar(255) NOT NULL`), plus `CREATE INDEX forecast_run_created_at_idx ON forecast_run (created_at DESC)`.
`V45__seed_forecasting_permissions.sql` (mirror V39):
```sql
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'forecasting', true, true);
```

- [ ] **Step 6: Implement `ForecastRunRepository`**

`interface ForecastRunRepository extends JpaRepository<ForecastRun, UUID>` with the two derived query methods above.

- [ ] **Step 7: Write `ForecastRunRepositoryIntegrationTest`**

Testcontainers Postgres (mirror `ResolvedReservationQueryIntegrationTest`): save a run via `create(...)` + `markSucceeded(List.of(new ForecastDay(...)), "timesfm-3.0", 1)`, then `findById` and assert the JSONB `series` and `covariates` round-trip; `findAllByOrderByCreatedAtDesc()` orders newest first; `findByStatus(RUNNING)` finds only running rows.

- [ ] **Step 8: Run the integration test, confirm it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.forecasting.ForecastRunRepositoryIntegrationTest`
Expected: PASS (Docker reachable).

- [ ] **Step 9: Commit**

```bash
cd backend && ./gradlew spotlessApply
git add backend/src/main/resources/db/migration backend/src/main/java/com/goldys/platform/forecasting backend/src/test/java/com/goldys/platform/forecasting
git commit -m "feat: forecast_run persistence and forecasting permission seed"
```

---

### Task 2: TimesFM client (port + HTTP adapter)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/forecasting/TimesFmRequest.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/TimesFmForecast.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/TimesFmClient.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/TimesFmException.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/TimesFmProperties.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/TimesFmHttpClient.java`
- Test: `backend/src/test/java/com/goldys/platform/forecasting/TimesFmHttpClientTest.java`

**Interfaces:**
- Consumes: nothing in-platform (JDK `HttpClient`, Jackson `ObjectMapper`).
- Produces:
  - `record Covariate(String name, List<BigDecimal> values)`
  - `record TimesFmRequest(List<LocalDate> dates, List<BigDecimal> target, List<Covariate> covariates, int horizonDays, List<String> quantiles)`
  - `record TimesFmForecast(List<LocalDate> dates, Map<String, List<BigDecimal>> quantiles)`
  - `interface TimesFmClient { TimesFmForecast forecast(TimesFmRequest request); }`
  - `@ConfigurationProperties(prefix = "forecasting.timesfm") record TimesFmProperties(boolean enabled, String baseUrl)` (defaults via `application.yml`).
  - `class TimesFmHttpClient implements TimesFmClient` — constructor `(TimesFmProperties properties, ObjectMapper mapper)`; `forecast` POSTs `{dates, target, covariates, horizon, freq:"D", quantiles}` to `{baseUrl}/forecast` and parses `{dates, quantiles}`; non-2xx or IO → `TimesFmException`.

- [ ] **Step 1: Write the failing test `TimesFmHttpClientTest`**

Spin up `com.sun.net.httpserver.HttpServer` on an ephemeral port. Three test methods: `requestSerializesContract` — serves a canned `/forecast` JSON (2 dates, 3 quantiles) and asserts `forecast(...)` returns the parsed `TimesFmForecast` and the *request* body serialized `dates`, `target`, `covariates`, `horizon`, `freq:"D"`, `quantiles`; `non2xxThrows` — serves HTTP 500 and asserts `TimesFmException` with a message that does **not** contain the request payload; `unreachableThrows` — `baseUrl` at a closed port → `TimesFmException`.

- [ ] **Step 2: Run it, confirm failure** (no `TimesFmHttpClient`)

Run: `cd backend && ./gradlew test --tests com.goldys.platform.forecasting.TimesFmHttpClientTest`
Expected: compilation failure.

- [ ] **Step 3: Implement `TimesFmRequest`, `TimesFmForecast`, `Covariate`, `TimesFmClient`, `TimesFmException`, `TimesFmProperties`, `TimesFmHttpClient`**

`TimesFmHttpClient` mirrors `CtbClient`: a `java.net.http.HttpClient` with a 5-minute read timeout; build the JSON with the injected `ObjectMapper` (serialize `BigDecimal` nulls as JSON null — Jackson default). On non-2xx, throw `TimesFmException("TimesFM service returned HTTP " + code)` (no body/creds). Add `forecasting.timesfm.enabled`/`base-url` to `backend/src/main/resources/application.yml` (`enabled: ${FORECASTING_TIMESFM_ENABLED:false}`, `base-url: ${FORECASTING_TIMESFM_BASE_URL:http://localhost:8001}`).

- [ ] **Step 4: Run it, confirm pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.forecasting.TimesFmHttpClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd backend && ./gradlew spotlessApply
git add backend/src/main/java/com/goldys/platform/forecasting backend/src/main/resources/application.yml backend/src/test/java/com/goldys/platform/forecasting
git commit -m "feat: TimesFM client port and HTTP adapter"
```

---

### Task 3: Series alignment (`ForecastSeriesAssembler`)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/forecasting/CovariateSeries.java`
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastSeriesAssembler.java`
- Test: `backend/src/test/java/com/goldys/platform/forecasting/ForecastSeriesAssemblerTest.java`

**Interfaces:**
- Consumes: `TimesFmRequest`, `Covariate` (Task 2); `semantic.catalog.MetricPoint` (`bucketStart()`, `value()`).
- Produces:
  - `record CovariateSeries(String name, List<MetricPoint> points)`
  - `final class ForecastSeriesAssembler` with static methods:
    - `static List<LocalDate> axis(LocalDate from, LocalDate to)` — every date inclusive.
    - `static List<BigDecimal> align(List<LocalDate> axis, List<MetricPoint> points)` — value per date, `null` where no point.
    - `static TimesFmRequest request(List<LocalDate> axis, List<MetricPoint> target, List<CovariateSeries> covariates, int horizonDays, List<String> quantiles)` — target = `align(axis, target)`, each covariate = `Covariate(name, align(axis, points))`.

- [ ] **Step 1: Write the failing test `ForecastSeriesAssemblerTest`**

Four test methods: `axisInclusiveDates` — `axis(2026-10-01, 2026-10-03)` → 3 dates; `alignNullsMissingDays` — `align` returns `null` for a date with no point (never zero) and the value for a present one; `alignIgnoresOutOfAxisPoints` — a point on a date outside the axis is ignored; `requestAlignsCovariates` — `request(...)` produces a `TimesFmRequest` whose `target`/covariate arrays are axis-aligned and whose `horizonDays`/`quantiles` pass through.

- [ ] **Step 2: Run it, confirm failure**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.forecasting.ForecastSeriesAssemblerTest`
Expected: compilation failure.

- [ ] **Step 3: Implement `CovariateSeries` and `ForecastSeriesAssembler`**

Plain static methods (no Spring). `align` builds a `Map<LocalDate, BigDecimal>` from `points` (`putIfAbsent` so a later duplicate does not clobber), then maps the `axis` in order.

- [ ] **Step 4: Run it, confirm pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.forecasting.ForecastSeriesAssemblerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd backend && ./gradlew spotlessApply
git add backend/src/main/java/com/goldys/platform/forecasting backend/src/test/java/com/goldys/platform/forecasting
git commit -m "feat: forecast series alignment"
```

---

### Task 4: `ForecastService` (submit / execute / fail-interrupted)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastService.java`
- Test: `backend/src/test/java/com/goldys/platform/forecasting/ForecastServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/forecasting/ForecastServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `ForecastRun`, `ForecastRunRepository`, `ForecastRunStatus` (Task 1); `TimesFmClient`, `TimesFmRequest`, `TimesFmForecast`, `TimesFmProperties` (Task 2); `ForecastSeriesAssembler`, `CovariateSeries` (Task 3); `semantic.catalog.{MetricQueryService, MetricCatalog, MetricId, MetricQuery, TimeRange, TimeGrain, Calendar, TimeSeriesResult, MetricSeries, MetricPoint}`; `auth` (nothing here — auth is in Task 5).
- Produces:
  - `class ForecastService` — constructor `(ForecastRunRepository runs, MetricQueryService queries, MetricCatalog catalog, TimesFmClient client, TaskExecutor executor, TimesFmProperties properties, Clock clock)` (Spring `@Autowired`; `executor` is `@Qualifier("applicationTaskExecutor")`).
    - `ForecastRun submit(String targetMetric, List<String> covariateMetrics, LocalDate historyFrom, int horizonDays, String email)` — validate horizon `1..90`; resolve `MetricId.fromValue`; reject `PRODUCT_TOP_SELLERS` or unknown; fetch the target series, count non-null values, throw `IllegalArgumentException("insufficient history …")` when `< 14`; `historyTo = LocalDate.now(clock)`; persist `ForecastRun.create(...)` with `status QUEUED`; `executor.execute(() -> execute(runId))`; return the run.
    - `void execute(UUID runId)` — load; `markRunning()`; re-fetch target + covariates (via `series(id, range)`), assemble `TimesFmRequest`, call `client.forecast(...)`, transpose to `List<ForecastDay>`, `markSucceeded(series, "timesfm-3.0", elapsedMs)`; on `TimesFmException`/`RuntimeException` → `markFailed(message)` (message operator-facing, no payload).
    - `MetricSeries series(MetricId id, TimeRange range)` — `MetricQueryService.query(new MetricQuery(id, range, TimeGrain.DAY, Set.of(), null))`, cast to `TimeSeriesResult`, assert exactly one `MetricSeries`, return it.
    - `@PostConstruct void failInterruptedRuns()` — `findByStatus(RUNNING)` → `markFailed("service restarted before completion")`.
  - `static final int MIN_HISTORY_POINTS = 14;`

- [ ] **Step 1: Write the failing test `ForecastServiceTest`** (unit, fake `TimesFmClient`, synchronous executor `Runnable::run`, fixed `Clock`)

Test methods: `submitPersistsQueuedWithHistoryToToday` — `submit` persists a `QUEUED` run with `historyTo` = clock's today and returns it; `horizonValidated` — `submit` rejects horizon 0 and 91 (`IllegalArgumentException`); `submitRejectsInsufficientHistory` — a target series with 3 non-null points is rejected; `submitRejectsRankedList` — `PRODUCT_TOP_SELLERS` is rejected; `executeMarksSucceeded` — `execute` marks `SUCCEEDED` with the transposed `List<ForecastDay>` and `modelVersion "timesfm-3.0"`; `executeMarksFailedOnClientError` — `execute` marks `FAILED` when the client throws `TimesFmException`; `failInterruptedRunsMarksRunningFailed` — `failInterruptedRuns` flips a `RUNNING` run to `FAILED`.

- [ ] **Step 2: Run it, confirm failure**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.forecasting.ForecastServiceTest`
Expected: compilation failure.

- [ ] **Step 3: Implement `ForecastService`**

Fake-friendly constructor for tests (overload without `@Qualifier`, taking `TaskExecutor` + `Clock` explicitly). `submit` fetches only the target (validation), `execute` re-fetches both. Transpose `TimesFmForecast.dates/quantiles` (index i per date) into `ForecastDay(date, quantiles-by-key)`.

- [ ] **Step 4: Run it, confirm pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.forecasting.ForecastServiceTest`
Expected: PASS.

- [ ] **Step 5: Write `ForecastServiceIntegrationTest`**

Testcontainers Postgres + a fake `TimesFmClient` bean (a `@TestConfiguration` overriding `TimesFmClient`): `submit` then run the executor synchronously, assert the row is `SUCCEEDED` with series persisted; assert `POST`-level behavior is covered in Task 5. (Keep this light: one happy path.)

- [ ] **Step 6: Run it, confirm pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.forecasting.ForecastServiceIntegrationTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
cd backend && ./gradlew spotlessApply
git add backend/src/main/java/com/goldys/platform/forecasting backend/src/test/java/com/goldys/platform/forecasting
git commit -m "feat: forecast orchestration service"
```

---

### Task 5: Application service, controller, DTOs, arch rules

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/forecasting/ForecastableMetrics.java`
- Create: `backend/src/main/java/com/goldys/platform/application/ForecastingApplicationService.java`
- Create: `backend/src/main/java/com/goldys/platform/application/ForecastMetricDto.java`
- Create: `backend/src/main/java/com/goldys/platform/application/CreateForecastRequest.java`
- Create: `backend/src/main/java/com/goldys/platform/application/ForecastRunDto.java`
- Create: `backend/src/main/java/com/goldys/platform/application/ForecastComparisonDto.java`
- Create: `backend/src/main/java/com/goldys/platform/api/ForecastingController.java`
- Modify: `backend/src/test/java/com/goldys/platform/ArchitectureBoundariesTest.java`
- Test: `backend/src/test/java/com/goldys/platform/application/ForecastingApplicationServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ForecastingControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `ForecastService` (Task 4); `semantic.catalog.MetricCatalog`; `auth.{PermissionService, ResourceKey, PermissionAction, UserRole}`; `forecasting.ForecastRun`/`ForecastRunStatus`/`ForecastDay`.
- Produces (all in `application`):
  - `final class ForecastableMetrics { static boolean forecastable(MetricId id); static List<MetricId> forecastable(MetricCatalog catalog); }` — `id != MetricId.PRODUCT_TOP_SELLERS`.
  - `record ForecastMetricDto(String metric, String name, String unit, String sourceDomain, String requiredPermission)`
  - `record CreateForecastRequest(String target, List<String> covariates, LocalDate historyFrom, int horizonDays)`
  - `record ForecastRunDto(UUID id, String status, String targetMetric, List<String> covariates, LocalDate historyFrom, LocalDate historyTo, int horizonDays, Instant createdAt, List<ForecastSeriesPointDto> series, String errorMessage, String modelVersion)` with `record ForecastSeriesPointDto(LocalDate date, BigDecimal median, BigDecimal p10, BigDecimal p90)` (median = quantiles `"0.5"`, p10 = `"0.1"`, p90 = `"0.9"`).
  - `record ForecastComparisonDto(UUID runId, Double mape, Double mae, List<ForecastComparisonPointDto> points)` with `record ForecastComparisonPointDto(LocalDate date, BigDecimal predictedMedian, BigDecimal actual)` (`actual` null when no resolved value yet).
  - `class ForecastingApplicationService` — `static final ResourceKey RESOURCE = new ResourceKey("forecasting")`; methods:
    - `List<ForecastMetricDto> metrics(UserRole role)` — `permissions.require(role, RESOURCE, READ)`, map `ForecastableMetrics.forecastable(catalog)` → DTOs via `catalog.definition(id)`.
    - `ForecastRunDto create(UserRole role, String email, CreateForecastRequest req)` — require `forecasting` READ; for `req.target()` and each covariate require `catalog.definition(metric).requiredPermission()` READ (resource = `new ResourceKey(...)`); delegate to `ForecastService.submit(...)`; map.
    - `ForecastRunDto get(UserRole role, UUID id)` — require `forecasting` READ; re-check the run's target metric READ; map.
    - `List<ForecastRunDto> list(UserRole role)` — require `forecasting` READ; `findAllByOrderByCreatedAtDesc()` mapped.
    - `ForecastComparisonDto comparison(UserRole role, UUID id)` — require `forecasting` READ + target metric READ; load run; for each forecast date `< LocalDate.now(clock)`, fetch the target's actual via `MetricQueryService` at DAY grain and compute MAPE/MAE over points with a non-null, non-zero actual.
  - `class ForecastingController` (`api`, `@RestController`) — `GET /api/forecasting/metrics`, `POST /api/forecasting/runs`, `GET /api/forecasting/runs`, `GET /api/forecasting/runs/{id}`, `GET /api/forecasting/runs/{id}/comparison`; each obtains `@AuthenticationPrincipal AccountUserDetails user` and calls the service with `currentUser.roleOf(user)` / `user.email()`.

- [ ] **Step 1: Write the failing test `ForecastingApplicationServiceTest`** (unit, mock `ForecastService` + `PermissionService` + `MetricQueryService` + `MetricCatalog`)

Test methods: `metricsRequiresForecastingRead` — `metrics` requires `forecasting` READ and returns every id except `PRODUCT_TOP_SELLERS` with the right `name`/`unit`/`sourceDomain`; `createRequiresMetricPermission` — `create` requires the target and each covariate's `requiredPermission` and throws `AccessDeniedException` when missing; `getRequiresMetricPermission` — `get` re-checks the target metric; `comparisonComputesMapeMae` — `comparison` computes MAPE/MAE from a small fixed series (hand-computed values) and leaves `actual` null for future dates.

- [ ] **Step 2: Run it, confirm failure**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.application.ForecastingApplicationServiceTest`
Expected: compilation failure.

- [ ] **Step 3: Implement `ForecastableMetrics`, the DTOs, `ForecastingApplicationService`, `ForecastingController`**

Follow `PlatformMapService` (auth + composition) and `PlatformMapController` (thin delivery) shapes. `comparison` derives actuals by calling `MetricQueryService.query(new MetricQuery(runTarget, new TimeRange(firstForecastDate, today, Calendar.CALENDAR), DAY, Set.of(), null))` and reading the single series' points.

- [ ] **Step 4: Run it, confirm pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.application.ForecastingApplicationServiceTest`
Expected: PASS.

- [ ] **Step 5: Extend `ArchitectureBoundariesTest`**

Add rules (mirror existing): `forecasting` depends only on `semantic` + `auth` + JPA (not `reconciliation`/`canonical`/`ingestion`/`api`/`application`); `application.ForecastingApplicationService` may depend on `forecasting`; `ForecastingController` (in `api`) does not read `canonical`/raw ledger.

- [ ] **Step 6: Write `ForecastingControllerIntegrationTest`**

Testcontainers Postgres + a fake `TimesFmClient` bean: `GET /api/forecasting/metrics` returns 200 with the list when authorized and 403 without `forecasting` READ; `POST /api/forecasting/runs` creates a `QUEUED` run; `GET /api/forecasting/runs/{id}` returns it; `POST` with a covariate the caller cannot read returns 403 (seed a test permission row granting `forecasting` but not the metric).

- [ ] **Step 7: Run the full backend suite**

Run: `cd backend && ./gradlew test spotlessCheck`
Expected: PASS (Docker reachable).

- [ ] **Step 8: Commit**

```bash
cd backend && ./gradlew spotlessApply
git add backend/src/main/java/com/goldys/platform/forecasting backend/src/main/java/com/goldys/platform/application backend/src/main/java/com/goldys/platform/api backend/src/test/java/com/goldys/platform
git commit -m "feat: forecasting API and authorization"
```

---

### Task 6: Python inference service (`forecast-service/`)

**Files:**
- Create: `forecast-service/requirements.txt`
- Create: `forecast-service/predictor.py`
- Create: `forecast-service/app.py`
- Create: `forecast-service/Dockerfile`
- Create: `forecast-service/test_app.py`
- Modify: `docker-compose.yml` (add a `timesfm` dev service)

**Interfaces:**
- Consumes: the HTTP contract in spec §4 (POST `/forecast`, GET `/health`).
- Produces: a runnable service on `:8001` exposing `POST /forecast` and `GET /health`.

- [ ] **Step 1: Write the failing test `forecast-service/test_app.py`**

Using FastAPI `TestClient` with a `FakePredictor` injected (a module-level `predictor` in `app.py`, swapped in the test): `GET /health` → `{"status":"ok","model":"timesfm-3.0","warm":true}`; `POST /forecast` with 3 dates, a target, one covariate, `horizon:2`, `freq:"D"`, 9 quantiles → returns `dates` of length 2 and `quantiles` keyed `"0.1"`…`"0.9"` each of length 2; a request whose `target` length ≠ `dates` length → 422.

- [ ] **Step 2: Run it, confirm failure**

Run: `cd forecast-service && python -m pytest test_app.py -q` (with a venv having fastapi + httpx)
Expected: failure (no `app`).

- [ ] **Step 3: Implement `predictor.py`, `app.py`, `requirements.txt`**

`predictor.py`: a `Predictor` protocol `predict(dates, target, covariates, horizon, freq, quantiles) -> tuple[list[str], dict[str, list[float]]]` and `TimesFmPredictor` wrapping `TimesFM3Forecaster.from_pretrained(os.environ.get("TIMESFM_MODEL", "google/timesfm-3.0-pytorch"))` (loaded lazily on first call). `app.py`: pydantic request/response models; endpoints; convert JSON `null` → `float("nan")` for the model and back for the response. `requirements.txt`: `fastapi`, `uvicorn[standard]`, `pydantic`, `numpy`, `torch`, `timesfm>=3.0.0`, `httpx` (test).

- [ ] **Step 4: Run it, confirm pass**

Run: `cd forecast-service && python -m pytest test_app.py -q`
Expected: PASS.

- [ ] **Step 5: Add `Dockerfile` and the dev compose service**

`Dockerfile`: `python:3.11-slim`, install CPU torch (`--index-url https://download.pytorch.org/whl/cpu`), `pip install -r requirements.txt`, `EXPOSE 8001`, `CMD ["uvicorn","app:app","--host","0.0.0.0","--port","8001"]`; volume for `TIMESFM_CACHE_DIR` (model cache). In `docker-compose.yml`, add a `timesfm` service (build `./forecast-service`, port `8001:8001`, healthcheck `GET /health`). Do **not** edit `docker-compose.prod.yml` in this task — flag it for explicit approval in Task 9.

- [ ] **Step 6: Commit**

```bash
git add forecast-service docker-compose.yml
git commit -m "feat: TimesFM Python inference service"
```

---

### Task 7: Frontend `Api` methods + types + demo fixtures

**Files:**
- Modify: `frontend/lib/api/types.ts` (add types + 5 `Api` methods)
- Modify: `frontend/lib/api/live.ts` (5 live methods)
- Modify: `frontend/lib/api/demo.ts` (5 demo methods + fixtures)
- Modify: `frontend/lib/api/demo.test.ts` (fixture tests)

**Interfaces:**
- Consumes: the REST contract (Task 5).
- Produces: `listForecastMetrics(): Promise<ForecastMetric[]>`, `createForecastRun(input: CreateForecastInput): Promise<ForecastRun>`, `getForecastRun(id: string): Promise<ForecastRun>`, `listForecastRuns(): Promise<ForecastRun[]>`, `getForecastComparison(id: string): Promise<ForecastComparison>`.

- [ ] **Step 1: Add types to `types.ts`** (`ForecastRunStatus`, `ForecastMetric`, `ForecastSeriesPoint`, `ForecastRun`, `CreateForecastInput`, `ForecastComparisonPoint`, `ForecastComparison`) and the 5 method signatures to the `Api` interface.

- [ ] **Step 2: Add the 5 live methods to `live.ts`** (`fetchApi` calls, mirroring `getPlatformMap`).

- [ ] **Step 3: Add the 5 demo methods + fixtures to `demo.ts`**

Deterministic fixtures: `listForecastMetrics` returns a handful (Gross sales → `resolved_daily_sales`, Net sales, Covers → `resolved_reservation_day`, Bookings, Labour cost → `resolved_labour_day`, Food cost % → `derived`); `createForecastRun` returns `{ id: "fc-1", status: "QUEUED", … }`; `getForecastRun` returns a `SUCCEEDED` run with a synthetic median/p10/p90 series; `listForecastRuns` returns 2 runs; `getForecastComparison` returns points with a small MAPE/MAE.

- [ ] **Step 4: Extend `demo.test.ts`** — assert the 5 methods resolve with the right shapes.

- [ ] **Step 5: Run the frontend checks**

Run: `cd frontend && bun run typecheck && bun run lint && bun run test`
Expected: PASS (typecheck now enforces both `liveApi` and `demoApi` implement the new methods).

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/api
git commit -m "feat: forecasting Api methods (live + demo)"
```

---

### Task 8: Forecasting page + nav entry

**Files:**
- Create: `frontend/app/(app)/forecasting/page.tsx`
- Create: `frontend/app/(app)/forecasting/forecasting-page.test.tsx`
- Modify: `frontend/lib/nav.ts` (add "Forecasting" under a group; use `TrendingUp` or `LineChart` icon from `lucide-react`)

**Interfaces:**
- Consumes: `useApi()` (from `frontend/lib/demo-mode.tsx`), the 5 methods (Task 7), Recharts.
- Produces: `/forecasting` route rendering the page described in spec §7.

- [ ] **Step 1: Write the failing page test `forecasting-page.test.tsx`**

Mock `useApi` to return the demo fixtures: assert the page renders the target selector, the covariate multi-select with each covariate's `sourceDomain` shown, a horizon selector (7/14/30/90), a "sources in use" summary, and — after selecting a target and running — the forecast chart and the runs list. Assert the `FAILED` state renders the error message.

- [ ] **Step 2: Run it, confirm failure**

Run: `cd frontend && bun run test forecasting-page`
Expected: FAIL (no page).

- [ ] **Step 3: Implement `forecasting/page.tsx`**

Client component. On mount, `listForecastMetrics()`. A target dropdown, a covariate multi-select (each option labelled `name — sourceDomain`), a horizon segmented control, and a Run button that calls `createForecastRun` then polls `getForecastRun` (e.g. every 2s) until `SUCCEEDED`/`FAILED`. A `LineChart` (Recharts) plots history (target metric's history is *not* returned by the run — show the forecast series with median + p10/p90 band; if history is wanted later, fetch it separately). A runs list from `listForecastRuns` with a "Compare" action calling `getForecastComparison`. Use the existing design-system tokens; keep it accessible (labels, `aria` on the selectors).

- [ ] **Step 4: Add the nav entry** in `frontend/lib/nav.ts` (a new `Intelligence` item `{ title: "Forecasting", href: "/forecasting", icon: LineChart, keywords: ["predict", "timesfm", "revenue", "forecast"] }`, importing `LineChart` from `lucide-react`).

- [ ] **Step 5: Run the frontend checks**

Run: `cd frontend && bun run typecheck && bun run lint && bun run test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/app/\(app\)/forecasting frontend/lib/nav.ts
git commit -m "feat: forecasting page and nav entry"
```

---

### Task 9: Decision doc, docs index, and production wiring (gated)

**Files:**
- Create: `docs/decisions/timesfm-inference-service.md`
- Modify: `README.md` (add Forecasting to "What exists today")
- Modify: `docs/superpowers/README.md` (append the spec/plan rows)
- Modify: `docker-compose.prod.yml` (add the `timesfm` service — **requires explicit user approval; do not run `docker compose` against prod**)

**Interfaces:**
- Consumes: nothing further.

- [ ] **Step 1: Write `docs/decisions/timesfm-inference-service.md`** — context, decision (second Python runtime as a data-agnostic inference service behind a port; the "no microservices" invariant's deliberate exception), consequences (deploy adds a container; CPU latency; non-commercial license — internal use only, GCP route for commercialization). Status: Accepted.

- [ ] **Step 2: Update `README.md`** — add a "Forecasting" row under "What exists today" (TimesFM 3.0, configurable target + covariates, persisted runs, compare-to-actuals) and a note that it needs the `timesfm` service.

- [ ] **Step 3: Update `docs/superpowers/README.md`** — add the `2026-10-12 | TimesFM forecasting | [spec] | [plan] | In progress` row.

- [ ] **Step 4: Ask the user before touching `docker-compose.prod.yml`** — do **not** add the prod service or run any `docker compose` against the prod stack until the user explicitly approves this step. If approved, add the `timesfm` service mirroring the dev definition (with a cache volume and a pinned image/healthcheck); otherwise leave a note in the decision doc that prod wiring is pending.

- [ ] **Step 5: Final verification**

Run: `cd backend && ./gradlew test spotlessCheck build` and `cd frontend && bun run typecheck && bun run lint && bun run test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add docs README.md
git commit -m "docs: TimesFM forecasting decision, README and index"
```

---

## Execution notes

- Tasks 1–5 are one buildable slice (backend, tested against a fake `TimesFmClient`); Task 6 is independently buildable/testable; Tasks 7–8 are independently buildable/testable via demo fixtures. Task 9 wraps up docs + gated prod wiring.
- The Python service (Task 6) and the backend never need to be wired together for any task's tests — each side has its own fake/contract.
