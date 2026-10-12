# TimesFM Forecasting — Design

**Status:** Draft
**Date:** 2026-10-12
**Scope:** A dedicated Forecasting page that predicts a chosen business metric (the **target**) using
Google's **TimesFM 3.0** zero-shot model, with selectable past-only **covariate** inputs whose sources
are shown, persisted asynchronous forecast runs, and a compare-to-actuals view. Design only; the
executable plan is `docs/superpowers/plans/2026-10-12-timesfm-forecasting.md`.

---

## 1. Objective

Staff can ask "what will next month look like?" and get a model answer, not a hand-rolled trend line.
The page lets a user pick *what to predict* and *what to use to predict it*, see exactly which sources
feed the model, and later check how accurate the forecast was.

Three concrete needs, in order:

1. **Predict a target metric** (e.g. `sales.gross`) over a horizon (7/14/30/90 days) with uncertainty
   bands, using TimesFM 3.0 zero-shot (no per-venue training).
2. **Choose the inputs** — a set of past-only covariates from the resolved metric catalogue (e.g.
   `reservations.covers`, `labour.cost`, `inventory.purchases`) — and **see which source/domain each
   comes from**.
3. **Persist each run and compare to actuals** — reopen a past forecast and see how it did against
   the resolved values that later arrived (MAPE/MAE + per-date table).

Out of scope for v1 (see §13): exposing forecasting to Ask Goldy's, export, future-known covariates
(forward bookings/rosters), and per-dimension *separate* series (labour split by department, product
split by SKU — v1 forecasts each metric's summed total).

## 2. Decisions

| # | Decision | Choice |
|---|---|---|
| A | Model | **TimesFM 3.0** (`google/timesfm-3.0-pytorch`, 330M params), zero-shot, natively multivariate with past-only covariate support. |
| B | Hosting | A **self-hosted Python inference service** (FastAPI + PyTorch), not Google Cloud. Data stays in-house; full covariate support; no per-call cost. The Java backend calls it over HTTP behind a port. |
| C | Service boundary | The Python service is **data-agnostic**: it receives aligned numeric arrays (`dates`, `target`, `covariates`) and returns quantile arrays. It knows nothing about Goldy's schema, metrics, or permissions. Java does all data preparation from the semantic layer. |
| D | Run lifecycle | **Async runs + persisted series.** Creating a forecast writes a `forecast_run` row (`QUEUED → RUNNING → SUCCEEDED/FAILED`); the Java backend calls the Python service, stores the returned quantile series, and the page polls until done. Chosen because 330M params on CPU takes seconds-to-tens-of-seconds, and compare-to-actuals needs the series persisted. |
| E | Data source | Read the **resolved metric catalogue** through `MetricQueryService` (the same path dashboards and Ask Goldy's tools use). No new read path, no reading canonical/raw. |
| F | Inputs (covariates) | **Past-only covariates** drawn from the resolved catalogue, aligned to the target's daily dates. No future-known covariates in v1 (the forward data isn't in the resolved layer). |
| G | Forecastable set (v1) | Every catalogue metric whose result is a **time series** — which is all of them except `product.top_sellers` (a ranked list). Each metric forecasts as its **summed daily total**: the executors already sum across any dimension (labour = all departments, product sales = all SKUs, reservations = all periods). Per-dimension *separate* series (labour split by department, product by SKU) need a dimension filter the current query interface does not expose, so they are deferred to §13. The metrics endpoint computes the set, so the UI never offers an unforecastable metric. |
| H | Quantiles | Store all nine deciles TimesFM 3.0 returns (`0.1 … 0.9`); render the median plus a `0.1–0.9` band. |
| I | Persistence | One new `forecast_run` table; the series is stored as JSONB on the row (compare-to-actuals loads the whole series anyway, so no separate point table). |
| J | Authorization | New `forecasting` resource, seeded `ALL × OWNER` (READ + WRITE). Creating a run also re-checks each selected metric's `requiredPermission` READ, exactly as dashboard render does. |
| K | Second runtime | This deliberately adds a **second runtime** (Python). Recorded as a decision (see §10): the Java backend stays a single monolith; the inference service is a data-agnostic external dependency behind a port, with the same standing as Postgres or the external LLM. |
| L | Licensing | TimesFM 3.0's open weights are under a **non-commercial license**. Fine for the pub's own internal forecasting; commercializing the product would need the Google Cloud (BigQuery ML/Vertex) route. Recorded as a constraint, not a blocker. |
| M | Feature flag | `forecasting.timesfm.enabled` (default off in CI/demo). With it off, the app boots and the page works from demo fixtures without the Python service. |

## 3. Backend architecture

New package `com.goldys.platform.forecasting`. It is a consumer of `semantic` and `auth` (like
`reporting` and `dashboard`), and is never reached by `canonical`, `reconciliation`, or `ingestion`.

| Unit | Role |
|---|---|
| `ForecastRun` (JPA entity) + `ForecastRunRepository` | Persisted run (schema §5). |
| `TimesFmClient` (port) + `TimesFmHttpClient` | Adapter calling the Python service over JDK `HttpClient` — the same shape as `CtbClient` / the `SourceConnector` port. |
| `ForecastSeriesAssembler` | Pure, unit-tested mapping from `MetricQueryService` results to the aligned numeric arrays sent to the model. |
| `ForecastService` | Orchestration: validate the request, fetch each series, assemble, call the port, persist, advance status. |
| `ForecastingApplicationService` | One authorization decision per use case, then maps to response DTOs (mirrors the other `*ReportingService`s). |
| `ForecastingController` | Thin REST adapter in `api`. |

The forecastable-metrics list and the run's target/covariates are described by a small record:

```java
// in forecasting
record MetricRef(MetricId metric, String dimensionValue) {}  // dimensionValue always null in v1
record ForecastRequest(MetricRef target, List<MetricRef> covariates,
                       LocalDate historyFrom, int horizonDays) {}
```

`ForecastService` resolves each `MetricRef` to a single `MetricSeries` via
`MetricQueryService.query(new MetricQuery(metric, range, DAY, Set.of(), null))`, asserting exactly one
series (the v1 forecastable-set rule, §2 G).

## 4. Python inference service (`forecast-service/`)

FastAPI + PyTorch, `timesfm` package. Loads `TimesFM3Forecaster.from_pretrained("google/timesfm-3.0-pytorch")`
once at startup and stays warm.

**`POST /forecast`**

```jsonc
// request
{
  "dates": ["2026-09-01", "…", "2026-10-11"],      // ascending, one per day (no gaps)
  "target": [1234.5, "…"],                          // length == dates; null = missing
  "covariates": { "covers": [85, "…"], "…": ["…"] }, // optional, each length == dates
  "horizon": 30,                                    // days ahead
  "freq": "D",
  "quantiles": [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]
}
// response
{
  "dates": ["2026-10-12", "…", "2026-11-10"],       // horizon dates, ascending
  "quantiles": { "0.1": ["…"], "0.5": ["…"], "0.9": ["…"] } // each length == horizon
}
```

**`GET /health`** → `{ "status": "ok", "model": "timesfm-3.0", "warm": true }`.

- **Config via env**: `TIMESFM_MODEL` (default `google/timesfm-3.0-pytorch`), `TIMESFM_DEVICE`
  (`cpu`/`cuda`), `TIMESFM_CACHE_DIR`.
- **Dockerized**; added to `docker-compose.yml` (dev) and `docker-compose.prod.yml` with a healthcheck
  on `/health`.
- The model download is ~hundreds of MB; cache it in a volume so restarts don't re-download.

## 5. Data model (V44)

```sql
CREATE TABLE forecast_run (
  id                uuid PRIMARY KEY,
  created_at        timestamptz NOT NULL,
  created_by        varchar(255) NOT NULL,   -- caller email, matching saved_dashboard.created_by
  target_metric     text NOT NULL,          -- MetricId, e.g. 'sales.gross'
  target_dimension  text,                   -- null in v1
  covariates        jsonb NOT NULL,         -- [{ "metric": "reservations.covers", "dimensionValue": null }, …]
  history_from      date NOT NULL,
  history_to        date NOT NULL,
  horizon_days      int  NOT NULL,
  grain             text NOT NULL DEFAULT 'DAY',
  status            text NOT NULL,          -- QUEUED | RUNNING | SUCCEEDED | FAILED
  error_message     text,
  model_version     text,                   -- e.g. 'timesfm-3.0'
  elapsed_ms        bigint,
  forecast_series   jsonb,                  -- [{ "date": "…", "q0.1": …, … "q0.9": … }], set on SUCCEEDED
  CHECK (horizon_days > 0)
);
CREATE INDEX forecast_run_created_at_idx ON forecast_run (created_at DESC);
```

Permission seed (V45): `forecasting` resource, `READ` + `WRITE`, for `ALL × OWNER` only (the same
shape as `V23`, `V26`, `V39`).

## 6. API + frontend contract

| Endpoint | Purpose |
|---|---|
| `GET /api/forecasting/metrics` | The forecastable metric list: `{ metric, name, unit, sourceDomain, requiredPermission }`, derived from `MetricCatalog` filtered by the §2 G rule. |
| `POST /api/forecasting/runs` | Body `ForecastRequest`. Returns `{ id, status: "QUEUED" }`. |
| `GET /api/forecasting/runs/{id}` | Run status + (on `SUCCEEDED`) the series and model metadata. |
| `GET /api/forecasting/runs` | List of runs, newest first (single venue, owner-level access — no per-user filter in v1). |
| `GET /api/forecasting/runs/{id}/comparison` | Forecast vs actuals: for each forecast date now in the past, the predicted median/bounds and the resolved actual, plus MAPE/MAE. |

Frontend `Api` additions (both `liveApi` and `demoApi`, plus `types.ts`):
`listForecastMetrics`, `createForecastRun`, `getForecastRun`, `listForecastRuns`,
`getForecastComparison`.

## 7. Frontend page (`/forecasting`)

New route `app/(app)/forecasting/page.tsx` + a nav entry. Layout:

- **Target selector** — a metric dropdown populated from `listForecastMetrics`.
- **Covariate multi-select** — pick zero-or-more inputs; each option shows its `sourceDomain` (e.g.
  "Gross sales — resolved_daily_sales"). The selected set feeds a **"sources in use"** panel listing
  the target and each covariate with its domain and unit.
- **Horizon** — 7 / 14 / 30 / 90 days.
- **Run** — creates the run and polls `getForecastRun` until `SUCCEEDED`/`FAILED`.
- **Chart** — Recharts: the target's history, the forecast median, and a shaded `0.1–0.9` band.
  Failure renders the operator-facing message.
- **Runs list** — from `listForecastRuns`; selecting a past run opens the **compare-to-actuals** view
  (predicted vs resolved actual per date + MAPE/MAE).

Demo fixtures return a plausible forecast for a couple of metrics so the page is fully usable with
`NEXT_PUBLIC_DEMO_MODE` on and no Python service.

## 8. Authorization

- New resource `forecasting`; seed `ALL × OWNER` (`READ` + `WRITE`). No BOH/FOH guesses (the
  field-to-role open question still gates those).
- `GET /api/forecasting/metrics` and `GET …/runs` → `forecasting` READ.
- `POST /api/forecasting/runs` → `forecasting` READ, **plus** `requiredPermission` READ for the target
  and each covariate, so a metric the caller can't read can't be silently forecast either.
- `GET …/runs/{id}` and `…/comparison` → `forecasting` READ (the run's own metrics were already
  gated at creation; the caller's current metric grants are re-checked at read time like dashboards).
- A denied check throws `AccessDeniedException` → explicit "not permitted" (never a partial list).

## 9. Errors and edge cases

- **Python service unreachable / non-2xx** → run `FAILED` with an operator-facing `error_message`
  (no payload, credentials, or tokens — the `ConnectorFetchException` rule). The app stays healthy.
- **Missing days** → the assembler pads gaps with `null`; the model handles nulls, and the run carries
  an "N days unresolved" notice (the metric "display + note" philosophy). A target with *no* resolved
  data at all is rejected up front with a clear message.
- **Too little history** → if fewer than, say, 14 resolved points remain, reject with a clear message
  (a forecast from nothing is noise).
- **First-call latency** → the Python service warms at startup; `TimesFmHttpClient` uses a generous
  read timeout (the async run absorbs the wait).
- **Concurrency** → runs are independent rows; no global lock. The Python service may serve one
  request at a time on CPU; a queue of runs is fine (each run just waits).

## 10. Architecture fit

- **Read direction:** `forecasting` reads only through `semantic` (`MetricQueryService` +
  `MetricCatalog`). It never reads `canonical`, `reconciliation`, or the raw ledger. Forecasts are
  *predictions*, a new consumer of resolved data — not canonical facts, so they need no bitemporal
  treatment and no projector.
- **`ArchitectureBoundariesTest`:** add rules that `forecasting` depends only on `semantic` + `auth`
  (+ JPA) and is not reachable from `reconciliation`/`canonical`/`ingestion`; and that
  `ForecastingController` does not read `canonical`/raw.
- **Permissions:** one new resource, seeded `ALL × OWNER` (§8).
- **Decision doc:** record the second-runtime exception and the licensing constraint in
  `docs/decisions/timesfm-inference-service.md` (context, decision, consequences) so the
  "no microservices" invariant's deliberate exception is explicit, not implicit.

## 11. Testing

- **Backend unit:** `ForecastSeriesAssembler` (alignment, null-padding, the v1 forecastable-set rule,
  rejection when no resolved data / too little history); `ForecastService` status transitions and
  persistence; `TimesFmHttpClient` (mocked HTTP: happy path, non-2xx → `FAILED`, timeout).
- **Backend integration** (`*IntegrationTest`, Testcontainers Postgres): V44/V45 apply; create→poll→
  compare round-trip against a fake `TimesFmClient`; `POST /api/forecasting/runs` denied without the
  metric's permission; `ArchitectureBoundariesTest` passes.
- **Python service:** unit tests for the `/forecast` handler shape (array lengths, quantile keys) with
  a fake predictor; `/health`.
- **Frontend (Vitest):** the page and `buildForecastSeries` chart mapping; demo fixtures for every new
  `Api` method; `typecheck` + `lint`.

## 12. Risks

1. **CPU inference latency.** 330M params on CPU may take 10s–60s+ per forecast. Mitigated by the
   async run model (the UI never blocks). If unacceptable, the service can move to a GPU box or a
   smaller checkpoint — the port makes that a deploy change, not a code change.
2. **Data sparsity.** The pub has ~1–2 years of daily resolved data (CTB from 2025-10, MarketMan
   history to 2022). Enough for zero-shot daily forecasting; the "too little history" guard (§9)
   prevents pathological requests. Verify real history length against production before shipping.
3. **Non-commercial license.** Internal use is fine; productizing would require the GCP route. Recorded
   (§2 L), not a code risk.
4. **Model quality on pub data.** Zero-shot on hospitality daily series is reasonable but unproven
   here. Compare-to-actuals is exactly the mechanism to measure it; treat v1 forecasts as
   experimental and show them with uncertainty bands and the provenance of inputs.

## 13. Later (not in this spec)

- **Per-dimension series** (labour by department, product by SKU, reservations by service period):
  needs a dimension filter that yields one series per dimension value; the current `MetricQueryService`
  executors always sum to a single total. The model already supports multi-series input.
- **Future-known covariates** (forward bookings, rostered shifts, planned closures): needs forward
  data in the resolved layer first.
- **Ask Goldy's forecasting tool**: a bounded, enum-validated tool per
  `docs/contracts/ai-tool-boundary.md` that returns a typed forecast widget.
- **Export** (CSV/PDF) of a forecast or a comparison.
- **GPU** or a lighter checkpoint if CPU latency becomes the bottleneck.
