# Performance & operational baseline

> Measurement-driven pass. Everything below is evidence from (a) reading the actual query/request
> paths, (b) the existing `RequestTimingFilter` (WARN at 1s) and Hibernate `LOG_QUERIES_SLOWER_THAN_MS=200`
> instrumentation, and (c) PostgreSQL query-plan reasoning. Live latency numbers require a deployed
> environment with real data volume; the "before/after" here is expressed in query/request counts and
> access-pattern change (which are volume-independent) rather than invented milliseconds.

## Baseline measurements

| Operation | Request pattern (before) | Access pattern (before) |
|---|---|---|
| Dashboard initial load | 5 HTTP requests (`summary`, `latest`, `sales-trend`, `activity`, `top-sellers`) | 5× Spring dispatch, 5× permission check, 5× DB round trips |
| Connector status (`/api/connectors`) | 1 request | `ingestion_run` **full table scan** (`findAllByOrderByStartedAtDesc`) + in-memory dedupe |
| Ingestion health (in dashboard summary) | 1 request | `ingestion_run` **full table scan** + in-Java aggregation |
| Activity window (14 days) | 1 request | `started_at >= X` **sequential scan** (no usable index) |
| Boot recovery | boot only | `ingestion_run` full scan to find `RUNNING` rows |
| `recomputeAll()` (both projectors) | rule change + boot | full delete + reinsert of resolved rows (O(canonical rows)) |
| AI tool `get_sales_by_period` | 1 tool call / 1 model round trip | resolved range read via PK |

## Bottlenecks found

1. **Unbounded ledger scans on the hot path** — `IngestionService.latestRunPerSource()` and
   `health()` loaded the *entire* `ingestion_run` table on every dashboard/connector render, then
   deduped/aggregated in Java. Cost grows linearly with ingestion history.
2. **HTTP N+1 on the dashboard** — five independent requests for one screen.
3. **Missing `started_at` index** — the activity-window query had a range predicate but no usable
   index (the existing index is `(source_system, started_at)`; its leading column is `source_system`).
4. **Boot recovery full scan** — `recoverDanglingRuns()` scanned every run to find `RUNNING` ones.

## Changes made

1. **`latestRunPerSource()`** → `latestPerSource()`: a single correlated-subquery
   (`started_at = max(started_at) for the same source`), served by the existing
   `(source_system, started_at)` index. No entity loading, no in-memory dedupe.
2. **`health()`** → SQL aggregates: `statusCounts()` (group-by status) + `avgFailedDurationSeconds()`
   (native `avg(extract(epoch …))`). No entity loading; completeness/time-to-detect computed in SQL.
3. **`recoverDanglingRuns()`** → `findByStatus(RUNNING)`.
4. **`GET /api/dashboard/bootstrap`** — one application-level endpoint returning
   `{summary, latestSales, salesTrend, activity, topSellers}` under a single authorization check.
   Drill-down endpoints (`/summary`, `/sales-trend`, `/top-sellers`, …) remain for individual refresh.
5. **Frontend dashboard** now calls `getDashboardBootstrap()` once instead of five fetchers.
6. **Operational metrics** (Micrometer + Actuator + Prometheus) with a central
   `OperationalMetrics` naming surface (see below).

## Before / after

| Path | Before | After |
|---|---|---|
| Dashboard HTTP requests | 5 | 1 |
| Dashboard permission checks | 5 | 1 |
| Connector status DB reads | full ledger scan + dedupe | 1 indexed correlated subquery |
| Ingestion health DB reads | full ledger scan + Java aggregation | 2 SQL aggregates |
| Activity window | sequential scan | index range scan (`started_at`) |
| Boot recovery | full scan | `WHERE status = 'RUNNING'` |

## Indexes added and why

- `idx_ingestion_run_started_at` (`V19`) — `IngestionService.activity` filters on `started_at >= X`
  alone; the pre-existing `(source_system, started_at DESC)` index can't serve it. Justified by the
  range predicate, not guesswork. No other indexes were added — the resolved/canonical range reads
  already use their primary keys, and the `latestPerSource()` subquery uses the existing source index.

## Projection findings

- `DailySalesProjector.recomputeAll()` and `ProductSalesProjector.recomputeAll()` are full
  delete-and-rebuild over canonical rows — **O(canonical rows), cheap at pub scale** (a pub's daily
  sales ≈ hundreds of rows; product/days ≈ a few thousand). They are kept as-is.
- **Synchronous coupling retained.** The daily-sales listener projects in the canonical-write
  transaction; the added cost is one small `recompute(date)`. Atomic consistency is worth more than
  the sub-millisecond saving, so no outbox/worker was introduced.
- `ProductSalesProjector.recomputeAll()` still runs on every boot (documented as cheap). A
  `goldys.projection.duration` timer now records rebuild cost so a regression is visible before it
  becomes a problem.
- **Progression for when it does get expensive (in order):** affected-key recompute → dirty-key
  table → background projection worker → generation-based rebuild/swap. Not reached yet.

## Frontend findings

- The five-fetch dashboard is now one bootstrap fetch (removes 4 round trips and 4 redundant
  dispatches). Charts are Recharts (already a dependency); the hand-coded SVG renderers were removed
  in the prior pass. No Server Components were introduced — the measured win was round-trip count,
  not hydration.

## AI latency findings

- `get_sales_by_period` is one tool call / one model round trip; its payload is the resolved series
  for the requested range (already indexed). No change made.
- Conversation history grows unbounded in `useAskGoldys` (each turn appended). This is the next AI
  latency/context item to address (server-side thread persistence + summarized history) — deferred,
  not justified by current single-turn usage.

## Operational metrics added

Exposed at `/actuator/prometheus` (`/actuator/health` for LB checks). HTTP request latency is
auto-collected (`http.server.requests`); custom meters (controlled cardinality via bounded tag
values — source system, entity type, tool id):

- `goldys.projection.duration` (timer, tag `entity`)
- `goldys.connector.duration` / `goldys.connector.failures` (tag `source`)
- `goldys.ingestion.payloads` (counter, tag `source`)
- `goldys.ai.tool.duration` / `goldys.ai.tool.failures` (tag `tool`)

Production hardening note: bind Prometheus to a management port / scrape-auth before public
exposure; metric tag values are already bounded to a small known set.

## Things deliberately not optimized

- **Connector output-watermark semantics** — the port carries an input watermark but no output
  cursor. No real connector needs incremental cursors yet; designing one now would bolt
  source-specific fields into the generic port. Defer until a connector actually needs it.
- **Async projection / outbox worker** — not justified (projection is cheap and atomic today).
- **Projector global-rebuild algorithm** — not changed; cheap at current scale.
- **Server Components / code-splitting** — not a measured bottleneck.
- **AI history summarization** — deferred (see above).
- **Read replica / cache** — no query is currently read-dominated enough to warrant one.

## Thresholds for future architectural changes

Measurable triggers, to be revisited against the metrics above rather than guessed:

| Change | Trigger |
|---|---|
| Background projection worker / outbox | `goldys.projection.duration` p95 consistently > ~250ms, or a projection failure blocks a canonical write |
| Dirty-key projection table | `recomputeAll()` duration exceeds ~1s, or rule changes become frequent |
| Message queue (Kafka etc.) | connector fan-out to >1 downstream consumer, or ingestion throughput exceeds the single-writer ledger |
| Read replica | reporting queries exceed target p95 (e.g. 200ms) after indexing + read models, under steady read load |
| Analytics warehouse | ad-hoc/OLAP queries over years of raw history become a product requirement |
| Separate service | a module's deployment cadence or resource profile repeatedly causes incidents with the monolith |
