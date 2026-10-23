# Current architecture

> Living description of the backend's current dependency direction. Historical plans and
> specifications (`docs/system-context.md`, `docs/prd.md`, `docs/superpowers/…`) remain the
> source-of-truth for *why* the system is shaped this way; this file documents *how* the code is
> organized today.

## Shape

A modular monolith: Spring Boot (Java 25) + PostgreSQL 16, single deployable. No microservices,
message bus, second database, or analytics warehouse. Boundaries are package boundaries, enforced
by a JUnit/ArchUnit test rather than by separate services.

## Data flow

```
raw evidence (immutable ingestion ledger)
   → canonical facts (bitemporal)
      → reconciliation (projectors + resolvers)
         → resolved semantic projections (disposable, recomputable)
            → semantic query interfaces
               → application services
                  → HTTP/SSE delivery + AI reporting tools
```

The invariant is *read direction*, not a ban on reading canonical: reconciliation internals
(projectors, resolvers, override services) read canonical freely. **Final business consumers** must
not derive truth from canonical source observations — they read the resolved projections through the
semantic layer.

## Packages

| Package | Role | May depend on |
|---|---|---|
| `api` | REST controllers (thin adapters: identity + delegation + JSON) | `application`, `auth`, `connectors` (webhook ingest) |
| `conversational` | SSE "Ask Goldy's" endpoint + tool callbacks | `reporting`, `auth`, `api` (shared `NotConfiguredException`), `dashboard` (value records), `semantic` (catalog) |
| `reporting` | Fixed reporting tools (`ReportingTool`) + dispatch + widget spec | `semantic`, `auth` |
| `application` | Use-case services: composition + authorization + response mapping | `semantic`, `reconciliation`, `canonical`, `ingestion`, `auth`, `connectors`, `dashboard`, `reporting` |
| `dashboard` | Saved-dashboard documents, widget records, template catalogue, filters/visibility records + JPA repositories | `semantic` (catalog value types), (JPA) |
| `semantic` | Business-query interfaces + metric records (leaf) | (nothing in-platform) |
| `reconciliation` | Projectors, resolvers, resolved read models, overrides, rules | `semantic` (implements it), `canonical`, `auth` |
| `canonical` | Bitemporal canonical entities + query facades + ingest facades | (JPA + ingestion ids) |
| `ingestion` | Raw ledger + connector runner + run/failure tracking | (JPA) |
| `connectors` | Vendor adapters behind the `SourceConnector` port | `canonical` (ingest), `ingestion` |
| `auth` | Identity, roles, table-driven permissions | (JPA) |
| `widget` | Versioned widget spec records (the only thing the frontend renders) | (nothing in-platform) |
| `config`, `metrics` | Security, request filters, Micrometer metric names | — |

## Application services

Use-case services that a controller delegates to; each owns a coherent read model or write action.

- `DashboardApplicationService` — dashboard bootstrap, summary, activity, top sellers, sales trend.
- `SavedDashboardApplicationService` — saved dashboards: CRUD, templates, sharing, revisions, render.
- `SalesReportingService` — Sales screen's per-source listing + resolved latest total.
- `ReservationReportingService`, `LabourReportingService`, `InventoryReportingService` — domain
  summaries, including cross-domain composition (e.g. hours per cover, food cost %).
- `ReconciliationApplicationService` — exceptions, drill-in, and manual overrides.
- `ConnectorApplicationService` / `ConnectorHealthService` — connector statuses, run, CSV upload,
  freshness.
- `DataExplorerService` — raw / canonical / resolved browsing for the Data explorer.
- `TrustService` / `DataQualityService` — provenance drill-down, trust and freshness state.
- `InvoiceGraphService` — supplier → invoice → line graph for the Kitchen screen.
- `PlatformMapService` — the Data health dependency map (sources → domains → metrics → dashboards,
  plus permission resources) as a `NetworkGraphDto`; composed from the catalogue, `app.freshness`
  config, connector health and the caller's visible dashboards. Gated on `connectors` READ as a whole.

`ResolutionRuleController` is intentionally unchanged: it is already a thin adapter over
`ResolutionRuleService` (which owns rule validation, WRITE authorization, and projection recompute)
and `RuleAuditService`. Those services remain in `reconciliation` because they manage the
reconciliation rule repository directly.

## Semantic query interfaces

Business-named reads that REST, AI tools, and future exports share, so the same business question is
answered by the same implementation:

- `SalesMetricsQuery` — `dailySales(from, to)`, `latestTradingDay()`, `openConflicts()`.
- `ProductMetricsQuery` — `topSellers(from, to, limit)`, `productSales(from, to)`,
  `productSales(date, product)`, `openConflicts()`.

Implemented by `ResolvedDailySalesQuery` / `ResolvedProductSalesQuery` over the resolved
projections. For example, the dashboard sales chart, `get_sales_by_period` (Ask Goldy's), and any
future CSV export of sales all read `SalesMetricsQuery.dailySales(...)` and only differ in how they
*render* the same metric.

## Authorization model

One authorization decision per use case, deliberately placed:

- **Controller** obtains the authenticated `UserRole` (identity only — no check).
- **Application service** authorizes read use cases (`permissions.require(role, resource, READ)`);
  **ToolDispatcher** authorizes AI tool calls the same way.
- **Domain write services** (`DailySalesOverrideService`, `ProductSalesOverrideService`,
  `ResolutionRuleService`) keep their single `WRITE` check at the mutation boundary.
- **Semantic queries** are pure reads and never authorize.

Resources in use: `reconciliation.sales`, `reconciliation.status`, `connectors`,
`reservations.metrics`, `labour.hours`, `labour.cost`, `labour.wages`, `inventory.cost`,
`inventory.stock`, `conversational.chat`, `conversational.threads`, `dashboards`. Each metric in
the catalogue names its required resource (`docs/metrics/catalog.md`). All are granted to
`ALL × OWNER` only; department-scoped grants wait on the stakeholder field-to-role mapping.

## Architecture rules (enforced by `ArchitectureBoundariesTest`)

1. `semantic` is a leaf (no in-platform dependencies).
2. `api` never reads `canonical` or the raw ledger.
3. `reporting` depends only on `semantic` + `auth` (never `canonical`/`reconciliation`).
4. `conversational` never reaches persistence (`canonical`/`reconciliation`/`ingestion`).
5. `dashboard` never reaches `reconciliation`/`canonical`/`ingestion`/`api`.
6. `conversational` never reaches the `dashboard` repositories (only its value records).

## Shared widget / dashboard runtime

A versioned, typed widget contract (`com.goldys.platform.widget`, schema version 2) is the only
thing the frontend renders. It is a discriminated union of `stat`, `time-series`, `bar-chart`,
`table`, and `ranked-list` widgets — data, never code. The AI never emits React/JSX/SQL/handlers;
tools return widget specs built from semantic data.

- **Backend** produces widget specs from `ReportingTool`s (e.g. `get_sales_by_period` → a
  time-series spec). Each spec carries a `WidgetQuery` (tool id + bounded input) so it can be
  re-run.
- **Frontend** renders every widget through one `WidgetRegistry` (Recharts-backed components) plus a
  `WidgetRenderer`. Both the built-in dashboard and Ask Goldy's answers use it; unknown/malformed
  widgets degrade to a notice (validated at the SSE boundary via `parseWidgetSpecs`).
- **Saved dashboards** (`saved_dashboard` table) persist query configuration only, never embedded
  data. Each document carries `filters` (dashboard-level reusable filters), `visibility`
  (`PRIVATE`/`SHARED`/`ORG_WIDE`), `pinned`, and `current_revision`. `GET
  /api/dashboards/{id}/render` re-runs the stored `MetricQuery`s through `MetricQueryService`,
  authorizing each metric at render time via
  `permissions.require(role, metric.requiredPermission, READ)` and merging the dashboard filters
  through the shared `WidgetRenderer` — so a reopened dashboard shows current resolved data, and a
  metric the caller may not read renders an explicit denial instead of a value.
  `docs/contracts/widget-spec.schema.json` and `dashboard-document.schema.json` are the
  machine-readable contracts.
- **Versioning** — every create/update/restore writes a `saved_dashboard_revision` snapshot (the
  serialized document plus the share roles in force), keyed by `current_revision`, so a past
  revision can be restored through the normal update path.
- **Sharing** — `saved_dashboard_share` holds one row per role grant (department × seniority) for
  `SHARED` dashboards; `ORG_WIDE` and `PRIVATE` need no rows.
- **Templates** — `DashboardTemplateCatalog` exposes nine code-based starting points built from
  catalogue `MetricId`s (valid by construction); instantiating one routes through the normal create
  path, never a separate render path.

### Data-ownership choice

Widgets in a saved dashboard store the semantic query (`MetricQuery` — metric, range, grain,
dimensions, comparison), not a snapshot. Rendering always re-runs those queries live, so dashboards
stay current. Revisions snapshot the *document* (query config + shares) for history/restore, never
the rendered data.



### Data-handling corrections (15 October 2026)

Semantic period summaries use `TimeGrain.TOTAL`; saved stat/table widgets request the same period
aggregation, retaining qualification notices. API, tool and saved-dashboard reads check the
transitive operand permissions of derived metrics. Explicit permission-table rows grant managers
aggregate sales, bookings and labour, and known authenticated roles forecasting with aggregate
support; raw sources and individual pay remain restricted. New/custom roles require explicit grants.

Forecast submission freezes the exact internal model request and its qualifications in JSONB before
queueing. Execution uses that snapshot; legacy queued work without one requires resubmission.
Forecasting receives a qualified `Clock` bean backed by `VenueClock`, keeping its package independent
of configuration classes. Snapshotting does not freeze external weather or model artefacts.


### Integrated data foundation (16 October 2026)

The bounded relational query path has its own typed registry/compiler/executor. It reads the
registered resolved projections plus the explicitly approved single-source invoice and CTB
product-sale observations (`CANONICAL_DIRECT`). It does not expose raw tables or a generic AI tool.
These source observations retain source basis and are not a substitute for reconciled metrics.
Detailed source costs require `inventory.cost`, including for predicates/grouping/order operands.

The executor uses an independent read-only transaction and `SET LOCAL ROLE goldys_ro`; the role
has SELECT only on the allowlisted analytical tables. V94 grants role membership to the principal
used by the application's Flyway datasource. A deployment with a separate migration principal
must explicitly grant the application principal membership before enabling queries. There is no
fallback to unrestricted execution. Raw-table reads and DML are exercised as denied operations.

Parent SUM/AVG/COUNT across a one-to-many join are rejected; DISTINCT monetary values do not repair
entity fanout. Users can query parent aggregates independently or sum child facts. Query responses
carry explicit coverage, source-basis and limit notices; saved query widgets retain those notices.
Saved-query filters are used as saved, with a visible notice when dashboard-level filters cannot
apply. Saved definitions remain configuration revisions, not retained report results.

Unreleased foundation migrations V74–V80 have been sequenced as V86–V92, after the current main and
drift migrations. V93 stores idempotent source-observation receipts; V94 enables the analytical role.
No already-applied main migration is edited. A test upgrades a disposable V82 schema through the
combined migrations without enabling out-of-order execution. None was applied to production.


### Dated inventory observation reads (16 October correctness increment)

`resolved_inventory_observation` is a disposable SQL view of current canonical count and waste lines,
with current same-source stocktake-header partial/locked context. `ResolvedInventoryObservationQuery`
implements the semantic `InventoryObservationQuery` port. The application checks both
`inventory.stock` and `inventory.cost` before paged API reads. Raw UUIDs/version IDs remain in the
projection for lineage and are omitted from its business DTO. No new grants, raw browsing, source
writes or AI tool are introduced. Counts are dated observations, not current balances. Units and
nullable source costs are preserved; heterogeneous quantities are never totalled.

Sales, food/beverage, product, labour, booking, inventory and payment calendar-grid metrics carry `MetricCoverage` in provenance and reason/count notices.
Observed values include explicit zeros; null projection values and absent dates are distinct.
The expected trading population remains unverified even when every requested date has a value.
This metadata introduces no age expiry, estimated missing money, valuation or statistical bound.


### Inventory receipt protection (16 October 2026)

Canonical stocktake/count/waste writers share an internal processing-receipt repository and the
existing source-fact locks. Receipt insertion and canonical version supersession share one
transaction. Equal-value receipts remain evidence, preventing old intermediate observations from
manufacturing revisions after a correction. Count version provenance includes its nullable
canonical header-version FK, and count receipt identity includes that header context. The
observation view and semantic/API authorization remain unchanged.

Inventory replay now uses the existing lightweight raw reference query and loads bytes per payload;
the former inventory-only bulk-payload/time facade and duplicate repository finder were removed
after their only caller moved. No new ingestion port, domain, permission or frontend call is added.
