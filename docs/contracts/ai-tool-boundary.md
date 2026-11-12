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
