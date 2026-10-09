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
| `COMPARE_METRIC_PERIODS` | A catalogue metric across two periods |
| `RANK_DIMENSION` | A catalogue metric ranked by an allowed dimension |
| `GET_RECONCILIATION_STATUS` | Open conflicts and resolution state |
| `CREATE_DASHBOARD_DRAFT` | Proposes a dashboard document for the user to save |

Adding a tool means adding a `ToolId` value, a `ReportingTool` implementation with a bounded
`ToolInput` record, and a case in the conversational eval fixtures
(`backend/src/test/resources/conversational-eval/`).
