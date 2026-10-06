# Semantic Metric Catalogue — Design

**Status:** In review
**Date:** 2026-10-06
**Scope:** Design for a unified semantic metric catalogue — a stable, bounded, machine-readable
definition of every business metric (the "what does this number mean" layer) plus a controlled
query interface, derived metrics, a reusable period-comparison framework, and provenance metadata —
that dashboards, AI reporting tools, and exports migrate onto. No implementation; design only.

This design consolidates and formalizes the per-domain semantic query interfaces built across
`docs/superpowers/specs/2026-10-02-reporting-tool-framework-design.md`,
`docs/superpowers/specs/2026-10-06-daily-sales-read-model-design.md`,
`docs/superpowers/specs/2026-10-06-product-sales-read-model-design.md`, and
`docs/superpowers/specs/2026-10-06-next-domains-reservations-labour-inventory-design.md`. It does not
replace them; it layers a catalogue over the resolved projections they established.

---

## 1. Objective

Remove the ambiguity that lets two parts of Goldy's compute the same named metric differently
("net sales", "POS-only sales", "reconciled sales", "sales excluding refunds", "trading-day sales",
"calendar-day sales"), by pinning every business metric to:

- a stable machine-readable ID,
- a documented definition contract (formula, unit, source domain, dimensions, grains, permission,
  null/missing semantics, comparison semantics, version),
- a single controlled query path, and
- provenance on every result.

The result must make it *difficult* — not merely discouraged — to compute the same named metric two
ways.

### Guiding constraints (binding)

1. **Modular, additive, descriptive** — the catalogue is not a new central monolith or a constraint
   machine. Adding a metric = one `MetricId` constant + one small executor bean + one
   `MetricDefinition` entry. Adding a dashboard panel = a persisted `MetricQuery` (configuration),
   not new code.
2. **Grafana-shaped dashboard/query model** — a dashboard is a set of panels; a panel is a small
   typed query (metric + time range + grain + group-by) plus a rendering type; queries are
   persisted, re-runnable configuration.
3. **No generic SQL abstraction** — bounded enums only; arbitrary columns, arbitrary SQL, free-text
   filters, and dynamic expressions from the LLM are structurally impossible.
4. **Explicit, tested query implementations preserved** — the existing resolved `*MetricsQuery`
   interfaces and their `reconciliation` implementations remain the tested core; executors wrap
   them, they do not replace them.
5. **Reconciliation administration is not forced into the catalogue** — rules/overrides UI,
   per-source listings, and drill-in stay exactly as they are (provenance, not metrics).

---

## 2. Terminology resolution (one concept, one name)

| Ambiguous term | Resolution |
|---|---|
| **net sales** | `sales.net` — resolved net sales = gross − GST (`netTotal`). AUD. |
| **reconciled sales** | Synonym for **resolved** sales — the authoritative post-reconciliation value. `sales.gross`/`sales.net` *are* the reconciled figure. One name. |
| **POS-only sales** | **Not a business metric.** Lightspeed's raw unreconciled figure, already surfaced on the Sales screen as per-source *provenance*. Catalogue rule: business metrics are always resolved; single-source raw figures are reconciliation provenance. |
| **sales excluding refunds** | `sales.excluding_refunds` — **defined but deferred** (refunds not ingested; see §11). |
| **trading-day vs calendar-day sales** | Not distinct metric IDs — a *time-semantics* parameter. Sales metrics are queried with a `Calendar ∈ {TRADING, CALENDAR}`. Today both resolve to "calendar day in venue timezone"; the exact boundary is a deferred business decision (§11). |

---

## 3. The metric catalogue

Every metric is registered under a stable `MetricId` with a `MetricDefinition` contract. IDs are
stable, machine-readable, dotted strings — never physical table or Java class names.

### 3.1 Base metrics (read resolved projections; live today)

| ID | Name | Business definition / formula | Unit | Source domain | Valid dimensions | Grains | Null/missing semantics |
|---|---|---|---|---|---|---|---|
| `sales.gross` | Gross sales | Resolved gross sales incl. GST (`totalSales`) | AUD | `resolved_daily_sales` | — | day, week, month | STRICT: aggregate is null if any day in range is unresolved |
| `sales.net` | Net sales | Resolved `netTotal` = gross − GST | AUD | `resolved_daily_sales` | — | day, week, month | STRICT |
| `sales.gst` | GST | Resolved `gstTotal` | AUD | `resolved_daily_sales` | — | day, week, month | STRICT |
| `reservations.bookings` | Bookings | Resolved bookings | count | `resolved_reservation_day` | service_period | day, week, month | sum (absent day = no row, not zero, unless projected) |
| `reservations.attended` | Attended parties | Resolved attended | count | `resolved_reservation_day` | service_period | day, week, month | sum |
| `reservations.covers` | Covers | Resolved covers (guests) | count | `resolved_reservation_day` | service_period | day, week, month | sum |
| `reservations.no_shows` | No-shows | Resolved no-shows | count | `resolved_reservation_day` | service_period | day, week, month | sum |
| `labour.scheduled_hours` | Scheduled hours | Resolved scheduled hours | hours | `resolved_labour_day` | department | day, week, month | sum of non-null |
| `labour.actual_hours` | Actual hours | Resolved actual hours | hours | `resolved_labour_day` | department | day, week, month | sum of non-null |
| `labour.cost` | Labour cost | Resolved actual cost | AUD | `resolved_labour_day` | department | day, week, month | STRICT: null if any day's cost is unknown |
| `inventory.purchases` | Purchases (COGS) | Resolved purchases | AUD | `resolved_inventory_day` | — | day, week, month | sum of non-null |
| `inventory.wastage` | Wastage | Resolved wastage | AUD | `resolved_inventory_day` | — | day, week, month | null when no wastage data at all |
| `inventory.stock_on_hand` | Closing stock | Resolved stock-on-hand | AUD | `resolved_inventory_day` | — | day | latest value |
| `product.sales_amount` | Product sales amount | Resolved product amount | AUD | `resolved_product_sales` | product | day, week, month | sum |
| `product.sales_quantity` | Product sales quantity | Resolved product quantity | units | `resolved_product_sales` | product | day, week, month | sum |

**Missing-data semantics** (two declared kinds, per metric):

- **STRICT** — the period aggregate is `null` (with the unresolved dates listed in provenance) when
  any constituent day is unresolved. Used for financial totals where a partial sum would understate
  truth. Matches the existing `labourCost` behavior and the dashboard "Needs decision" pattern.
- **SUM** — the aggregate sums resolved values; days with no resolved row are *not* silently
  zeroed — they are recorded in `MetricProvenance.missingPeriods` and the result is flagged partial.
  Used for counts already treated as summable.

This is the enforcement of "do not silently convert unknown data to zero": an unresolved day is
never `0`, it is either a `null` aggregate (STRICT) or an explicit `missingPeriods` entry (SUM).

### 3.2 Derived metrics (explicit formulas over resolved metrics)

Derived metrics are `MetricId`s whose executor composes other executors' results **in code** (a plain
Java function, not a parsed formula language). They centralize what today is duplicated across
`LabourReportingService` and `InventoryReportingService`.

| ID | Formula | Unit | Zero/unknown behavior |
|---|---|---|---|
| `reservations.no_show_rate` | `no_shows ÷ bookings` | % | null when bookings = 0 |
| `reservations.booking_to_cover_conversion` | `attended ÷ bookings` | % | null when bookings = 0 |
| `reservations.avg_party_size` | `covers ÷ attended` | ratio | null when attended = 0 |
| `sales.average_spend_per_cover` | `sales.gross ÷ reservations.covers` | AUD | null when covers = 0 or gross unresolved |
| `labour.hours_per_cover` | `labour.actual_hours ÷ reservations.covers` | hours/cover | null when covers = 0 |
| `labour.cost_per_cover` | `labour.cost ÷ reservations.covers` | AUD/cover | null when covers = 0 or cost unknown |
| `labour.hours_variance` | `labour.scheduled_hours − labour.actual_hours` | hours | — |
| `labour.foh_percent` | FOH `labour.cost ÷ sales.gross` | % | null when gross = 0/unresolved or FOH cost unknown |
| `labour.boh_percent` | BOH `labour.cost ÷ sales.gross` | % | null when gross = 0/unresolved or BOH cost unknown |
| `inventory.food_cost_percent` | `inventory.purchases ÷ sales.gross` | % | null when gross = 0/unresolved or purchases unknown |
| `product.top_sellers` | ranked product list by summed resolved amount | list | items with unresolved days flagged, summing resolved rows only |

`labour.foh_percent` / `labour.boh_percent` bind the department at the metric ID (FOH/BOH are
distinct metrics) rather than via a free department filter, keeping the surface bounded. A future
arbitrary department split is a *dimension* question, deferred to §11.

### 3.3 Required pipeline extension: `sales.net` / `sales.gst`

`resolved_daily_sales` currently stores only `total_sales` (gross), while both Lightspeed
(`total − gst`) and CTB (`net` field) already populate `gst_total`/`net_total` in canonical. To make
`sales.net`/`sales.gst` *resolved* metrics:

1. Add `net_total` and `gst_total` columns to `resolved_daily_sales` (new Flyway migration).
2. Generalize `DailySalesResolver` / `DailySalesProjector` to resolve per-field (total, gst, net)
   with the same override → agreement → rule → unresolved precedence.
3. Extend `DailySalesMetric` to carry `net`/`gst`.

This touches only the resolved read model, not reconciliation *administration* (rules/overrides UI),
consistent with constraint 5.

---

## 4. Type model & query API

All types are in the `semantic` package (a leaf; see §6). Illustrative signatures — exact forms are
finalized in the implementation plan.

```java
public enum MetricId {   // stable, machine-readable
  SALES_GROSS("sales.gross"), SALES_NET("sales.net"), SALES_GST("sales.gst"),
  RESERVATIONS_BOOKINGS("reservations.bookings"), /* … */
  RESERVATIONS_NO_SHOW_RATE("reservations.no_show_rate"),
  LABOUR_FOH_PERCENT("labour.foh_percent"),
  INVENTORY_FOOD_COST_PERCENT("inventory.food_cost_percent"),
  SALES_AVERAGE_SPEND_PER_COVER("sales.average_spend_per_cover");
}

public enum TimeGrain { DAY, WEEK, MONTH }
public enum Calendar  { TRADING, CALENDAR }        // boundary deferred; both = venue-local today
public enum Dimension { SERVICE_PERIOD, DEPARTMENT, PRODUCT }  // empty set = no grouping

public enum Comparison {
  PREVIOUS_DAY, PREVIOUS_WEEK, SAME_WEEKDAY_LAST_WEEK,
  SAME_PERIOD_LAST_YEAR, ROLLING_4_WEEKS, ROLLING_12_WEEKS,
  BUDGET, FORECAST                                 // declared; deferred (no data source)
}

public record TimeRange(LocalDate from, LocalDate to, Calendar calendar) {}

public record MetricQuery(
    MetricId metric, TimeRange range, TimeGrain grain,
    Set<Dimension> dimensions, Comparison comparison) {}   // comparison optional

// Results: sealed union of shapes; leaf types (no widget dependency).
public record MetricPoint(LocalDate bucketStart, BigDecimal value) {}   // value null = unresolved
public record Series(String dimensionValue, List<MetricPoint> points) {}
public record RankedItem(String label, BigDecimal primary, BigDecimal secondary) {}

public sealed interface MetricResult permits TimeSeriesResult, RankedListResult {
  MetricId metric();
  MetricProvenance provenance();
}
public record TimeSeriesResult(MetricId metric, List<Series> series, MetricProvenance provenance)
    implements MetricResult {}
public record RankedListResult(MetricId metric, List<RankedItem> items, MetricProvenance provenance)
    implements MetricResult {}

public record MetricProvenance(
    MetricId metric, String definitionVersion, TimeRange range, TimeGrain grain,
    String sourceDomain, Instant dataFreshness, List<LocalDate> missingPeriods,
    String calculationVersion) {}

public interface MetricQueryService { MetricResult query(MetricQuery query); }
public interface MetricExecutor { MetricId id(); MetricResult evaluate(MetricQuery query); }
```

**Result shapes:**

- `TimeGrain` + empty dimension set → one `Series`.
- `TimeGrain` + a dimension → one `Series` per dimension value (renders as multi-series or table).
- `product.top_sellers` → `RankedListResult`.

**What is structurally impossible:** arbitrary column names, arbitrary SQL, free-text filters, and
dynamic expressions — `MetricQuery` carries only a bounded `MetricId`, bounded
`TimeGrain`/`Dimension`/`Comparison` enums, and a date range. There is no stringly-typed surface for
an LLM to inject anything into.

---

## 5. Executors, derived metrics, comparison, provenance

### 5.1 Executors

- **Base executors** wrap the existing `*MetricsQuery` interfaces (`SalesMetricsQuery`,
  `ReservationMetricsQuery`, `LabourMetricsQuery`, `InventoryMetricsQuery`, `ProductMetricsQuery`),
  unchanged. They read the resolved projection, bucket by grain, and apply the metric's declared
  missing/STRICT semantics. A few thin aggregate methods are added where a range aggregate does not
  yet exist (e.g. `ReservationMetricsQuery.bookings(from, to)`).
- **Derived executors** call other executors and compose in code, applying the zero/unknown rules in
  §3.2.
- **Registry** — `Map<MetricId, MetricExecutor>` populated by Spring beans. No codegen, no
  reflection, no annotation framework.

### 5.2 Comparison framework

Centralized in `semantic`; a small composable function, not an engine. Given a `MetricQuery` with a
`Comparison`, it derives the reference period (e.g. `SAME_WEEKDAY_LAST_WEEK` shifts both bounds back
7 days) and returns the current and reference `MetricResult`s plus a delta. The seven period
comparisons are computable from resolved daily data today; `BUDGET` and `FORECAST` are declared and
stubbed (no data source). This replaces the single covers-only `PeriodComparison` record.

### 5.3 Provenance

Every `MetricResult` carries `MetricProvenance`: metric ID, definition version, time range, grain,
source domain, data freshness (`max(resolved_at)` over the rows read), missing/unresolved periods,
and calculation version. This is the substrate for future trust/provenance UX.

---

## 6. Package placement & architecture rules

The catalogue lives in the existing `semantic` package (already a leaf, already the home of the
metric records and `*MetricsQuery` interfaces), sub-package `semantic.catalog`:

- **`semantic.catalog`** — `MetricId`, `MetricDefinition`, `MetricQuery`, `TimeGrain`/`Calendar`/
  `Dimension`/`Comparison`, `MetricResult`, `MetricProvenance`, `MetricQueryService`,
  `MetricExecutor`, base + derived executors.
- Base executors depend **only** on the `*MetricsQuery` interfaces (also in `semantic`), never on
  `reconciliation`/`canonical`. Spring injects the existing `Resolved*Query` implementations at
  runtime — the same pattern `reporting` tools already use.
- The existing `*MetricsQuery` interfaces and their `reconciliation` implementations are unchanged
  (except the §3.3 net/gst extension).

**Resulting dependency direction (unchanged invariants):**

```
reconciliation ──implements──▶ semantic.*MetricsQuery
semantic.catalog ──wraps────▶ semantic.*MetricsQuery        (leaf preserved)
reporting / application / conversational ──consume──▶ semantic.catalog (MetricQueryService)
```

ArchUnit rules to update/keep: `semantic` remains a leaf; executors depend only on `*MetricsQuery`;
`reporting`/`conversational` never reach `reconciliation`/`canonical`; `reporting` still depends only
on `semantic` + `auth`.

---

## 7. Consumer migration

| Consumer | Today | After |
|---|---|---|
| `LabourReportingService` | hand-sums covers + gross sales for foh/boh %, hours-per-cover | authorize + `metricQueryService.query(labour.foh_percent, …)` |
| `InventoryReportingService` | hand-sums gross sales for food-cost % | authorize + `query(inventory.food_cost_percent, …)` |
| `DashboardApplicationService` | reads `*MetricsQuery`, builds series inline | `query(sales.gross / product.top_sellers, …)` |
| AI `ReportingTool`s (4) | each hand-builds widget from `*MetricsQuery` | thin `MetricQuery → MetricResult → WidgetSpec` renderers |
| Exports (Smart Exporter) | n/a (future) | same `MetricQueryService` path |
| **Not migrated** | reconciliation admin (rules/overrides UI, per-source listing, drill-in) | unchanged |

`reporting.Metric` (currently only `GROSS_SALES`) is replaced by `MetricId`. `ToolId` stays; tools
become renderers over one or two `MetricQuery`s.

---

## 8. Permissions (modular, no new matrix)

- `MetricDefinition.requiredPermission` is a `ResourceKey` field — the documented gate for a metric.
- **`MetricQueryService` is permission-agnostic** (like the existing semantic queries, which "never
  authorize"). Enforcement stays at the two existing single points — application services and
  `ToolDispatcher` — doing `permissions.require(role, definition.requiredPermission(), READ)`.
- **Now:** each metric maps to an existing domain resource (sales → `reconciliation.sales`,
  reservations → `reservations.metrics`, labour → `labour.cost`/`labour.hours`, inventory →
  `inventory.cost`, product → `reconciliation.sales`), owner-only seeding as today.
- **Later:** finer roles = change the `requiredPermission` value + seed rows; no query-path change.

---

## 9. Testing

For **each** metric, base and derived:

1. formula correctness (exact resolved values; exact ratio/percent),
2. period boundaries (inclusive `from`/`to`; week/month grain bucketing),
3. trading-day semantics (`TRADING` vs `CALENDAR` identical today; boundary deferred),
4. missing data (no row → STRICT null vs SUM-with-flag, per definition),
5. unresolved data (null value / `hasConflict` → surfaced in `missingPeriods`),
6. zero denominators (derived → null, never divide-by-zero),
7. permissions (denied → explicit "not permitted"),
8. comparison calculations (prev-day / prev-week / same-weekday-last-week / SYLY / rolling 4w / 12w).

Plus updated **ArchUnit** rules (§6).

---

## 10. Documentation

`docs/metrics/catalog.md` — the human-readable catalogue, generated alongside `MetricDefinition`
(with a test asserting the two stay in sync). Per metric: ID, name, business definition, formula,
unit, source domain, valid dimensions, allowed grains, required permission, null/missing semantics,
comparison semantics, version. It answers "what exactly does this number mean?"

---

## 11. Deliberately deferred

- `sales.excluding_refunds` — refunds are not ingested.
- `budget` / `forecast` comparisons — no data source.
- Exact trading-day boundary — defaults to calendar-day-in-venue-timezone until a business decision.
- Arbitrary department/labour dimension split — FOH/BOH are distinct metric IDs today; a free
  department dimension is a future extension, not this pass.
- Grafana-style dashboard variables — future extension; the query/panel model accommodates it
  without a rewrite.

---

## 12. Completion criteria → deliverables

| Criterion | Deliverable |
|---|---|
| metric catalogue | `MetricDefinition` registry + `docs/metrics/catalog.md` |
| stable metric IDs | `MetricId` |
| semantic query API | `MetricQueryService` |
| derived metric support | derived `MetricExecutor`s |
| comparison framework | `Comparison` + comparators |
| permission mapping | `requiredPermission` field + doc |
| provenance metadata | `MetricProvenance` on every result |
| consumers migrated | tools + labour/inventory services + dashboard |
| tests added | per-metric matrix + ArchUnit |
| metrics deliberately deferred | §11 |
