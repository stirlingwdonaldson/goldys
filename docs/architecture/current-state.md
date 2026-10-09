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

