# Goldy's "Ask Goldy's" Analytical Expansion Design

**Status:** In review
**Date:** 2026-10-07
**Scope:** Expand "Ask Goldy's" from single-shot metric retrieval into multi-step analytical
reasoning over the semantic layer, while preserving the fixed-tool safety boundary. Adds a bounded
tool catalogue over composable analytical primitives, server-side conversation persistence with
bounded context, an inspectable tool trace, dashboard draft create + update, tool/metric-specific
permission declarations, and a two-tier evaluation suite.

This is **sub-project A** of a two-part effort. Sub-project B ("PASS 9 — Provenance, Trust, and
Data-Quality UX") is specified separately and builds on the provenance seam defined here (§7).

## 1. Objective

Make Ask Goldy's behave as an analytics interface over Goldy's trusted semantic layer, not a
general-purpose database agent. It must answer multi-step questions — e.g. *"Why were Saturday
sales down?"*, *"Compare this week with the previous four-week average"*, *"Did lower covers or
lower spend per cover explain the drop?"* — by chaining bounded tool calls over the metric
catalogue, while:

- never gaining SQL or arbitrary-expression access;
- never silently presenting missing/unresolved data as a number;
- distinguishing observed fact from calculated relationship from correlation from plausible
  explanation from causal claim;
- persisting conversations server-side without trusting client-supplied history;
- surfacing an inspectable trace of what it queried, when, and with what freshness.

## 2. Sources of Truth and Baseline

- `docs/system-context.md` — "Restricted, tool-mediated AI access" and "JSON-driven UI generation"
  invariants; the three-layer data model and read-direction invariant.
- `docs/contracts/ai-tool-boundary.md` — fixed, enum-validated tool boundary.
- `docs/architecture/current-state.md` — package dependency table and `ArchitectureBoundariesTest`
  rules; the semantic query interfaces and authorization model.
- `docs/metrics/catalog.md` — the 30-metric catalogue, display+note semantics, provenance fields.
- `docs/superpowers/specs/2026-10-02-conversational-bi-design.md` — the current Ask Goldy's slice,
  its `AssistantService` port, and the deferred persistence/tool-trace items (§8/§11).
- `docs/superpowers/specs/2026-10-02-reporting-tool-framework-design.md` — `ToolId` / `ReportingTool`
  / `ToolDispatcher` / `ToolResult` contract.
- `docs/superpowers/specs/2026-10-07-dashboards-design.md` — `DashboardDocument` schema, validation,
  and the confirm-to-save draft path.

**Baseline (merged, in `origin/main`):**

- `reporting` — `ToolId` (5 values), `ReportingTool`, `ToolRegistry`, `ToolDispatcher`, `ToolInput`,
  `ToolResult(WidgetSpec, List<String>)`, `WidgetRenderer`, and four data tools +
  `CreateDashboardDraftTool` (in `conversational`).
- `semantic.catalog` — `MetricCatalog` (30 `MetricDefinition`s), `MetricQueryService` →
  `MetricQueryServiceImpl`, `MetricQuery`, `MetricResult` (sealed: `TimeSeriesResult` |
  `RankedListResult`), `MetricProvenance(metric, definitionVersion, range, grain, sourceDomain,
  dataFreshness, missingPeriods, calculationVersion)`, `ComparisonService`, `TimeRange`, `TimeGrain`,
  `Dimension`, `Comparison`.
- `semantic` query interfaces — `SalesMetricsQuery`, `ProductMetricsQuery`, `ReservationMetricsQuery`,
  `LabourMetricsQuery`, `InventoryMetricsQuery` (each implemented over the resolved projections).
- `conversational` — `ChatController` (SSE, Owner-gated via `conversational.chat`),
  `AssistantService` port, `ChatClientAssistantService`, `ReportingToolCallbacks`,
  `ConversationContext`, `AnswerPayload(widgets, trace, asOf, notices, draft)`,
  `ConversationEvent` (TextDelta/Answer/Error). Stateless: client sends full `messages` list.
- `auth` — `PermissionService.require(role, ResourceKey, PermissionAction)`, table-driven;
  resources today: `reconciliation.sales`, `reservations.metrics`, `labour.hours`, `labour.cost`,
  `inventory.cost`, `conversational.chat`, `dashboards`, `connectors`.
- Schema owned by Flyway through `V25`.

## 3. Scope

### In scope

- Four **general analytical tools** (`get_metric`, `compare_metric_periods`, `rank_dimension`,
  `get_reconciliation_status`) and five **domain tools** (`get_sales_by_period` kept,
  `get_top_products`, `get_reservation_summary` kept, `get_labor_variance`, `get_inventory_summary`).
- A small **analytics-primitives** layer in `semantic` (compare, rank, contribution, variance,
  related-metrics) that all tools compose.
- A **richer tool-result envelope** carrying structured `MetricProvenance` (the PASS 9 seam).
- **Server-side conversation persistence** with a full history browser, plus summarization/compaction
  and a hard bound on tool-call rounds per turn.
- **Tool/metric-specific permissions** authorized before execution, seeded OWNER-only.
- **Dashboard draft create + update** through the existing validated confirm-to-save path.
- A **two-tier evaluation suite** (§14).
- Rewritten system prompt enforcing the evidence hierarchy and explicit failure semantics.

### Out of scope

- Any SQL tool, free-text field/filter expression, or generic "run this view" tool.
- `get_staff_cost_detail` / a `labor.sensitive` permission (no per-staff cost data source today).
- The department × seniority field-to-role matrix (deferred — see §17).
- PASS 9 trust-state model, freshness rules, and reconciliation/audit UX (separate spec).
- Budget/forecast comparisons (`Comparison.BUDGET`/`FORECAST` remain unimplemented).
- RAG / vector retrieval.

## 4. Architecture and Dependency Direction

No new deployables or databases. The modular monolith and `ArchitectureBoundariesTest` rules remain,
with one deliberate rule narrowing:

- **`semantic`** stays a leaf. It gains the analytics primitives (§5) and one new query interface,
  `ConnectorHealthQuery` (§8), both pure reads. It does not gain dependencies.
- **`reporting`** gains the new tools as thin adapters over `semantic`. Its dependency stays
  `semantic` + `auth` only — no `canonical`/`reconciliation`/`ingestion`.
- **`conversational`** gains `ConversationService` (persistence + summarization + bounded rounds) and
  its own JPA repositories for the conversation store. The rule "conversational never reaches
  persistence (canonical/reconciliation/ingestion)" is **narrowed** to "conversational never reaches
  `canonical`/`reconciliation`/`ingestion`" — it may own a *conversation* store, but still may not
  read business data directly. The existing rule "conversational never reaches the dashboard
  repositories" is unchanged.
- **`application`** implements the new `ConnectorHealthQuery` (a `semantic` interface), reusing the
  existing `ConnectorApplicationService` connector-status aggregation (which already exposes
  `lastRunAt`). `reporting` depends only on the `semantic` interface. No cycle is introduced
  (`semantic` remains a leaf).
- Flyway migrations `V26`+ for the conversation store and permission seeds.

**Evolvability constraints (explicit, approved):** the three seams that make future changes local
must be preserved throughout implementation:

1. `AssistantService` port + discovered tools (`List<ReportingTool>`) + externalized prompt — so the
   AI slice can be rewritten without touching tools, controller, or persistence.
2. `ToolDispatcher` as the single authorize+validate choke point — so relaxing the tool boundary
   (e.g. a future read-only SQL tool) is one contained tool addition plus a deliberate invariant
   change, never a rewrite.
3. `MetricQueryService` + `MetricCatalog` + `MetricExecutor` as the single definition boundary — so a
   future dynamic catalogue swaps *behind* that boundary, not through tools or the frontend.

`ConversationService` must be **orchestration-agnostic**: it operates on message history and tool
trace, never on Spring AI internals, so it survives a swap of the orchestration strategy.

## 5. Analytical Primitives (`semantic`)

Composable, permission-agnostic reads over `MetricQueryService` and `MetricCatalog`. Each is a plain
service in `semantic.catalog` (or a new `semantic.analytics` sub-package); none authorizes.

| Primitive | Shape | Notes |
|---|---|---|
| retrieve series | `MetricQueryService.query(MetricQuery)` | exists |
| compare periods | `ComparisonService` + `deltaPercent` | exists; exposed by `compare_metric_periods` |
| rank groups | `RankingService.rank(MetricId, Dimension, TimeRange, limit) → RankedListResult` | supports the pairs with a grouped read (`PRODUCT`, `DEPARTMENT`); generalizes `ProductMetricsQuery.topSellers` |
| contribution | (deferred) | no consuming tool this pass |
| largest variance | (deferred) | no consuming tool this pass |
| related metrics | `MetricCatalog.related(MetricId) → Set<MetricId>` | curated catalogue map, not parsed from formulas |

The catalogue declares explicit metric relatedness — a curated map exposed as
`MetricCatalog.related(id) → Set<MetricId>`, not parsed from formulas. This is the single source for
"what else explains this metric", surfaced to the model via `get_metric` results (§7) rather than a
separate tool.

The `contribution` and `largest variance` primitives are deferred this pass — no tool in §6 consumes
them yet (YAGNI); they remain planned primitives for a future question type.

## 6. Tool Catalogue

Ten tools. The four general tools are the composable core; the domain tools are thin wrappers, not
one tool per English question.

| Tool | `ToolId` | Input (record) | Backs onto | Capability resource |
|---|---|---|---|---|
| `get_metric` | `GET_METRIC` | `GetMetricInput(MetricId, startDate, endDate, TimeGrain, Set<Dimension>)` | `MetricQueryService` + `MetricCatalog.related` | `conversational.chat` (per-metric data gate) |
| `compare_metric_periods` | `COMPARE_METRIC_PERIODS` | `CompareMetricPeriodsInput(MetricId, startDate, endDate, Comparison, TimeGrain)` | `ComparisonService` + `MetricQueryService` | `conversational.chat` (per-metric data gate) |
| `rank_dimension` | `RANK_DIMENSION` | `RankDimensionInput(MetricId, Dimension, startDate, endDate, limit)` | `RankingService` | `conversational.chat` (per-metric data gate) |
| `get_sales_by_period` | `GET_SALES_BY_PERIOD` (kept) | existing `GetSalesByPeriodInput` | `MetricQueryService` | `reconciliation.sales` |
| `get_top_products` | `GET_TOP_PRODUCTS` | `GetTopProductsInput(startDate, endDate, limit)` | `RankingService` / `ProductMetricsQuery.topSellers` | `reconciliation.sales` |
| `get_reservation_summary` | `GET_RESERVATION_SUMMARY` (kept) | existing | `MetricQueryService` | `reservations.metrics` |
| `get_labor_variance` | `GET_LABOR_VARIANCE` | `GetLaborVarianceInput(startDate, endDate)` | `LabourMetricsQuery` + derived (`labour.hours_variance`, `labour.foh_percent`, `labour.boh_percent`) | `labour.hours` + `labour.cost` |
| `get_inventory_summary` | `GET_INVENTORY_SUMMARY` | `GetInventorySummaryInput(startDate, endDate)` | `InventoryMetricsQuery` + `inventory.food_cost_percent` | `inventory.cost` |
| `get_reconciliation_status` | `GET_RECONCILIATION_STATUS` | `GetReconciliationStatusInput(startDate, endDate)` | unresolved periods via `MetricProvenance.missingPeriods` + `openConflicts()` where exposed + `ConnectorHealthQuery` | `reconciliation.status` |
| `create_dashboard_draft` | `CREATE_DASHBOARD_DRAFT` (kept, extended) | `CreateDashboardDraftInput(..., dashboardId?)` | `DashboardWidgetValidator` | `dashboards` |

- **`get_metric`** is the general single-metric tool: it queries any catalogue metric and renders the
  appropriate widget (time-series for series metrics, stat/table for scalar derived metrics,
  ranked-list for `product.top_sellers`). Its result carries the metric's `relatedMetrics` as a
  hint so the model can discover the next fetch in a multi-step chain.
- **`compare_metric_periods`** computes current vs. reference (via `ComparisonService.referenceRange`)
  and a delta percentage, rendered as a comparison widget; `Comparison.BUDGET`/`FORECAST` return an
  explicit "no data source" error, not a value.
- **`rank_dimension`** ranks the groups of a dimension by the metric (e.g. product by sales amount,
  department by labour cost). `get_top_products` is its product-sales convenience wrapper.
- **`get_reconciliation_status`** reports, over the range: unresolved periods per domain (derived
  from each domain's representative metric's `missingPeriods`), open-conflict counts where the query
  interfaces expose them, and per-source last-successful-ingestion time via `ConnectorHealthQuery`.
  It is the only tool whose result is "about the data" rather than "the data itself".

All inputs are typed records with enum fields (`MetricId`, `Dimension`, `TimeGrain`, `Comparison`)
and `LocalDate` ranges — no free-text field, filter, or expression. Date ranges are validated
(`startDate <= endDate`, both present); a range spanning no data returns an empty result plus a
notice, never a fabricated figure.

`get_labour_variance` and `get_inventory_summary` rename and broaden the existing `get_labour_cost`
and `get_food_cost` tools (added in the dashboards pass) rather than duplicating them; the renamed
tools keep the codebase's `labour` spelling.

## 7. Result Envelope and Provenance Seam

`ToolResult` grows from `(WidgetSpec, List<String> notices)` to:

```java
public record ToolResult(
    WidgetSpec widget,
    List<String> notices,
    List<MetricProvenance> provenance) {}
```

`provenance` is the list of `MetricProvenance` for the metric queries the tool ran (empty for
`create_dashboard_draft` and `get_reconciliation_status`). `ReportingToolCallbacks` serializes this
into the model-visible outcome as `{ ok, widget, notices, provenance: [...] }`, so the model receives
structured `metric`, `range`, `grain`, `sourceDomain`, `dataFreshness`, and `missingPeriods` without
touching any ingestion table.

`AnswerPayload.TraceEntry` becomes:

```java
record TraceEntry(String tool, String description, List<MetricProvenance> provenance) {}
```

This is the exact seam PASS 9 extends: it will derive trust states (verified / resolved-by-rule /
stale / …) from `dataFreshness`, `missingPeriods`, and connector health without changing the tool
contract shape again.

## 8. Permissions

Mechanism only; OWNER-only seeds; the department × seniority matrix stays deferred.

**Capability gate (unchanged).** `ToolDispatcher` requires `tool.resource()` READ for every call.
The general tools (`get_metric`, `compare_metric_periods`, `rank_dimension`) use
`conversational.chat` as their capability resource — their data authorization is purely per-metric,
below.

**Per-metric data gate (new).** For tools that return metric data, the dispatcher derives the metric
queries via `ReportingTool.toMetricQueries(input)` and, *before* execution, requires
`catalog.definition(metric).requiredPermission()` READ for each distinct metric. `toMetricQueries`
returns an empty list for non-data tools (`create_dashboard_draft`, `get_reconciliation_status`), so
their data gate is empty and they rely on their capability resource alone. This realizes
"tool/metric-specific resource declarations" without changing the `ResourceKey` + `PermissionAction`
model — the PRD's `sales.metrics.read` notation maps to resource `reconciliation.sales` (etc.) +
action `READ`. A tool touching two domains (e.g. `get_labor_variance` → `labour.hours` +
`labour.cost`) is denied unless the role holds *all* required permissions.

`create_dashboard_draft` (create and update) is gated on `dashboards` READ only at draft time; the
per-metric authorization for a *saved* dashboard already happens at save/render time and is
unchanged.

**Derived-metric permission union (deferred, noted).** A cross-domain derived metric (e.g.
`inventory.food_cost_percent` = purchases ÷ sales.gross, or `sales.average_spend_per_cover` = gross ÷
covers) is assigned a single `requiredPermission`, but its computation reads more than one domain's
base metrics. Today this is moot — the only role that can use Ask Goldy's is OWNER, which holds every
resource. When the department × seniority matrix lands, per-metric authorization for derived metrics
must require the union of their constituent base metrics' permissions; the `MetricCatalog.related(id)`
map added in §5 is the hook for that (recorded in §18).

**`ConnectorHealthQuery` seam.** A new `semantic` interface exposing per-source last-successful
ingestion, implemented by `application` (reusing `ConnectorApplicationService`). This keeps
`reporting` off `application`/`ingestion` while letting `get_reconciliation_status` report connector
freshness.

**New seeds (migration, OWNER-only, matching V6/V13/V23 convention):**

- `reconciliation.status` — READ for `get_reconciliation_status`.
- `conversational.threads` — READ + WRITE for the history browser (list/read/rename/delete).

No new `labor.sensitive` resource in this pass.

## 9. Conversation Persistence and Context Management

**Schema (`V26__conversation_threads.sql`):**

- `conversation_thread` — `id` UUID PK, `user_account_id` UUID FK, `title` text, `summary` text
  (compacted history, nullable), `created_at`, `updated_at`, `last_message_at`.
- `conversation_message` — `id` UUID PK, `thread_id` UUID FK, `role` text (`user` | `assistant`),
  `content` text, `tool_trace` jsonb (the `AnswerPayload.trace` for assistant turns, nullable),
  `created_at`.

**Server-authoritative contract.** The chat request becomes `{ threadId?: UUID, message: string }`.
The server loads its own thread history, appends the new user message, and **never** accepts a
client-supplied assistant message — client history is untrusted and cannot become tool results. The
SSE events (`text`, `answer`, `error`) are unchanged, but `answer.trace` is the richer §7 shape. The
assistant message and its `tool_trace` are persisted on stream completion.

**Context management.** Each turn is bounded:

- **Summarization/compaction.** When a thread exceeds a token/turn threshold, `ConversationService`
  compacts older turns into a stored `summary` (a temperature-0 summarization call to the same
  model), and the prompt context becomes `summary` + the most recent K turns verbatim. Messages are
  never deleted from the store — only elided from the model's context.
- **Bounded rounds.** Tool-call iterations per turn are capped (default 8) via a bounded-round
  `ToolCallingManager`/advisor. On exhaustion, tool calls stop and the model summarizes with the data
  it already has, marking the answer accordingly.

**History browser API (gated on `conversational.threads`):**

- `GET /api/conversational/threads` — list own threads (id, title, updated, last message preview).
- `GET /api/conversational/threads/{id}` — messages + `tool_trace`.
- `PATCH /api/conversational/threads/{id}` — rename (WRITE).
- `DELETE /api/conversational/threads/{id}` — delete (WRITE).

**Frontend.** `use-ask-goldys` is rewritten to hold a `threadId`, send `{threadId, message}` only,
and reconcile the assistant turn from the streamed `answer` (not from a local echo). A new
"Conversations" screen lists/reopens/renames/deletes threads.

## 10. Tool Trace and Provenance UI

The "How I got this" affordance expands from `{tool, description}` to include, per tool call:
metrics queried, time ranges, data freshness (`dataFreshness`), and unresolved periods
(`missingPeriods`). The frontend renders this as an expandable, per-tool block. No chain-of-thought,
no internal prompts, no raw ingestion references are ever emitted.

## 11. Multi-step Reasoning and Analytical Caution

Multi-step reasoning uses the existing Spring AI tool loop (already multi-call per turn), now bounded
by §9. The rewritten `prompts/ask-goldys-system.txt` enforces:

- **Evidence hierarchy.** The model must distinguish *observed fact* (a tool-returned number),
  *calculated relationship* (e.g. a delta), *correlation* (two series move together), *plausible
  explanation*, and *causal claim*. It must not present correlation as causation, and must use
  hedging language for non-causal explanations ("accounts for much of the decline", "is consistent
  with") rather than asserting "fell because".
- **Only tool-returned numbers.** Never invent, estimate, or round a figure; cite what tools
  returned.
- **Explicit failure relay.** Relay denials, missing periods, stale connectors, unresolved conflicts,
  invalid ranges, unavailable domains, and tool failures verbatim — never a silent guess or partial
  answer presented as complete.
- **Ambiguity.** Ask one short clarifying question when the period/metric is missing.
- **Bounded tool use.** Chain tools only as the question requires; stop and summarize at the round
  cap.

## 12. Failure Semantics

The assistant must handle, explicitly, and the tool layer must surface (via `ok:false` + `error` or
via `notices`/`provenance.missingPeriods`):

- **missing data** — `missingPeriods` → "N days unresolved / no resolved data".
- **stale connectors** — `get_reconciliation_status` last-successful-ingestion older than the query
  range → the model notes the data may be incomplete.
- **unresolved conflicts** — unresolved periods/`openConflicts` → surfaced, never zeroed.
- **permission denial** — `AccessDeniedException` → explicit "you don't have access to that data".
- **invalid date range / bad enum** — `IllegalArgumentException` → structured error, model corrects or
  declines.
- **unavailable domain** — no executor/metric for the request → explicit "no data source for X".
- **tool failure** — a generic runtime failure returns a bounded error to the model, never a stack
  trace.

## 13. Dashboard Composition

`CreateDashboardDraftInput` gains an optional `dashboardId`. When present, the tool produces an
**update draft** (title/description/filters/widgets for an existing dashboard); when absent, a
**create draft** as today. Both run through `DashboardWidgetValidator` and are carried on
`AnswerPayload.draft` (never persisted); the frontend confirm path routes create → `POST
/api/dashboards` and update → `PUT /api/dashboards/{id}`. Generated dashboards are validated exactly
like manually created ones — there is no separate validation path.

## 14. Evaluation Suite

Two tiers, as agreed.

**Tier 1 — deterministic core (no live model).** A `ConversationalEvalHarness` under `src/test`
defines scenarios as data — question, expected tool-call sequence, seeded resolved data, expected
numeric outcome, expected permission outcome, expected schema validity — and replays them through the
real `ToolDispatcher`/`MetricQueryService`/`PermissionService` against Testcontainers Postgres via a
`ScriptedAgent` that issues the golden tool calls. It asserts:

- **numeric correctness** — the returned figures match the seeded resolved data;
- **tool selection** — the golden sequence is the one the dispatcher actually executes and the result
  shapes match;
- **permission correctness** — a denied role receives an explicit denial, never a partial result;
- **schema validity** — every widget spec validates against `widget-spec.schema.json`;
- **failure semantics** — missing data, invalid range, unavailable domain, tool failure each produce
  the documented outcome.

**Tier 2 — live-LLM (gated).** A `@Tag("llm")` JUnit suite, disabled unless a model is configured,
runs the real model against the ten-category question set and scores:

- **tool selection** (did the model pick the right tool(s) for the question);
- **unsupported-claim rate** (does any answer assert a number the trace did not produce);
- **causal-language detection** (heuristic scan for unhedged causal phrasing on correlational data).

The ten-category question set (per PRD): simple retrieval, period comparison, cross-domain reasoning,
ambiguous-request clarification, missing data, permission denial, unresolved conflict, dashboard
generation, prompt injection, and arbitrary SQL/database-access attempt. The last two assert the
boundary holds: no SQL tool exists and injection is treated as a question, not an instruction.

Scoring dimensions reported across both tiers: numeric correctness, tool selection, permission
correctness, unsupported-claim rate, schema validity.

## 15. API Surface

- `POST /api/conversational/chat` — request body changes to `{threadId?, message}`; SSE events
  unchanged (`text`/`answer`/`error`), `answer.trace` richer.
- `GET /api/conversational/threads`, `GET /api/conversational/threads/{id}`,
  `PATCH /api/conversational/threads/{id}`, `DELETE /api/conversational/threads/{id}` — new.
- No other public endpoint changes; tools remain server-side only.

## 16. Testing Strategy

- **Unit:** each primitive (`RankingService`, `ContributionService`, `VarianceService`,
  `RelatedMetricsService`); each tool's input validation + metric-query derivation + widget mapping;
  `ToolDispatcher` per-metric authorization (all-required vs. partial denial); `ConversationService`
  summarization/compaction and round-cap; the scripted eval harness itself.
- **Integration (Postgres/Testcontainers):** full loop — seed resolved data, run a scripted multi-step
  turn through the real dispatcher, assert the `answer` payload (widgets, trace, notices, provenance)
  and persistence (thread + messages + `tool_trace` rows).
- **Auth:** non-Owner → 403 on `conversational.chat` and on `conversational.threads`; a role missing
  one required metric permission is denied for a cross-domain tool.
- **Architecture:** `ArchitectureBoundariesTest` updated for the narrowed `conversational` rule and
  the new `ConnectorHealthQuery` edge.
- **Frontend (Vitest):** `use-ask-goldys` `{threadId, message}` contract; history browser; richer
  trace rendering; dashboard draft update flow.
- **Eval:** Tier 1 runs in CI deterministically; Tier 2 is opt-in.

## 17. Decisions Recorded

- **Orchestration:** extend the existing Spring AI `ChatClient` loop (Approach A); no explicit
  planner/executor harness.
- **Tools:** ten tools — four general (`get_metric`, `compare_metric_periods`, `rank_dimension`,
  `get_reconciliation_status`) + five domain + `create_dashboard_draft` (create/update), all thin
  adapters over `semantic` primitives. No SQL tool, no free-text surface.
- **Permissions:** mechanism (per-metric authorization in the dispatcher) + OWNER-only seeds; the
  department × seniority matrix is deferred (it remains the open stakeholder mapping noted throughout
  the repo docs).
- **Persistence:** server-authoritative `{threadId, message}`; client history untrusted; full history
  browser; lossy summarization + bounded rounds.
- **Eval:** two-tier — deterministic scripted-agent core + opt-in live-LLM.
- **Provenance seam:** `ToolResult`/`TraceEntry` carry `List<MetricProvenance>`; PASS 9 extends this
  into trust states without a further contract change.
- **Spring AI 1.1.x** remains the pinned line (per the conversational-bi design §8/§9).

## 18. Known Limitations / Deferred

- No `get_staff_cost_detail` or `labor.sensitive` permission (no per-staff cost data).
- `Comparison.BUDGET`/`FORECAST` unimplemented (no data source).
- Reservations/labour/inventory open-conflict counts are surfaced via `missingPeriods` rather than a
  dedicated per-domain `openConflicts()` query; dedicated counts land with PASS 9's reconciliation UX.
- Derived-metric authorization is single-permission today (not the union of constituent base-metric
  permissions); moot for OWNER, must be fixed when the department × seniority matrix lands (§8).
- Summarization is lossy by design (compacted turns elided from the model's context).
- Live-LLM eval is non-deterministic and requires a configured model.
- Metric definitions remain a code catalogue (`MetricId`/`Dimension` enums); a dynamic catalogue is a
  future cross-cutting change behind the `MetricQueryService`/`MetricCatalog` boundary (§4).

## 19. Open Questions

- **Exact Spring AI 1.1.x mechanism for bounding tool-call rounds.** Pinned during implementation; a
  short spike will confirm the cleanest `ToolCallingManager`/advisor hook and the round-cap semantics
  (stop-and-summarize vs. error).
- **Summarization threshold.** Default is proposed as a token estimate (~4k) with the most recent 6
  turns kept verbatim; confirm the numbers with a real workload.
- **`get_reservation_summary` derived ratios.** Include `no_show_rate` / conversion / avg party size
  now (they are catalogue metrics) — minor extension, confirm.
- **Live-LLM eval provider/model config.** Defaults to the same OpenAI-compatible endpoint as the
  feature; confirm the model and gating env var.
