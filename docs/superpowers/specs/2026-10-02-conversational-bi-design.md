# Goldy's Conversational BI Design

**Status:** In review
**Date:** 2026-10-02
**Scope:** Conversational BI (PRD Requirement 9) — the "Ask Goldy's" assistant — as a
first end-to-end slice: LLM + tool-calling wiring + SSE chat API + frontend drawer +
widget rendering, over the one existing tool (`get_sales_by_period`). Owner-only. No
RAG, no conversation persistence, no pinning, no additional tools.

## 1. Objective

Prove the Conversational BI loop end-to-end: a plain-language sales question → the
model decides to call `get_sales_by_period` → `ToolDispatcher` authorizes and
dispatches → a JSON widget spec streams back and renders in the "Ask Goldy's"
drawer. The fixed, enum-validated tool boundary is enforced **by construction**, not
by prompt discipline.

This slice deliberately proves the loop with the single tool that exists today, so
the LLM→tool→widget boundary is locked before Spring AI is wired into more tools. It
sits directly on the reporting tool framework (PRD Requirement 11 scaffolding) that
just landed.

## 2. Sources of Truth and Baseline

- `docs/prd.md` Requirement 9 (Conversational BI) and Requirement 11 (tool-calling
  framework).
- `docs/system-context.md` — "Restricted, tool-mediated AI access" and
  "JSON-driven UI generation" invariants; the Conversational BI module description.
- `docs/contracts/ai-tool-boundary.md` — the fixed, enum-validated tool boundary.
- `docs/contracts/widget-spec.schema.json` — widget spec v1 (`stat`/`table`/
  `line-chart`/`bar-chart`).
- `docs/superpowers/specs/2026-09-30-phase-two-ui-design.md` §6 — the "Ask Goldy's"
  drawer, answer anatomy, unresolved routing.
- `docs/superpowers/specs/2026-10-02-reporting-tool-framework-design.md` — the tool
  framework this slice builds on.

**Baseline (merged, in `origin/main`):** the reporting tool framework — `ToolId`,
`Metric`, `WidgetSpec`, `ToolResult`, `ReportingTool`, `ToolInput`,
`GetSalesByPeriodInput`, `GetSalesByPeriodTool`, `ToolRegistry`, `ToolDispatcher`
(merged as PR #32; `origin/main` tip `c51d22f`). `ToolDispatcher` authorizes
`reconciliation.sales` READ through `PermissionService` before executing a tool.
Spring AI is still commented out and unpinned in `backend/build.gradle`.

## 3. Scope

### In scope

- Spring AI 2.0.x (OpenAI-compatible), pinned.
- `ChatService` orchestrator + per-request tool callbacks + `ConversationContext`
  accumulator.
- An SSE chat endpoint `POST /api/conversational/chat`, authenticated, role-resolved,
  Owner-gated.
- The "Ask Goldy's" frontend drawer: ask → working state → streamed answer + widgets
  + "How I got this" trace + "as of" + denied/unresolved handling.
- The advisor-chain seam for RAG/memory/guardrails (deferred, see §8).
- Owner-only authorization (see §5).

### Out of scope

- RAG / vector infrastructure (seam only — see §8).
- Conversation persistence and a re-openable history list.
- "Pin to dashboard" (saved reports).
- Additional tools (`get_labor_cost_variance`, product/labor tools) — deferred until
  their resolved views exist.
- Smart Exporter (Requirement 10) — reuses this slice's infrastructure later.
- Freeform SQL, generic query tools, or any write-back.

## 4. Architecture

A new backend package `com.goldys.platform.conversational`, sitting on top of
`com.goldys.platform.reporting` (tool framework) and `com.goldys.platform.auth`:

| Component | Responsibility |
|---|---|
| `AssistantService` | Narrow port: run a conversation over the fixed tool set for a role, streaming results. The controller depends only on this interface. |
| `ChatClientAssistantService` | The implementation: system prompt + per-request `ToolCallback`s + `ChatClient` + advisor chain. |
| `ReportingToolCallbacks` | Adapts each `ReportingTool` bean into a Spring AI `ToolCallback`, bound to the request's `UserRole`; routes through `ToolDispatcher`. |
| `ConversationContext` | Per-request accumulator of tool results + trace + notices. |
| `ChatController` | `SseEmitter` endpoint; `@AuthenticationPrincipal` → `CurrentUserService.roleOf(user)` → `AssistantService`. |
| `ConversationalAiConfig` | Provides the `ChatClient` (system prompt + default advisors), created conditionally when an API key is configured. |

The single invariant everything rests on: **`ToolDispatcher` stays the only place
authorization and enum validation happen.** The model never touches SQL or free-text
filters; the only tool it can call is `get_sales_by_period`, whose input is the typed
record `GetSalesByPeriodInput(startDate, endDate, metric: GROSS_SALES)`.

## 5. Authorization — Owner-only

Two gates, one permission model (the PRD invariant "same permission model, multiple
enforcement points" — no bespoke seniority checks):

1. **Feature gate (new).** Resource key `conversational.chat`. A new migration
   (`V13__seed_conversational_permission.sql`, next in sequence) seeds
   `(department=ALL, seniority=OWNER, resource=conversational.chat, can_read=true,
   can_write=true)` — the same pattern as `V6__seed_admin_permissions.sql`.
   `ChatController` calls `permissions.require(role, "conversational.chat", READ)`
   **before** opening the stream. A non-Owner has no row for that resource →
   `AccessDeniedException` → the existing `ApiExceptionHandler` maps it to **403**.
   Fail-closed by construction.
2. **Data gate (unchanged).** Each tool call still flows through `ToolDispatcher` →
   `permissions.require(role, "reconciliation.sales", READ)`. Kept deliberately even
   though the feature is Owner-only, because Smart Exporter (Req 10) and any future
   broadening reuse the same dispatcher.

**Frontend:** the "Ask" button renders only when
`useCurrentUser().user?.seniority === "Owner"`, following the existing fail-closed
pattern in `business-kpi-grid.tsx` / `staff-page-view.tsx` (absent seniority →
button hidden). The backend 403 remains the source of truth.

## 6. Streaming Protocol and Data Flow

**Endpoint.** `POST /api/conversational/chat` — authenticated, JSON request body,
`text/event-stream` response (Spring MVC `SseEmitter` bridging Spring AI's
`Flux<ChatResponse>`).

**Request.** `{ "messages": [ { "role": "user" | "assistant", "content": "…" } ] }`.
The server is **stateless**: the drawer sends its full in-session turn list each
request (this is how the one-question "clarify" step works), so no server-side
conversation store is needed.

**SSE events** (named):

- `text` → `{ "delta": "…" }` — the model's prose summary, accumulated by the client.
- `answer` → `{ "widgets": [WidgetSpec], "trace": [{"tool","description"}],
  "asOf": ISO-8601, "notices": ["…"] }` — terminal structured payload.
- `error` → `{ "message": "…" }` — terminal failure (also the access-denied surface
  if it ever slips past the controller gate).

**The loop:**

1. Controller resolves `role` via `CurrentUserService`, requires `conversational.chat`
   READ → else 403.
2. `ChatClientAssistantService` takes the configured `ChatClient` (system prompt +
   default advisors) and attaches the per-request tool callbacks — each
   `ReportingTool` wrapped as a `ToolCallback` bound to this request's `UserRole` —
   via `.tools(...)`.
3. The model streams; if it decides it needs data, Spring AI's tool loop (the
   auto-configured `ToolCallingManager`, driving internal tool execution) emits a
   tool call for `get_sales_by_period`, deserializes the JSON args into
   `GetSalesByPeriodInput`, and invokes our callback.
4. The callback calls `ToolDispatcher.dispatch(GET_SALES_BY_PERIOD, input, role)` —
   the single authorization/validation choke point — and records the `ToolResult`
   into the per-request `ConversationContext`, then returns the resolved numbers to
   the model.
5. The model finishes its prose (streamed as `text` deltas) referencing those numbers.
6. On stream completion, `ChatClientAssistantService` reads `ConversationContext` and
   emits the `answer` event: widgets (`ToolResult.widget()`), trace (tool name +
   description), `asOf` timestamp, and notices.

Frontend shows a generic **"Working…"** state while the stream is open; the tool
names surface in the "How I got this" trace once `answer` lands. A real-time
"Running get_sales_by_period…" status line is a later nicety via a loop-inside
advisor — no contract change.

## 7. Tool Boundary and Trust Layer

- **No freeform anything by construction.** The model's only callable tool is
  `get_sales_by_period`; there is no SQL tool and no free-text-filter tool. Spring AI
  derives the parameter schema from the typed record `GetSalesByPeriodInput`, so a
  `metric` value outside the `Metric` enum fails Jackson deserialization before any
  execution.
- **Invalid/unknown args → recoverable.** On deserialization or validation failure,
  the callback returns a structured error to the model ("invalid argument; metric
  must be GROSS_SALES") so the model corrects itself or honestly says it can't answer
  — a raw exception never reaches the user.
- **Denied → explicit, never partial.** `ToolDispatcher` throws
  `AccessDeniedException`; the callback catches it and returns an explicit "you don't
  have access to that data" result the model is instructed to surface verbatim.
- **Unresolved → surfaced, never dropped.** `get_sales_by_period` returns unresolved
  dates in `ToolResult.notices()`; these flow through to `answer.notices` and render
  as "N dates are unresolved — reconcile them first" (deep-link to
  `/reconciliation`), per phase-two-ui-design §6.5.
- **System prompt** is the only "personality" surface: the model is "Ask Goldy's", it
  may only answer by calling the provided tools, it must cite the numbers it was
  given and never invent figures, and it must relay denials/unresolved notices as-is.

## 8. Extensibility and Future-Proofing

Three mechanisms, in response to the requirement that tools, the prompt, and the
eventual agent harness be changeable without rework.

1. **Modular, changeable tools.** The tool set is discovered, not hardcoded:
   `ChatClientAssistantService` takes `List<ReportingTool>` (all beans) and wraps each
   into a `ToolCallback` dynamically. Adding a tool = one `@Component` implementing
   `ReportingTool` + a `ToolId` enum value + its input record. Zero changes to chat
   wiring, the controller, or the prompt. The `ReportingTool` contract already
   carries exactly what the LLM and any harness need — `id()`, `name()`,
   `description()`, `inputType()` (for JSON-schema generation), and
   `execute(input, role)`. `inputType()` is the one addition this slice makes to the
   contract: Spring AI needs the concrete input type to derive the parameter schema
   and deserialize the model's JSON args.
2. **Easily-changeable system prompt.** The prompt is not in Java — it is externalized
   to a classpath resource `prompts/ask-goldys-system.txt`, read through a config
   property (`app.conversational.system-prompt`) that defaults to that file and is
   overridable via env without recompiling.
3. **Future-proof for an agent harness.** The seam is three layers, all already
   present in this design:

   - **Tools** — every `ReportingTool` becomes a `ToolCallback`. Skills, callables,
     and future tools are just more callbacks; `ToolCallingManager` discovers and
     executes them.
   - **Advisor chain** — where RAG (`QuestionAnswerAdvisor`), memory, and guardrails
     compose by ordering around the chat call (available in 1.1.x today).
   - **`ToolCallingManager`** — the decorator seam where a full harness's governance
     (round limits, human-in-the-loop approval, interceptor chains) plugs in later;
     a custom `ToolCallingManager` bean wraps the default without subclassing
     Spring AI internals.

   Spring AI 1.1.x already exposes all three seams. The 2.0-style composable tool
   loop (`ToolCallingAdvisor` / `ToolSearchToolCallingAdvisor`) is a Spring AI 2.x
   feature that arrives with a future Spring Boot 4 upgrade; the `ToolCallback`
   contract is stable across that boundary, so the upgrade changes the loop's
   internals, not our tools.

   The `AssistantService` port insulates the controller from the orchestration
   strategy, so an agent-harness implementation can replace `ChatClientAssistantService`
   without touching tools, controller, or prompt.

   **Caveat (policy, not architecture):** this design keeps the *fixed, enum-validated*
   tool set invariant — deliberately narrower than an open-ended agent that plans and
   discovers arbitrary actions. A truly open-ended agent would require relaxing the
   enum-whitelist policy, a contained change rather than a rearchitecture.

   **Pin Spring AI 1.1.x** (the line compatible with this project's Spring Boot
   3.5.x; Spring AI 2.x requires Spring Boot 4.x). Use the `ToolCallback` /
   `FunctionToolCallback` API — `FunctionCallback` is the deprecated 1.0-era name.

## 9. Config and Secrets

All environment-driven, no secrets in the repo:

| Setting | Default | Notes |
|---|---|---|
| `spring.ai.openai.base-url` | `https://api.openai.com/v1` | overridable for any OpenAI-compatible endpoint |
| `spring.ai.openai.api-key` | *(unset)* | via `OPENAI_API_KEY`; the chat beans are created only when this is set |
| `spring.ai.openai.chat.options.model` | `gpt-4o-mini` | cheap default; bump for higher quality |
| `spring.ai.openai.chat.options.temperature` | `0` | deterministic for tool-calling/trust |
| `app.conversational.system-prompt` | `prompts/ask-goldys-system.txt` | §8.2 |

`.env.example` gains optional `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL`
placeholders (never committed real values). The chat feature is **optional**: when
`spring.ai.openai.api-key` is absent, the OpenAI `ChatModel`/`ChatClient` beans are
not created, the app still boots, and the chat endpoint (if reached) returns a clear
"AI not configured" error — never a startup crash.

## 10. Frontend

New `frontend/components/ask-goldys/`:

- **`ask-goldys-drawer.tsx`** — shadcn `Sheet`, opened from an "Ask" button in
  `app-shell` (rendered only when `seniority === "Owner"`) + `⌘K`. Empty state with a
  small static suggestion-chip set; message list; input.
- **`use-ask-goldys.ts`** — POSTs `{messages}` to `/api/conversational/chat` with
  `fetch` + `ReadableStream` parsing of the `text/event-stream` (no new dependency —
  native streaming, since `EventSource` cannot POST). Maintains accumulated summary,
  the `answer` payload, `error`, and a `working` flag.
- **`widget-renderer.tsx`** — maps `WidgetSpec.type` → `StatCard` / table / Recharts
  `LineChart`/`BarChart` (Recharts already a dependency). Unknown type → nothing
  rendered + a notice (never crash).
- **`answer-block.tsx`** — summary + widgets + collapsible "How I got this" trace
  (tool name + source) + "as of" timestamp + notices. Denied/error renders the
  explicit message, never a partial answer.

## 11. Testing Strategy

- **Unit (backend):** `ChatClientAssistantService` maps a model tool-call to
  `ToolDispatcher` (fake `ChatModel`), collects `ToolResult` → `answer` payload;
  callback catches `AccessDeniedException` → explicit denied result; invalid args →
  structured error result; notices flow through.
- **Integration (PostgreSQL + Testcontainers):** seed resolved daily sales, run a
  scripted `ChatClient` against the real dispatcher, assert the `answer` event carries
  the resolved `line-chart` widget + empty notices.
- **Auth:** non-Owner → 403 before any stream; Owner → 200 stream.
- **Frontend (Vitest):** `widget-renderer` renders each type; `answer-block` shows
  trace/notices/denied; `use-ask-goldys` parses the SSE stream (mock `fetch`).
- **Config:** prompt loads from the resource file; absent API key → clear
  "not configured" state.

## 12. Decisions Recorded

- Prove the loop over the **one existing tool** (`get_sales_by_period`); no new tools.
- **OpenAI-compatible** provider, model/configurable via env.
- **Streaming (SSE)** via Spring MVC `SseEmitter` bridging `Flux` — no WebFlux.
- **Owner-only** via a new `conversational.chat` permission row; tool-level
  `reconciliation.sales` gate unchanged.
- **Defer** RAG, conversation persistence, and pin-to-dashboard; keep the
  advisor-chain seam.
- **System prompt** externalized to a classpath resource.
- **Tools discovered**, not hardcoded; `AssistantService` port isolates the
  orchestration strategy.
- **Spring AI 1.1.x** pinned (the Spring Boot 3.5.x line; 2.x needs Spring Boot 4);
  `ToolCallback` / `FunctionToolCallback` API.

## 13. Open Questions

- **Exact model default.** `gpt-4o-mini` is assumed as a cheap, deterministic default;
  confirm the production model (and whether a higher-capability model is needed for
  reliable tool-calling) before launch.
- **Suggestion-chip set per role.** Deferred (field-to-role mapping is still a PRD
  open question); this slice ships a small static, Owner-appropriate set.
- **Base URL / API key ownership.** Confirm the production OpenAI-compatible endpoint
  and how the key is provisioned (owner-provided, per §9).
