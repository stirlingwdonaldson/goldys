---
name: add-metric
description: Add or change a metric in Goldy's semantic metric catalogue (MetricId, MetricDefinition, executor, docs). Use when asked for a new KPI, ratio, or figure on a dashboard or in Ask Goldy's, or to change how an existing metric is computed.
---

Every named number has exactly one definition, in
`backend/src/main/java/com/goldys/platform/semantic/catalog/`. Dashboards, REST and Ask
Goldy's all read it through `MetricQueryService`; never compute a catalogue metric
anywhere else.

## Steps

1. **Check it doesn't exist.** Search `MetricId` and `docs/metrics/catalog.md`. If a
   metric with that meaning exists, reuse it; two metrics must never compute the same
   thing differently.
2. **Add a `MetricId`** with a stable dotted value (`<domain>.<snake_case>`). Never a
   table or class name. The value is persisted in saved dashboards, so never rename
   one; add a new id instead.
3. **Register a `MetricDefinition` in `MetricCatalog`** using the `base(...)` or
   `derived(...)` helper. Set:
   - grains (`DAY_WEEK_MONTH` unless only a point-in-time value makes sense, then
     `DAY_ONLY`);
   - valid `Dimension`s (`SERVICE_PERIOD`, `DEPARTMENT`, `PRODUCT`), only those the
     source data actually carries;
   - `requiredPermission`: an existing resource where the sensitivity matches;
     sensitive data (e.g. wages) gets its own resource, seeded `ALL × OWNER` only;
   - persistent caveats in notes (e.g. "gross includes GST").
   For a derived metric, add its operands to `CONSTITUENTS`, and update `RELATED` if
   it should be suggested alongside others.
4. **Execute it.**
   - Base metric: add it to the domain's executor (`SalesMetricExecutor`,
     `ReservationMetricExecutor`, `LabourMetricExecutor`, `InventoryMetricExecutor`,
     `ProductMetricExecutor`) and its `ids()`. It reads a resolved projection through
     the domain's semantic query, never canonical.
   - Derived metric: add a case to `DerivedMetricExecutor`. Return null (shown as
     missing, never 0) when the denominator is zero or an operand is unresolved.
5. **Document it** in `docs/metrics/catalog.md` (table row plus meaning).
   `MetricCatalogDocTest` fails if any `MetricId` is missing from that file.

## Tests

Add cases to the executor's test (`*MetricExecutorTest`, `DerivedMetricExecutorTest`),
including missing data and zero denominators, and run `MetricCatalogTest` and
`MetricCatalogDocTest`.
