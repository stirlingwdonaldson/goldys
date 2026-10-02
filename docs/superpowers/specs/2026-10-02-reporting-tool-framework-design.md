# Goldy's Reporting Tool Framework Design

**Status:** In review
**Date:** 2026-10-02
**Scope:** Backend reporting-tool framework (PRD Requirement 11 scaffolding) plus the
first fixed tool, `get_sales_by_period`. No LLM, no chat UI, no product/labor tools.

## 1. Objective

Build the reusable tool-calling layer that Conversational BI (Req 9) and Smart
Exporter (Req 10) will both sit on, and prove it with one real tool.

The deliverable is a **tool registry + dispatcher** that:

- registers tools by a stable `ToolId` enum;
- validates every tool parameter against an enum/structure schema (never SQL,
  never a free-text field name or filter);
- authorizes the call through `PermissionService` before dispatch;
- reads **resolved views only**;
- emits a `widget-spec.schema.json` **v1** widget spec (never executable code).

There is deliberately no model invocation in this slice — tools are invoked
directly and tested end-to-end, so the boundary is locked before Spring AI is
wired in.

## 2. Sources of Truth and Baseline

- `docs/prd.md` Requirements 9 and 11 (tool-calling framework, Conversational BI).
- `docs/contracts/ai-tool-boundary.md` — the fixed, enum-validated tool boundary.
- `docs/contracts/widget-spec.schema.json` — widget spec v1
  (`stat`/`table`/`line-chart`/`bar-chart`).
- Resolved views: `reconciliation.DailySalesReconciliationService.resolved(LocalDate)`
  → `Optional<DailySalesResolved(tradingDate, resolvedTotal, authoritativeSource)>`.

Baseline finding that scopes the first tool: the resolved layer is thin. Only
**daily gross sales** (`total_sales`) is resolved; product sales has no resolved
view, and `gst`/`net` are not reconciled. So the first tool reads daily gross
sales by date; product/labor tools are deferred until their resolved views exist.

## 3. Scope

### In scope

- `ToolId` enum and the tool registry/dispatcher.
- A parameter model that enforces enum-validated parameters.
- `PermissionService` dispatch on the `reconciliation.sales` resource.
- `get_sales_by_period` — resolved daily gross sales for an explicit date range,
  with unresolved dates surfaced explicitly.
- Widget-spec v1 emission from a tool result.
- Unit + PostgreSQL integration tests covering the whole path.

### Out of scope

- Spring AI / any model invocation (Req 9's LLM layer).
- The "Ask Goldy's" chat UI and widget rendering.
- `get_sales_by_product`, labor, or any tool whose resolved view does not exist yet.
- Freeform SQL, generic query tools, or any write-back.

## 4. Tool Framework

### 4.1 Registry and dispatcher

- `ToolId` — a stable enum; the first value is `GET_SALES_BY_PERIOD`.
- `ReportingTool` — the tool contract:

```java
public interface ReportingTool {
  ToolId id();
  String name();        // LLM-facing
  String description(); // LLM-facing
  ToolResult execute(ToolInput input, UserRole role);
}
```

- `ToolRegistry` — Spring bean mapping `ToolId` → `ReportingTool`.
- `ToolDispatcher` — the single entry point:

```java
ToolResult dispatch(ToolId id, ToolInput input, UserRole role);
```

The dispatcher (1) looks up the tool, (2) lets the tool validate its input, and
(3) requires `reconciliation.sales` READ before executing. Denial is explicit
(`AccessDeniedException`), never a filtered result.

### 4.2 Parameters

Every tool declares its input as a record whose fields are either structured
values (e.g. `LocalDate`) or **enums**. `Metric` is the first enum:

```java
public enum Metric { GROSS_SALES } // maps to canonical total_sales; extensible
```

No tool parameter may accept a raw string that names a field, a SQL fragment, or
a filter expression. This is enforced by construction (typed records + enums),
not by runtime string inspection.

### 4.3 Widget emission

A tool result carries its widget spec plus any explicit notices (e.g. unresolved
dates) — a tool must never silently drop data, so anything it couldn't resolve
becomes a notice the caller must surface:

```java
public record ToolResult(WidgetSpec widget, List<String> notices) {}
```

`WidgetSpec` is a version-1 record matching `widget-spec.schema.json`:

```java
public record WidgetSpec(int version, String type, String title, String description, List<Map<String, Object>> data) {}
```

`type` is one of `stat`, `table`, `line-chart`, `bar-chart`. The framework owns
this mapping so every future tool reuses it and none can emit executable code.

## 5. `get_sales_by_period`

- **Name/description (LLM-facing):** "Resolved daily sales totals for a date
  range." / "Gross sales, resolved across sources, per trading day."
- **Input:** `GetSalesByPeriodInput(LocalDate startDate, LocalDate endDate, Metric metric)`.
  Validation: `startDate <= endDate`, and both present; `metric` is an enum.
- **Behavior:** for each date in `[startDate, endDate]`, read
  `DailySalesReconciliationService.resolved(date)`:
  - resolved → emit `{ date, grossSales, source }` where `source` is
    `"agreed"`, `"override:<src>"`, or `"rule:<src>"`;
  - unresolved (`resolvedTotal == null`) → collect the date, **do not silently
    drop it**.
- **Widget:** `line-chart`, title "Daily sales", data = the resolved points.
- **Unresolved dates:** collected into `ToolResult.notices` (e.g.
  `"3 dates have no resolved total (unresolved conflict)"`), never presented as
  resolved values.

## 6. API Surface

No new REST endpoint in this slice. The dispatcher is a Spring bean consumed by
tests now and by the future LLM layer (Req 9). This keeps the tool boundary
internal until Spring AI is wired in.

## 7. Testing Strategy

- **Unit:** dispatcher routing, unknown-tool handling, enum validation, and the
  `get_sales_by_period` date-range/ordering/unresolved logic (mocked resolved
  view).
- **Integration (PostgreSQL):** seed canonical daily sales + an override, then
  invoke `get_sales_by_period` through the dispatcher and assert the resolved
  widget data and the explicit unresolved list.
- **Permission:** a denied role receives `AccessDeniedException`, never a
  partial result.
- **Widget:** every result conforms to `widget-spec.schema.json` v1 (assert the
  serialized JSON validates).

## 8. Boundaries

- Tools read resolved views only — never raw or canonical tables directly.
- No freeform query, no SQL, no free-text field/filter parameters.
- No model invocation; no write-back.
- Keep the `reconciliation.sales` resource; no new permission axes.
