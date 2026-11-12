# AI Tool Boundary Contract

The rules every Ask Goldy's (Conversational BI) tool must follow. The invariant behind them is in
`docs/system-context.md` ("Restricted, tool-mediated AI access"); this file states the contract
the code is held to.

## Rules

- Tools are registered by a stable enum identifier (`reporting/ToolId`). The model can only call
  tools in that enum.
- Every dimension, metric, grain, and filter a tool accepts is an enum or a bounded record
  (`MetricId`, `TimeGrain`, `Dimension`, `TimeRange`, …). No parameter accepts SQL, a free-text
  field name, or a free-text filter expression.
- `ToolDispatcher` authorizes before dispatch: `PermissionService.require(role, tool.resource(),
  READ)`, and additionally `require(role, metric.requiredPermission, READ)` for every metric a
  catalogue tool touches. A denial is returned to the model as an explicit "not permitted"
  result, never a filtered answer.
- Tools read through the `semantic` layer (resolved projections) only. `reporting` may depend on
  `semantic` and `auth` and nothing else; `ArchitectureBoundariesTest` enforces this.
- Tools return data plus widget specs conforming to widget schema **version 2**
  (`docs/contracts/widget-spec.schema.json`). They never emit executable UI code, JSX, or SQL.
- `BoundedToolCallingManager` caps a single answer at `MAX_TOOL_CALLS` (8) tool rounds.

## Current tool set

| `ToolId` | Purpose |
|---|---|
| `GET_SALES_BY_PERIOD` | Resolved daily sales over a range |
| `GET_TOP_PRODUCTS` | Ranked products by resolved amount |
| `GET_RESERVATION_SUMMARY` | Bookings, covers, no-shows |
| `GET_LABOUR_VARIANCE` | Scheduled vs actual hours (cost is permission-gated) |
| `GET_INVENTORY_SUMMARY` | Purchases, wastage, stock |
| `GET_METRIC` | Any catalogue metric (`docs/metrics/catalog.md`) at a grain |
| `DESCRIBE_METRICS` | Approved definitions, units, versions, dimensions and grains; discovery excludes inaccessible operands, explicit selections are denied atomically |
| `RUN_METRIC_ANALYSIS` | Compose 1–6 explicit catalogue queries; authorize every operand and validate the complete plan before reading; preserve each bucket and scope |
| `COMPARE_METRIC_PERIODS` | A catalogue metric across two periods |
| `RANK_DIMENSION` | A catalogue metric ranked by an allowed dimension |
| `GET_RECONCILIATION_STATUS` | Open conflicts and resolution state |
| `CREATE_DASHBOARD_DRAFT` | Proposes a dashboard document for the user to save |
| `GET_RECIPE_COSTING` | Qualified recipe cost summary |
| `GET_SALES_BY_CATEGORY` | Sales by reviewed category, with coverage qualifications |
| `GET_RECIPE_INGREDIENTS` | One recipe's ingredient lines (qty/unit, cost, supplier, sub-recipe) |

Adding a tool means adding a `ToolId` value, a `ReportingTool` implementation with a bounded
`ToolInput` record, and a case in the conversational eval fixtures
(`backend/src/test/resources/conversational-eval/`).

Definition-only tools declare `referencedMetrics`; the dispatcher authorizes their transitive
operands as well as all `toMetricQueries` operands. Empty discovery can list accessible definitions;
explicit requests never silently omit denied metrics. A definition does not prove data availability.

The first governed-analysis slice composes existing metric queries, not relational plans. It accepts
no filters, formulas, field names or joins. Results preserve scope, nulls, statuses and provenance;
they do not sum daily ratios or balances. Top-seller lists use `GET_METRIC`. Broader ontology-assisted
planning is the accepted direction in [the decision](../decisions/0001-governed-conversational-planning.md),
but the relational registry remains disconnected until its semantic contracts are aligned.

## Clear requests and numeric presentation

The assistant supplies the current venue date from the configured venue clock on every turn.
Explicit dates remain authoritative. The prompt routes clear metric/period requests directly to
execution and reserves clarification for genuinely missing business choices. Category gross sales
must not be substituted with the reviewed ex-GST food/beverage metrics.

`RUN_METRIC_ANALYSIS` retains raw `value` and adds `displayValue`: percentage ratios are formatted
as percentages to two decimal places in the tool, and missing values remain null. The model need
not compute or round a percentage. `reservations.no_show_share_all_bookings` implements an explicit
all-bookings denominator separately from the operational expected-bookings no-show rate.
Prompt instructions guide model behaviour; they are not a deterministic guarantee of correctness.

The conversational model is offered the general metric tools instead of the redundant daily-sales,
top-products, reservation-summary, labour-variance and inventory-summary shortcuts. Those tools
remain registered for compatibility. This reduces ambiguous routes to the same requested figure;
permissions still run in `ToolDispatcher` for every call. Product metric queries with `PRODUCT`
now retain a separate series per normalized product name, with per-product missing-period notices.

Metric-analysis rows include the authoritative definition alongside the value. Attendance party
counts/proxies and source-qualified product amounts carry their meaning in result notes, so final
answers need not rely on recalling definitions from the initial prompt.

The assistant buffers generated text until completion. If the model makes no tool invocation and
only promises a lookup or asks permission to perform that read, it receives one recovery attempt; a second unexecuted promise becomes an
explicit failure. This English-language heuristic does not retry business/date clarification questions or any
attempt that reached a tool (including denied calls), and preserves the eight tool-round cap.
Text deltas therefore arrive after completion rather than progressively while the model works.

If every queried metric has explicit coverage with no observed dates and missing/unresolved dates,
no returned metric widget contains a numeric value, and no other data result lacks coverage metadata,
the application replaces model prose with a missing-data statement. It cannot publish the model's
claim that such evidence is a known zero. Observed zero, mixed observed/missing evidence, observed category/recipe results without metric provenance, definitions
without query evidence and denied tools remain distinct. This is a narrow invariant, not general
validation of all generated claims.

Category-sale results now state their inclusive date scope and source tax-basis qualification.
They are explicitly distinguished from reviewed ex-GST `sales.food`/`sales.bev` metrics, whose
missing coverage does not invalidate the separate observed category result.

Reaching the tool-round cap triggers one final synthesis using the accumulated evidence with all
tools disabled. The cap's internal “summarize” instruction is no longer returned as if it were the
answer. No extra queries are permitted; failure or another unexecuted promise yields an explicit
incomplete-analysis message. This does not authorize arithmetic or unsupported claims in prose.

Argument deserialization/record-construction failures return a sanitized `ok: false` tool result
for correction within the same round budget; invalid arguments still never reach the dispatcher.
Operational failures are not mislabeled as argument errors. Ranked product results label monetary
amounts and quantities with their own units so the model can distinguish their meanings.
