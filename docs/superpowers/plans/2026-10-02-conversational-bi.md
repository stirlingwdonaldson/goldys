# Conversational BI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the Conversational BI loop end-to-end: a plain-language sales question → the model calls `get_sales_by_period` → `ToolDispatcher` authorizes/dispatches → a JSON widget spec streams back and renders in the "Ask Goldy's" drawer. Owner-only.

**Architecture:** A new `com.goldys.platform.conversational` package. `ReportingTool` beans are discovered and wrapped as Spring AI `ToolCallback`s (bound to the request's `UserRole`), routing every call through the existing `ToolDispatcher` — the single authorization/enum-validation choke point. An `AssistantService` port streams `ConversationEvent`s (text deltas + a terminal answer payload) which `ChatController` bridges to SSE. The frontend "Ask Goldy's" drawer renders streamed prose + widget specs + a provenance trace.

**Tech Stack:** Java 25, Spring Boot 3.5.0, **Spring AI 1.1.8** (the Spring Boot 3.5-compatible line — Spring AI 2.x requires Boot 4), Spring Data JPA, PostgreSQL 16 (Testcontainers), JUnit 5 + Mockito + AssertJ, Gradle (committed wrapper) + Spotless; Next.js 15 + React 19 + TypeScript, Tailwind, shadcn/ui (Sheet), Recharts 2.15.4, Bun, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-02-conversational-bi-design.md`

## Global Constraints

- Java 25; run everything with the committed Gradle wrapper (`./gradlew`), never a system Gradle. Frontend uses Bun (`bun run`, `bun install`).
- Spring AI **1.1.8** (BOM `org.springframework.ai:spring-ai-bom:1.1.8`, starter `org.springframework.ai:spring-ai-starter-model-openai`). Do NOT pull Spring AI 2.x — it requires Spring Boot 4.
- Tools read **resolved views only** — never raw or canonical tables directly. No freeform SQL, no free-text field/filter parameters.
- The LLM may only call the fixed tool set via `ToolDispatcher`; `ToolDispatcher` stays the only place authorization + enum validation happen. Do not add a second authorization path.
- Owner-only: resource key `conversational.chat`, seeded `(ALL, OWNER, conversational.chat, true, true)`. Non-owner → 403, never a partial answer. Frontend fails closed on `seniority === "Owner"`.
- Widget specs conform to `widget-spec.schema.json` v1 (`stat`/`table`/`line-chart`/`bar-chart`); never executable code.
- The chat feature is **optional**: absent `OPENAI_API_KEY` → app still boots, endpoint returns a clear "AI not configured" error. Never a startup crash.
- No secrets in the repo; `.env.example` only gains placeholders.
- Verification gates: `cd backend && ./gradlew test spotlessCheck build`; `cd frontend && bun run typecheck && bun run lint && bun run test`.
- Atomic Conventional Commits on branch `feature/conversational-bi`. Never commit to `main`.

## Review Focus

The input classes / failure modes the spec implies but whose happy-path tests would not otherwise exercise. Each is pinned by a test in the named task.

1. **A non-Owner reaches `/api/conversational/chat`** — 403 with `NOT_PERMITTED`, never an opened stream or a partial answer. → Task 7.
2. **A tool dispatch that throws `IllegalArgumentException`** (e.g. a validation failure) — the callback returns a structured error to the model, never propagates the exception. Out-of-enum values are rejected even earlier, by the typed record + Spring AI deserialization, by construction. → Task 4.
3. **A date range with an unresolved date** — the unresolved date appears in `answer.notices`, never silently dropped or presented as a resolved value. → Task 4 + Task 8.
4. **No API key configured** — the app boots, `AssistantService` is absent, and the endpoint returns a clear "AI not configured" error. → Task 6 + Task 7.
5. **A question the model answers without calling any tool** (e.g. "hello") — the answer carries zero widgets and an empty trace, and the stream completes normally. → Task 5.

---

## Task 1: Spring AI dependency + app config

**Files:**
- Modify: `backend/build.gradle:36-41`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/prompts/ask-goldys-system.txt`
- Modify: `.env.example`

**Interfaces:**
- Consumes: nothing new.
- Produces: the Spring AI dependency, the `spring.ai.*` + `app.conversational.*` property surface, and the prompt resource consumed by Tasks 5–7.

- [ ] **Step 1: Replace the stale Spring AI comment in `build.gradle`**

In `backend/build.gradle`, replace the commented-out block:

```gradle
	// Spring AI: needed for the Conversational BI / Smart Exporter tool-calling
	// framework (Phase 2, spec Requirements 9-10), not Phase 1. Coordinates and
	// version are deliberately left unpinned here - Spring AI's artifact IDs and
	// BOM version have moved fast; pin the current one when Phase 2 scaffolding
	// (Requirement 11) actually starts, rather than guessing now.
	// implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter'
```

with:

```gradle
	// Spring AI 1.1.x is the Spring Boot 3.5-compatible line (2.x requires Boot 4).
	implementation platform('org.springframework.ai:spring-ai-bom:1.1.8')
	implementation 'org.springframework.ai:spring-ai-starter-model-openai'
```

- [ ] **Step 2: Add the `spring.ai` + `app` config to `application.yml`**

Append to `backend/src/main/resources/application.yml`:

```yaml
  ai:
    model:
      # Default OFF so the app boots without a key. The OpenAI chat auto-config is
      # enabled-by-default when the starter is present; `none` disables it. Set
      # SPRING_AI_MODEL_CHAT=openai plus OPENAI_API_KEY to turn Conversational BI on.
      chat: ${SPRING_AI_MODEL_CHAT:none}
    openai:
      api-key: ${OPENAI_API_KEY:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4o-mini}
          temperature: 0

app:
  conversational:
    system-prompt: prompts/ask-goldys-system.txt
```

Note: the `ai:` block is nested under the existing `spring:` key (which already holds `application`, `datasource`, `jpa`, `flyway`, `servlet`); the `app:` block is top-level. Keep indentation consistent (2 spaces per level).

- [ ] **Step 3: Write the system prompt resource**

Create `backend/src/main/resources/prompts/ask-goldys-system.txt`:

```text
You are "Ask Goldy's", the reporting assistant for Goldy's pub.

You answer questions about the pub's data by calling the tools provided to you. Rules:
- Only answer using data returned by the tools. Never invent, estimate, or guess a number.
- If a tool returns a denial ("you don't have access"), tell the user plainly that they don't have access to that data. Never answer with a partial or approximate result.
- If a tool reports unresolved data (its notices), mention it explicitly rather than presenting the answer as complete.
- If a question needs data that no available tool provides, say so rather than making something up.
- If a question is ambiguous (missing a period or metric), ask one short clarifying question.
- Keep answers short: one or two sentences of summary, citing the numbers the tools returned.
```

- [ ] **Step 4: Add optional OpenAI placeholders to `.env.example`**

Append to `.env.example`:

```bash
# ---- Conversational BI ("Ask Goldy's") ----
# Optional. Leave unset to keep the feature disabled (the app boots fine without it).
# OPENAI_API_KEY=
# OpenAI-compatible base URL (defaults to api.openai.com/v1).
# OPENAI_BASE_URL=
# Model name (defaults to gpt-4o-mini).
# OPENAI_MODEL=
# Must be "openai" to activate the chat model auto-config (default "none").
# SPRING_AI_MODEL_CHAT=
```

- [ ] **Step 5: Verify the build still passes without a key**

Run: `cd backend && ./gradlew test spotlessCheck build`
Expected: PASS. The OpenAI auto-config must stay inert because `spring.ai.model.chat=none` by default — all existing `@SpringBootTest` contexts boot without an API key.

- [ ] **Step 6: Commit**

```bash
git add backend/build.gradle backend/src/main/resources/application.yml backend/src/main/resources/prompts/ask-goldys-system.txt .env.example
git commit -m "feat: add Spring AI dependency and Conversational BI config"
```

---

## Task 2: Add `inputType()` to the `ReportingTool` contract

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ReportingTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodTool.java`
- Test: `backend/src/test/java/com/goldys/platform/reporting/ReportingToolTest.java`

**Interfaces:**
- Consumes: `ReportingTool`, `ToolInput`, `GetSalesByPeriodInput` (existing).
- Produces: `ReportingTool.inputType(): Class<? extends ToolInput>` — used by Task 4 to generate the tool's JSON schema.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/reporting/ReportingToolTest.java`:

```java
package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportingToolTest {

  @Test
  void exposesItsInputType() {
    ReportingTool tool = new GetSalesByPeriodTool(null);

    assertThat(tool.inputType()).isEqualTo(GetSalesByPeriodInput.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*ReportingToolTest'`
Expected: FAIL — `inputType()` does not exist.

- [ ] **Step 3: Add the method to the interface**

In `ReportingTool.java`, add after `description()`:

```java
  /** The concrete {@link ToolInput} type; Spring AI derives the tool's JSON schema from it. */
  Class<? extends ToolInput> inputType();
```

- [ ] **Step 4: Implement it in `GetSalesByPeriodTool`**

In `GetSalesByPeriodTool.java`, add after the `description()` override:

```java
  @Override
  public Class<? extends ToolInput> inputType() {
    return GetSalesByPeriodInput.class;
  }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ReportingToolTest'`
Expected: PASS.

- [ ] **Step 6: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/main/java/com/goldys/platform/reporting/ReportingTool.java backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodTool.java backend/src/test/java/com/goldys/platform/reporting/ReportingToolTest.java
git commit -m "feat: expose inputType on the reporting tool contract"
```

---

## Task 3: Conversation value types

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/conversational/ChatRequest.java`
- Create: `backend/src/main/java/com/goldys/platform/conversational/ConversationEvent.java`
- Create: `backend/src/main/java/com/goldys/platform/conversational/AnswerPayload.java`
- Create: `backend/src/main/java/com/goldys/platform/conversational/ConversationContext.java`
- Test: `backend/src/test/java/com/goldys/platform/conversational/ChatRequestTest.java`

**Interfaces:**
- Consumes: `WidgetSpec` (existing, `com.goldys.platform.reporting`).
- Produces: `ChatRequest(List<ChatMessage>)` / `ChatRequest.ChatMessage(String role, String content)`; `ConversationEvent` (sealed: `TextDelta`, `Answer`, `Error`); `AnswerPayload(List<WidgetSpec>, List<TraceEntry>, Instant, List<String>)` with nested `TraceEntry(String tool, String description)`; `ConversationContext` with `record(ReportingTool, ToolResult)` and `toAnswerPayload()`. Used by Tasks 4–7.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/conversational/ChatRequestTest.java`:

```java
package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatRequestTest {

  @Test
  void acceptsUserAndAssistantMessages() {
    ChatRequest request =
        new ChatRequest(
            List.of(
                new ChatRequest.ChatMessage("user", "What were sales last week?"),
                new ChatRequest.ChatMessage("assistant", "I need a date range.")));

    assertThat(request.messages()).hasSize(2);
    assertThat(request.messages().get(0).role()).isEqualTo("user");
  }

  @Test
  void rejectsAnUnknownRole() {
    assertThatThrownBy(() -> new ChatRequest.ChatMessage("system", "be helpful"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("role");
  }

  @Test
  void rejectsANullContent() {
    assertThatThrownBy(() -> new ChatRequest.ChatMessage("user", null))
        .isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*ChatRequestTest'`
Expected: FAIL — `ChatRequest` does not exist.

- [ ] **Step 3: Write the value types**

`backend/src/main/java/com/goldys/platform/conversational/ChatRequest.java`:

```java
package com.goldys.platform.conversational;

import java.util.List;
import java.util.Objects;

/** The request body of a chat turn: the in-session message history. */
public record ChatRequest(List<ChatMessage> messages) {

  public ChatRequest {
    messages = messages == null ? List.of() : List.copyOf(messages);
  }

  /** A single message in the conversation. Only user and assistant roles are accepted. */
  public record ChatMessage(String role, String content) {
    public ChatMessage {
      Objects.requireNonNull(role, "role");
      Objects.requireNonNull(content, "content");
      if (!role.equals("user") && !role.equals("assistant")) {
        throw new IllegalArgumentException("Unknown message role: " + role);
      }
    }
  }
}
```

`backend/src/main/java/com/goldys/platform/conversational/ConversationEvent.java`:

```java
package com.goldys.platform.conversational;

/** A single event in the streamed conversation, mapped to an SSE named event. */
public sealed interface ConversationEvent
    permits ConversationEvent.TextDelta, ConversationEvent.Answer, ConversationEvent.Error {

  /** A chunk of the model's prose summary (SSE event `text`). */
  record TextDelta(String delta) implements ConversationEvent {}

  /** The terminal structured payload (SSE event `answer`). */
  record Answer(AnswerPayload payload) implements ConversationEvent {}

  /** A terminal failure (SSE event `error`). */
  record Error(String message) implements ConversationEvent {}
}
```

`backend/src/main/java/com/goldys/platform/conversational/AnswerPayload.java`:

```java
package com.goldys.platform.conversational;

import com.goldys.platform.reporting.WidgetSpec;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** The structured artifact attached to every answer: widgets, trace, "as of", notices. */
public record AnswerPayload(
    List<WidgetSpec> widgets, List<TraceEntry> trace, Instant asOf, List<String> notices) {

  public AnswerPayload {
    widgets = widgets == null ? List.of() : List.copyOf(widgets);
    trace = trace == null ? List.of() : List.copyOf(trace);
    Objects.requireNonNull(asOf, "asOf");
    notices = notices == null ? List.of() : List.copyOf(notices);
  }

  /** One provenance entry naming a tool the answer ran. */
  public record TraceEntry(String tool, String description) {}
}
```

`backend/src/main/java/com/goldys/platform/conversational/ConversationContext.java`:

```java
package com.goldys.platform.conversational;

import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolResult;
import com.goldys.platform.reporting.WidgetSpec;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-request accumulator of what the tool callbacks produced. Populated by the
 * callbacks as Spring AI runs the tool loop, then read once the stream completes to
 * build the terminal {@link AnswerPayload}. Thread-safe because the callbacks can run
 * on reactive scheduler threads.
 */
public class ConversationContext {
  private final List<AnswerPayload.TraceEntry> trace = new CopyOnWriteArrayList<>();
  private final List<WidgetSpec> widgets = new CopyOnWriteArrayList<>();
  private final List<String> notices = new CopyOnWriteArrayList<>();
  private final Instant asOf = Instant.now();

  public void record(ReportingTool tool, ToolResult result) {
    trace.add(new AnswerPayload.TraceEntry(tool.name(), tool.description()));
    widgets.add(result.widget());
    notices.addAll(result.notices());
  }

  public AnswerPayload toAnswerPayload() {
    return new AnswerPayload(List.copyOf(widgets), List.copyOf(trace), asOf, List.copyOf(notices));
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ChatRequestTest'`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/main/java/com/goldys/platform/conversational/ backend/src/test/java/com/goldys/platform/conversational/ChatRequestTest.java
git commit -m "feat: add conversational value types"
```

---

## Task 4: `ReportingToolCallbacks` adapter

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/conversational/ReportingToolCallbacks.java`
- Test: `backend/src/test/java/com/goldys/platform/conversational/ReportingToolCallbacksTest.java`

**Interfaces:**
- Consumes: `ReportingTool` (with `inputType()`), `ToolInput`, `ToolDispatcher`, `ToolResult`, `UserRole`, `ConversationContext`, Jackson `ObjectMapper` (Spring-provided bean).
- Produces: `ReportingToolCallbacks.forTools(List<ReportingTool>, UserRole, ConversationContext, ToolDispatcher, ObjectMapper): List<ToolCallback>` (Spring AI `org.springframework.ai.tool.ToolCallback`). Used by Task 5.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/conversational/ReportingToolCallbacksTest.java`:

```java
package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.Metric;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolInput;
import com.goldys.platform.reporting.ToolResult;
import com.goldys.platform.reporting.WidgetSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class ReportingToolCallbacksTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void dispatchesThroughTheToolAndRecordsTheResult() throws Exception {
    ToolDispatcher dispatcher = mock(ToolDispatcher.class);
    ToolResult result =
        new ToolResult(
            new WidgetSpec(1, "line-chart", "Daily sales", null, List.of()),
            List.of("1 date(s) have no resolved total (unresolved conflict)."));
    when(dispatcher.dispatch(eq(ToolId.GET_SALES_BY_PERIOD), any(), eq(OWNER))).thenReturn(result);

    ReportingTool tool = tool();
    ConversationContext context = new ConversationContext();
    ReportingToolCallbacks adapter = new ReportingToolCallbacks();

    ToolCallback callback = adapter.forTools(List.of(tool), OWNER, context, dispatcher, mapper).get(0);

    String raw =
        callback.call(
            "{\"startDate\":\"2026-09-13\",\"endDate\":\"2026-09-13\",\"metric\":\"GROSS_SALES\"}");

    assertThat(raw).contains("\"ok\":true");
    assertThat(context.toAnswerPayload().widgets()).hasSize(1);
    assertThat(context.toAnswerPayload().trace()).hasSize(1);
    assertThat(context.toAnswerPayload().notices())
        .containsExactly("1 date(s) have no resolved total (unresolved conflict).");
  }

  @Test
  void returnsAStructuredDenialInsteadOfThrowing() throws Exception {
    ToolDispatcher dispatcher = mock(ToolDispatcher.class);
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(dispatcher)
        .dispatch(eq(ToolId.GET_SALES_BY_PERIOD), any(), eq(OWNER));

    ReportingToolCallbacks adapter = new ReportingToolCallbacks();
    ToolCallback callback = adapter.forTools(List.of(tool()), OWNER, new ConversationContext(), dispatcher, mapper).get(0);

    String raw = callback.call("{\"startDate\":\"2026-09-13\",\"endDate\":\"2026-09-13\",\"metric\":\"GROSS_SALES\"}");

    assertThat(raw).contains("\"ok\":false");
    assertThat(raw).contains("access");
  }

  @Test
  void returnsAStructuredErrorInsteadOfPropagatingAnIllegalArgument() throws Exception {
    ToolDispatcher dispatcher = mock(ToolDispatcher.class);
    doThrow(new IllegalArgumentException("endDate is before startDate"))
        .when(dispatcher)
        .dispatch(eq(ToolId.GET_SALES_BY_PERIOD), any(), eq(OWNER));

    ReportingToolCallbacks adapter = new ReportingToolCallbacks();
    ToolCallback callback = adapter.forTools(List.of(tool()), OWNER, new ConversationContext(), dispatcher, mapper).get(0);

    String raw = callback.call("{\"startDate\":\"2026-09-14\",\"endDate\":\"2026-09-13\",\"metric\":\"GROSS_SALES\"}");

    assertThat(raw).contains("\"ok\":false");
    assertThat(raw).contains("endDate");
  }

  private static ReportingTool tool() {
    ReportingTool t = mock(ReportingTool.class);
    when(t.id()).thenReturn(ToolId.GET_SALES_BY_PERIOD);
    when(t.name()).thenReturn("get_sales_by_period");
    when(t.description()).thenReturn("Resolved daily sales totals for a date range.");
    when(t.inputType()).thenReturn(com.goldys.platform.reporting.GetSalesByPeriodInput.class);
    return t;
  }
}
```

Note: `callback.call(...)` deserializes the JSON args into `GetSalesByPeriodInput` via the input type, then routes through the mocked dispatcher. The assertion reads the model-facing outcome string (a JSON map with `"ok"`), and verifies the context was populated.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*ReportingToolCallbacksTest'`
Expected: FAIL — `ReportingToolCallbacks` does not exist.

- [ ] **Step 3: Write the adapter**

`backend/src/main/java/com/goldys/platform/conversational/ReportingToolCallbacks.java`:

```java
package com.goldys.platform.conversational;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolInput;
import com.goldys.platform.reporting.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

/**
 * Adapts each {@link ReportingTool} into a Spring AI {@link ToolCallback}, bound to the
 * request's {@link UserRole}, so the model can only reach the fixed tool set through the
 * {@link ToolDispatcher} (the single authorization + enum-validation choke point).
 */
@Component
public class ReportingToolCallbacks {

  public List<ToolCallback> forTools(
      List<ReportingTool> tools,
      UserRole role,
      ConversationContext context,
      ToolDispatcher dispatcher,
      ObjectMapper mapper) {
    return tools.stream().map(t -> toCallback(t, role, context, dispatcher, mapper)).toList();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ToolCallback toCallback(
      ReportingTool tool,
      UserRole role,
      ConversationContext context,
      ToolDispatcher dispatcher,
      ObjectMapper mapper) {
    return FunctionToolCallback.builder(
            tool.name(),
            (java.util.function.Function<ToolInput, Map<String, Object>>)
                input -> {
                  try {
                    ToolResult result = dispatcher.dispatch(tool.id(), input, role);
                    context.record(tool, result);
                    return outcome(true, result);
                  } catch (AccessDeniedException e) {
                    return outcome(false, "You don't have access to that data.");
                  } catch (IllegalArgumentException e) {
                    return outcome(false, e.getMessage());
                  }
                })
        .description(tool.description())
        .inputType((Class) tool.inputType())
        .build();
  }

  private static Map<String, Object> outcome(boolean ok, Object resultOrMessage) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ok", ok);
    if (ok) {
      ToolResult result = (ToolResult) resultOrMessage;
      m.put("widget", result.widget());
      m.put("notices", result.notices());
    } else {
      m.put("error", resultOrMessage);
    }
    return m;
  }
}
```

Note: the raw `(Class)` cast on `tool.inputType()` is the deliberate, contained erasure that lets `FunctionToolCallback` bind a `Function<ToolInput, ?>` to the tool's *concrete* input type (`GetSalesByPeriodInput`) for JSON-schema generation and arg deserialization. `mapper` is threaded through the signature for a single serialization dependency even though the happy path's outcome map is serialized by Spring AI itself; it is used if a tool result needs explicit JSON rendering.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ReportingToolCallbacksTest'`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/main/java/com/goldys/platform/conversational/ReportingToolCallbacks.java backend/src/test/java/com/goldys/platform/conversational/ReportingToolCallbacksTest.java
git commit -m "feat: adapt reporting tools to Spring AI tool callbacks"
```

---

## Task 5: `AssistantService` + `ChatClientAssistantService`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/conversational/AssistantService.java`
- Create: `backend/src/main/java/com/goldys/platform/conversational/ChatClientAssistantService.java`
- Test: `backend/src/test/java/com/goldys/platform/conversational/ChatClientAssistantServiceTest.java`

**Interfaces:**
- Consumes: `ChatClient` (Spring AI), `List<ReportingTool>`, `ToolDispatcher`, `ReportingToolCallbacks` (Task 4), `ObjectMapper`, `UserRole`, `ChatRequest`, `ConversationEvent`, `ConversationContext` (Task 3).
- Produces: `AssistantService.stream(ChatRequest, UserRole): Flux<ConversationEvent>`. Used by Task 6 (bean) and Task 7 (controller).

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/conversational/ChatClientAssistantServiceTest.java`:

```java
package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.ToolDispatcher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class ChatClientAssistantServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void emitsTextDeltasThenATerminalAnswer() {
    ChatModel model = mock(ChatModel.class);
    when(model.stream(any(Prompt.class)))
        .thenReturn(
            Flux.just(
                new ChatResponse(
                    List.of(new Generation(new AssistantMessage("Sales were $27,650.66."))))));

    ChatClientAssistantService service =
        new ChatClientAssistantService(
            ChatClient.builder(model).build(),
            List.of(),
            mock(ToolDispatcher.class),
            new ReportingToolCallbacks(),
            new ObjectMapper());

    List<ConversationEvent> events =
        service
            .stream(
                new ChatRequest(
                    List.of(new ChatRequest.ChatMessage("user", "What were sales last week?"))),
                OWNER)
            .collectList()
            .block();

    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isInstanceOf(ConversationEvent.TextDelta.class);
    assertThat(((ConversationEvent.TextDelta) events.get(0)).delta()).contains("$27,650.66");
    assertThat(events.get(1)).isInstanceOf(ConversationEvent.Answer.class);
    ConversationEvent.Answer answer = (ConversationEvent.Answer) events.get(1);
    assertThat(answer.payload().widgets()).isEmpty();
    assertThat(answer.payload().trace()).isEmpty();
    assertThat(answer.payload().notices()).isEmpty();
  }

  @Test
  void mapsAModelErrorToAnErrorEvent() {
    ChatModel model = mock(ChatModel.class);
    when(model.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("boom")));

    ChatClientAssistantService service =
        new ChatClientAssistantService(
            ChatClient.builder(model).build(),
            List.of(),
            mock(ToolDispatcher.class),
            new ReportingToolCallbacks(),
            new ObjectMapper());

    List<ConversationEvent> events =
        service
            .stream(
                new ChatRequest(List.of(new ChatRequest.ChatMessage("user", "hello"))), OWNER)
            .collectList()
            .block();

    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isInstanceOf(ConversationEvent.Error.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*ChatClientAssistantServiceTest'`
Expected: FAIL — `AssistantService` / `ChatClientAssistantService` do not exist. (If the `ChatResponse`/`Generation`/`AssistantMessage` constructor signatures differ in the resolved 1.1.8 jar, the compiler will say so — adjust to the actual signatures, which is expected version-sensitive drift, not a plan error.)

- [ ] **Step 3: Write the interface**

`backend/src/main/java/com/goldys/platform/conversational/AssistantService.java`:

```java
package com.goldys.platform.conversational;

import com.goldys.platform.auth.UserRole;
import reactor.core.publisher.Flux;

/**
 * Runs a conversation over the fixed tool set for a given role, streaming
 * {@link ConversationEvent}s. The controller depends only on this port, so a future
 * agent-harness implementation can replace it without touching tools or the controller.
 */
public interface AssistantService {
  Flux<ConversationEvent> stream(ChatRequest request, UserRole role);
}
```

- [ ] **Step 4: Write the implementation**

`backend/src/main/java/com/goldys/platform/conversational/ChatClientAssistantService.java`:

```java
package com.goldys.platform.conversational;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** The {@link AssistantService} backed by a Spring AI {@link ChatClient} + tool callbacks. */
public class ChatClientAssistantService implements AssistantService {

  private final ChatClient chatClient;
  private final List<ReportingTool> tools;
  private final ToolDispatcher dispatcher;
  private final ReportingToolCallbacks callbacks;
  private final ObjectMapper mapper;

  public ChatClientAssistantService(
      ChatClient chatClient,
      List<ReportingTool> tools,
      ToolDispatcher dispatcher,
      ReportingToolCallbacks callbacks,
      ObjectMapper mapper) {
    this.chatClient = chatClient;
    this.tools = tools;
    this.dispatcher = dispatcher;
    this.callbacks = callbacks;
    this.mapper = mapper;
  }

  @Override
  public Flux<ConversationEvent> stream(ChatRequest request, UserRole role) {
    ConversationContext context = new ConversationContext();
    List<ToolCallback> toolCallbacks =
        callbacks.forTools(tools, role, context, dispatcher, mapper);
    List<Message> messages = request.messages().stream().map(this::toMessage).toList();

    return chatClient
        .prompt()
        .messages(messages)
        .toolCallbacks(toolCallbacks.toArray(ToolCallback[]::new))
        .stream()
        .content()
        .filter(Objects::nonNull)
        .map(ConversationEvent.TextDelta::new)
        // Deferred so the payload is built after the tool callbacks have populated the context.
        .concatWith(Mono.fromSupplier(() -> new ConversationEvent.Answer(context.toAnswerPayload())))
        .onErrorResume(
            e -> Mono.just(new ConversationEvent.Error("Something went wrong generating the answer.")));
  }

  private Message toMessage(ChatRequest.ChatMessage m) {
    return switch (m.role()) {
      case "user" -> new UserMessage(m.content());
      case "assistant" -> new AssistantMessage(m.content());
      default -> throw new IllegalArgumentException("Unknown message role: " + m.role());
    };
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ChatClientAssistantServiceTest'`
Expected: PASS.

- [ ] **Step 6: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/main/java/com/goldys/platform/conversational/AssistantService.java backend/src/main/java/com/goldys/platform/conversational/ChatClientAssistantService.java backend/src/test/java/com/goldys/platform/conversational/ChatClientAssistantServiceTest.java
git commit -m "feat: add the conversational assistant service"
```

---

## Task 6: `ConversationalAiConfig` (conditional beans)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/conversational/ConversationalAiConfig.java`
- Test: `backend/src/test/java/com/goldys/platform/conversational/ConversationalAiConfigTest.java`

**Interfaces:**
- Consumes: `ChatModel` (Spring AI, auto-configured by the OpenAI starter when `spring.ai.model.chat=openai` + `spring.ai.openai.api-key` are set), `List<ReportingTool>`, `ToolDispatcher`, `ReportingToolCallbacks`, `ObjectMapper`.
- Produces: `ChatClient` and `AssistantService` beans, both `@ConditionalOnBean(ChatModel.class)`. Used by Task 7.

- [ ] **Step 1: Write the config**

`backend/src/main/java/com/goldys/platform/conversational/ConversationalAiConfig.java`:

```java
package com.goldys.platform.conversational;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Wires the Conversational BI beans, all gated on a {@link ChatModel} existing — which the
 * OpenAI starter only creates when {@code spring.ai.model.chat=openai} and an API key are
 * set. Without a key, none of these beans exist and the feature degrades cleanly.
 */
@Configuration
public class ConversationalAiConfig {

  @Bean
  @ConditionalOnBean(ChatModel.class)
  ChatClient conversationalChatClient(
      ChatModel model,
      @Value("${app.conversational.system-prompt:prompts/ask-goldys-system.txt}")
          String promptResource)
      throws IOException {
    String systemPrompt;
    try (var in = new ClassPathResource(promptResource).getInputStream()) {
      systemPrompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    return ChatClient.builder(model).defaultSystem(systemPrompt).build();
  }

  @Bean
  @ConditionalOnBean(ChatModel.class)
  AssistantService assistantService(
      ChatClient chatClient,
      List<ReportingTool> tools,
      ToolDispatcher dispatcher,
      ReportingToolCallbacks callbacks,
      ObjectMapper mapper) {
    return new ChatClientAssistantService(chatClient, tools, dispatcher, callbacks, mapper);
  }
}
```

- [ ] **Step 2: Write the wiring test**

`backend/src/test/java/com/goldys/platform/conversational/ConversationalAiConfigTest.java`:

```java
package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ConversationalAiConfigTest {

  @Autowired ObjectProvider<AssistantService> assistant;

  @Test
  void assistantServiceIsAbsentWithoutAnApiKey() {
    // The default context runs with spring.ai.model.chat=none and no key.
    assertThat(assistant.getIfAvailable()).isNull();
  }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ConversationalAiConfigTest'`
Expected: PASS — the default context boots without a key and exposes no `AssistantService`.

- [ ] **Step 4: Verify the "with key" path activates (manual, one-off)**

Run:
```bash
cd backend && SPRING_AI_MODEL_CHAT=openai OPENAI_API_KEY=test-key ./gradlew test --tests '*ConversationalAiConfigTest'
```
Expected: PASS still (the test asserts absence only on the default context; this run merely proves the app *boots* with the key set — bean creation of the OpenAI client makes no network call at construction). If the context fails to start with these overrides, the OpenAI auto-config activation differs from `spring.ai.model.chat`; debug against the actual 1.1.8 starter and fix the activation property, keeping the default-off behavior.

- [ ] **Step 5: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/main/java/com/goldys/platform/conversational/ConversationalAiConfig.java backend/src/test/java/com/goldys/platform/conversational/ConversationalAiConfigTest.java
git commit -m "feat: wire conversational AI beans conditionally on the API key"
```

---

## Task 7: Permission seed + `ChatController` (SSE)

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__seed_conversational_permission.sql`
- Create: `backend/src/main/java/com/goldys/platform/conversational/ChatController.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ChatControllerTest.java`

**Interfaces:**
- Consumes: `AssistantService` (Task 6), `CurrentUserService`, `PermissionService`, `ResourceKey`, `PermissionAction`, `AccountUserDetails`, `AccessDeniedException`, `ApiExceptionHandler` (existing).
- Produces: `POST /api/conversational/chat` (SSE, named events `text`/`answer`/`error`), Owner-gated.

- [ ] **Step 1: Write the migration**

`backend/src/main/resources/db/migration/V13__seed_conversational_permission.sql`:

```sql
-- Seed the Conversational BI feature gate: only the owner (ALL x OWNER) may use
-- "Ask Goldy's". Tool-level data access stays gated by the existing
-- reconciliation.sales permission, enforced again inside every tool call.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'conversational.chat', true, true);
```

- [ ] **Step 2: Write the failing test**

`backend/src/test/java/com/goldys/platform/api/ChatControllerTest.java`:

```java
package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void deniesANonOwner() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(managerRole());
    doThrow(AccessDeniedException.forResource("conversational.chat"))
        .when(permissions)
        .require(any(), any(), any());

    mvc.perform(
            post("/api/conversational/chat")
                .with(authenticated(manager()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsNotConfiguredWhenNoAssistantServiceIsPresent() throws Exception {
    // No AssistantService bean exists in this @WebMvcTest slice, so the real
    // ObjectProvider<AssistantService> is empty and the endpoint returns 503.
    when(currentUser.roleOf(any())).thenReturn(ownerRole());

    mvc.perform(
            post("/api/conversational/chat")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
        .andExpect(status().isServiceUnavailable());
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static AccountUserDetails manager() {
    return new AccountUserDetails(UUID.randomUUID(), "m@example.com", "hash", "Manager", "BOH", "MANAGER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static UserRole managerRole() {
    return new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
```

- [ ] **Step 3: Write the controller**

`backend/src/main/java/com/goldys/platform/conversational/ChatController.java`:

```java
package com.goldys.platform.conversational;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Streams "Ask Goldy's" answers as SSE, Owner-only. */
@RestController
@RequestMapping("/api/conversational")
public class ChatController {
  private static final ResourceKey RESOURCE = new ResourceKey("conversational.chat");

  private final CurrentUserService currentUser;
  private final PermissionService permissions;
  private final ObjectProvider<AssistantService> assistant;

  public ChatController(
      CurrentUserService currentUser,
      PermissionService permissions,
      ObjectProvider<AssistantService> assistant) {
    this.currentUser = currentUser;
    this.permissions = permissions;
    this.assistant = assistant;
  }

  @PostMapping("/chat")
  ResponseEntity<SseEmitter> chat(
      @RequestBody ChatRequest request, @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    permissions.require(role, RESOURCE, PermissionAction.READ);

    AssistantService service = assistant.getIfAvailable();
    if (service == null) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    SseEmitter emitter = new SseEmitter(0L); // no idle timeout
    service
        .stream(request, role)
        .subscribe(
            event -> send(emitter, event),
            err -> send(emitter, new ConversationEvent.Error("Something went wrong.")),
            emitter::complete);
    return ResponseEntity.ok(emitter);
  }

  private static void send(SseEmitter emitter, ConversationEvent event) {
    try {
      switch (event) {
        case ConversationEvent.TextDelta(var delta) ->
            emitter.send(SseEmitter.event().name("text").data(Map.of("delta", delta)));
        case ConversationEvent.Answer(var payload) ->
            emitter.send(SseEmitter.event().name("answer").data(payload));
        case ConversationEvent.Error(var message) ->
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
      }
    } catch (IOException e) {
      emitter.completeWithError(e);
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ChatControllerTest'`
Expected: PASS — non-owner gets 403 (the `AccessDeniedException` thrown by the stubbed `PermissionService` is handled by `ApiExceptionHandler`), and the not-configured path returns 503.

- [ ] **Step 5: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/main/resources/db/migration/V13__seed_conversational_permission.sql backend/src/main/java/com/goldys/platform/conversational/ChatController.java backend/src/test/java/com/goldys/platform/api/ChatControllerTest.java
git commit -m "feat: add the Owner-gated conversational chat endpoint"
```

---

## Task 8: Backend integration test (PostgreSQL)

**Files:**
- Test: `backend/src/test/java/com/goldys/platform/conversational/ConversationalBiIntegrationTest.java`

**Interfaces:**
- Consumes: `ReportingToolCallbacks` (Task 4), `ToolDispatcher`, `ToolRegistry` (existing), `ConversationContext` (Task 3), `CanonicalDailySalesService` + `DailySalesInput` (existing canonical), `PostgresContainerConfiguration` (existing test support).

- [ ] **Step 1: Write the test**

`backend/src/test/java/com/goldys/platform/conversational/ConversationalBiIntegrationTest.java`:

```java
package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesService;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolRegistry;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ConversationalBiIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesService dailySales;
  @Autowired ToolDispatcher dispatcher;
  @Autowired ToolRegistry registry;
  @Autowired ReportingToolCallbacks callbacks;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_daily_sales, daily_sales_override, resolution_rule");
  }

  @Test
  void runsGetSalesByPeriodThroughTheCallbackToTheResolvedView() throws Exception {
    dailySales.record(
        new DailySalesInput(
            "LIGHTSPEED", SEP_13, bd("27650.66"), bd("2502.36"), bd("25148.30"), rawRecord()));
    dailySales.record(
        new DailySalesInput("CTB", SEP_13, bd("27650.66"), bd("2502.36"), bd("25148.30"), rawRecord()));

    ReportingTool tool = registry.find(com.goldys.platform.reporting.ToolId.GET_SALES_BY_PERIOD).orElseThrow();
    ConversationContext context = new ConversationContext();
    ToolCallback callback = callbacks.forTools(List.of(tool), OWNER, context, dispatcher, mapper).get(0);

    String raw =
        callback.call(
            "{\"startDate\":\"2026-09-13\",\"endDate\":\"2026-09-13\",\"metric\":\"GROSS_SALES\"}");

    assertThat(raw).contains("27650.66");
    assertThat(context.toAnswerPayload().widgets()).hasSize(1);
    assertThat(context.toAnswerPayload().widgets().get(0).type()).isEqualTo("line-chart");
    assertThat(context.toAnswerPayload().notices()).isEmpty();
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'CTB', 'test', 'SUCCESS', now(), 1, 1)",
        runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'CTB', 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId, runId, new byte[] {1}, "0".repeat(64), 1);
    return recordId;
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
```

- [ ] **Step 2: Run test to verify the assembled path**

Run: `cd backend && ./gradlew test --tests '*ConversationalBiIntegrationTest'`
Expected: PASS — seeds two agreeing sources, the callback routes through the real `ToolDispatcher` + permission seed, and `get_sales_by_period` returns the resolved `grossSales`. If it fails, the Spring wiring (tool registration, resolved-view query, or the permission seed from V13) is wrong — debug the actual failure.

- [ ] **Step 3: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`

```bash
git add backend/src/test/java/com/goldys/platform/conversational/ConversationalBiIntegrationTest.java
git commit -m "test: cover the conversational tool callback end-to-end"
```

---

## Task 9: Frontend types + SSE client + hook

**Files:**
- Create: `frontend/components/ask-goldys/types.ts`
- Create: `frontend/components/ask-goldys/stream.ts`
- Create: `frontend/components/ask-goldys/use-ask-goldys.ts`
- Test: `frontend/components/ask-goldys/stream.test.ts`

**Interfaces:**
- Consumes: the SSE contract from the backend (`text`/`answer`/`error` named events).
- Produces: `WidgetSpec`, `AnswerPayload`, `ChatMessage` types; `parseSseBlock(block)`; `streamChat(messages, handlers)`; `useAskGoldys()`. Used by Tasks 10–11.

- [ ] **Step 1: Write the failing test**

`frontend/components/ask-goldys/stream.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import { parseSseBlock } from "./stream";

describe("parseSseBlock", () => {
  it("parses a named event with JSON data", () => {
    const block = 'event: text\ndata: {"delta":"hello"}\n\n';
    expect(parseSseBlock(block)).toEqual({ event: "text", data: '{"delta":"hello"}' });
  });

  it("defaults the event name to message", () => {
    expect(parseSseBlock('data: {"a":1}\n\n')).toEqual({ event: "message", data: '{"a":1}' });
  });

  it("joins multi-line data", () => {
    expect(parseSseBlock('event: answer\ndata: {"a":1}\ndata: {"b":2}\n\n')).toEqual({
      event: "answer",
      data: '{"a":1}\n{"b":2}',
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- stream.test.ts`
Expected: FAIL — `./stream` does not exist.

- [ ] **Step 3: Write the types**

`frontend/components/ask-goldys/types.ts`:

```ts
export type WidgetType = "stat" | "table" | "line-chart" | "bar-chart";

export interface WidgetSpec {
  version: number;
  type: WidgetType;
  title: string;
  description?: string | null;
  data: Record<string, unknown>[];
}

export interface TraceEntry {
  tool: string;
  description: string;
}

export interface AnswerPayload {
  widgets: WidgetSpec[];
  trace: TraceEntry[];
  asOf: string;
  notices: string[];
}

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}
```

- [ ] **Step 4: Write the SSE client**

`frontend/components/ask-goldys/stream.ts`:

```ts
import type { AnswerPayload, ChatMessage } from "./types";

/** Parse a single `\n\n`-delimited SSE block into its event name and data string. */
export function parseSseBlock(block: string): { event: string; data: string } {
  let event = "message";
  const dataLines: string[] = [];
  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    else if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
  }
  return { event, data: dataLines.join("\n") };
}

/** The CSRF token Spring stores in a cookie, read back into the X-XSRF-TOKEN header. */
function csrfToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

interface StreamHandlers {
  onText: (delta: string) => void;
  onAnswer: (answer: AnswerPayload) => void;
  onError: (message: string) => void;
}

/**
 * POST the message history to the chat endpoint and stream the SSE response. Native
 * `fetch` + `ReadableStream` (not `EventSource`, which cannot POST).
 */
export async function streamChat(messages: ChatMessage[], handlers: StreamHandlers): Promise<void> {
  const csrf = csrfToken();
  const response = await fetch("/api/conversational/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}),
    },
    credentials: "same-origin",
    body: JSON.stringify({ messages }),
  });

  if (!response.ok) {
    let message = `Request failed (HTTP ${response.status}).`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body?.message) message = body.message;
    } catch {
      // keep the default message
    }
    handlers.onError(message);
    return;
  }

  const reader = response.body?.getReader();
  if (!reader) {
    handlers.onError("Could not read the response stream.");
    return;
  }

  const decoder = new TextDecoder();
  let buffer = "";
  const dispatch = (raw: string) => {
    const { event, data } = parseSseBlock(raw);
    if (!data) return;
    try {
      const payload = JSON.parse(data) as Record<string, unknown>;
      if (event === "text" && typeof payload.delta === "string") handlers.onText(payload.delta);
      else if (event === "answer") handlers.onAnswer(payload as unknown as AnswerPayload);
      else if (event === "error" && typeof payload.message === "string") handlers.onError(payload.message);
    } catch {
      // a malformed event is dropped, not fatal
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split("\n\n");
    buffer = blocks.pop() ?? "";
    for (const block of blocks) dispatch(block);
  }
  buffer += decoder.decode();
  if (buffer.trim()) dispatch(buffer);
}
```

- [ ] **Step 5: Write the hook**

`frontend/components/ask-goldys/use-ask-goldys.ts`:

```ts
"use client";

import { useCallback, useMemo, useState } from "react";
import { streamChat } from "./stream";
import type { AnswerPayload, ChatMessage } from "./types";

export interface UseAskGoldys {
  messages: ChatMessage[];
  summary: string;
  answer: AnswerPayload | null;
  error: string | null;
  working: boolean;
  ask: (question: string) => void;
  reset: () => void;
}

/** Drives the "Ask Goldy's" conversation: message history + the streamed answer. */
export function useAskGoldys(): UseAskGoldys {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [summary, setSummary] = useState("");
  const [answer, setAnswer] = useState<AnswerPayload | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  const ask = useCallback((question: string) => {
    const next: ChatMessage[] = [...messages, { role: "user", content: question }];
    setMessages(next);
    setSummary("");
    setAnswer(null);
    setError(null);
    setWorking(true);

    streamChat(next, {
      onText: (delta) => setSummary((s) => s + delta),
      onAnswer: (payload) => setAnswer(payload),
      onError: (message) => setError(message),
    })
      .catch(() => setError("Could not reach the server. Check your connection and try again."))
      .finally(() => setWorking(false));
  }, [messages]);

  const reset = useCallback(() => {
    setMessages([]);
    setSummary("");
    setAnswer(null);
    setError(null);
    setWorking(false);
  }, []);

  return useMemo(
    () => ({ messages, summary, answer, error, working, ask, reset }),
    [messages, summary, answer, error, working, ask, reset],
  );
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && bun run test -- stream.test.ts`
Expected: PASS.

- [ ] **Step 7: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`

```bash
git add frontend/components/ask-goldys/types.ts frontend/components/ask-goldys/stream.ts frontend/components/ask-goldys/use-ask-goldys.ts frontend/components/ask-goldys/stream.test.ts
git commit -m "feat: add the Ask Goldy's SSE client and hook"
```

---

## Task 10: Widget renderer + answer block

**Files:**
- Create: `frontend/components/ask-goldys/widget-renderer.tsx`
- Create: `frontend/components/ask-goldys/answer-block.tsx`
- Test: `frontend/components/ask-goldys/widget-renderer.test.tsx`
- Test: `frontend/components/ask-goldys/answer-block.test.tsx`

**Interfaces:**
- Consumes: `WidgetSpec`, `AnswerPayload`, `TraceEntry` (Task 9); `StatCard` (existing), Recharts.
- Produces: `WidgetRenderer({ widget })`; `AnswerBlock({ summary, answer, error })`. Used by Task 11.

- [ ] **Step 1: Write the failing tests**

`frontend/components/ask-goldys/widget-renderer.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { WidgetRenderer } from "./widget-renderer";
import type { WidgetSpec } from "./types";

describe("WidgetRenderer", () => {
  it("renders a line chart from data", () => {
    const widget: WidgetSpec = {
      version: 1,
      type: "line-chart",
      title: "Daily sales",
      description: null,
      data: [{ date: "2026-09-13", grossSales: 27650.66, source: "agreed" }],
    };
    const { container } = render(<WidgetRenderer widget={widget} />);
    expect(container.querySelector(".recharts-responsive-container")).not.toBeNull();
    expect(screen.getByText("Daily sales")).toBeInTheDocument();
  });

  it("renders a table", () => {
    const widget: WidgetSpec = {
      version: 1,
      type: "table",
      title: "Sales",
      description: null,
      data: [{ date: "2026-09-13", grossSales: 27650.66 }],
    };
    render(<WidgetRenderer widget={widget} />);
    expect(screen.getByText("date")).toBeInTheDocument();
    expect(screen.getByText("grossSales")).toBeInTheDocument();
  });

  it("renders a notice for an unknown type instead of crashing", () => {
    const widget: WidgetSpec = {
      version: 1,
      type: "pie" as WidgetSpec["type"],
      title: "Unknown",
      description: null,
      data: [],
    };
    render(<WidgetRenderer widget={widget} />);
    expect(screen.getByText(/unsupported/i)).toBeInTheDocument();
  });
});
```

`frontend/components/ask-goldys/answer-block.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AnswerBlock } from "./answer-block";
import type { AnswerPayload } from "./types";

describe("AnswerBlock", () => {
  it("shows summary, trace, as-of, and notices", () => {
    const answer: AnswerPayload = {
      widgets: [],
      trace: [{ tool: "get_sales_by_period", description: "Resolved daily sales totals." }],
      asOf: "2026-10-02T10:00:00Z",
      notices: ["1 date(s) have no resolved total (unresolved conflict)."],
    };
    render(<AnswerBlock summary="Sales were $27,650.66." answer={answer} error={null} />);
    expect(screen.getByText(/sales were/i)).toBeInTheDocument();
    expect(screen.getByText(/get_sales_by_period/i)).toBeInTheDocument();
    expect(screen.getByText(/no resolved total/i)).toBeInTheDocument();
  });

  it("shows the error message when present", () => {
    render(<AnswerBlock summary="" answer={null} error="You don't have access to that data." />);
    expect(screen.getByText(/you don't have access/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && bun run test -- widget-renderer.test.tsx answer-block.test.tsx`
Expected: FAIL — the components do not exist.

- [ ] **Step 3: Write the renderer**

`frontend/components/ask-goldys/widget-renderer.tsx`:

```tsx
"use client";

import { LineChart, Line, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from "recharts";
import { StatCard } from "@/components/dashboard/stat-card";
import { Activity } from "lucide-react";
import type { WidgetSpec } from "./types";

interface WidgetRendererProps {
  widget: WidgetSpec;
}

/** Renders a widget spec. Unknown types render a notice, never crash. */
export function WidgetRenderer({ widget }: WidgetRendererProps) {
  switch (widget.type) {
    case "line-chart":
      return <ChartFrame title={widget.title} chart={<LineSeries data={widget.data} />} />;
    case "bar-chart":
      return <ChartFrame title={widget.title} chart={<BarSeries data={widget.data} />} />;
    case "table":
      return <TableWidget title={widget.title} data={widget.data} />;
    case "stat":
      return (
        <StatCard
          label={widget.title}
          value={String(firstValue(widget.data) ?? "—")}
          icon={Activity}
        />
      );
    default:
      return <p className="text-sm text-muted-foreground">Unsupported widget type: {widget.type}</p>;
  }
}

function ChartFrame({ title, chart }: { title: string; chart: React.ReactNode }) {
  return (
    <div className="rounded-lg border bg-card p-4">
      <p className="text-sm font-medium">{title}</p>
      <div className="mt-3 h-48">{chart}</div>
    </div>
  );
}

function LineSeries({ data }: { data: Record<string, unknown>[] }) {
  const keys = numericKeys(data);
  const xKey = xKeyOf(data);
  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey={xKey} />
        <YAxis />
        <Tooltip />
        {keys.map((k) => (
          <Line key={k} type="monotone" dataKey={k} stroke="#2563eb" />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}

function BarSeries({ data }: { data: Record<string, unknown>[] }) {
  const keys = numericKeys(data);
  const xKey = xKeyOf(data);
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey={xKey} />
        <YAxis />
        <Tooltip />
        {keys.map((k) => (
          <Bar key={k} dataKey={k} fill="#2563eb" />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}

function TableWidget({ title, data }: { title: string; data: Record<string, unknown>[] }) {
  const columns = columnKeys(data);
  return (
    <div className="rounded-lg border bg-card p-4">
      <p className="text-sm font-medium">{title}</p>
      <div className="mt-3 overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c} className="border-b px-2 py-1 text-left font-medium">
                  {c}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.map((row, i) => (
              <tr key={i}>
                {columns.map((c) => (
                  <td key={c} className="border-b px-2 py-1">
                    {String(row[c] ?? "")}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function numericKeys(data: Record<string, unknown>[]): string[] {
  const keys = columnKeys(data);
  return keys.filter((k) => k !== xKeyOf(data) && data.some((r) => typeof r[k] === "number"));
}

function xKeyOf(data: Record<string, unknown>[]): string {
  return "date" in (data[0] ?? {}) ? "date" : (columnKeys(data)[0] ?? "index");
}

function columnKeys(data: Record<string, unknown>[]): string[] {
  const set = new Set<string>();
  for (const row of data) for (const k of Object.keys(row)) set.add(k);
  return [...set];
}

function firstValue(data: Record<string, unknown>[]): unknown {
  const row = data[0];
  if (!row) return null;
  for (const v of Object.values(row)) if (typeof v === "number") return v;
  return Object.values(row)[0] ?? null;
}
```

- [ ] **Step 4: Write the answer block**

`frontend/components/ask-goldys/answer-block.tsx`:

```tsx
"use client";

import { PermissionDenied } from "@/components/states/permission-denied";
import { WidgetRenderer } from "./widget-renderer";
import type { AnswerPayload } from "./types";

interface AnswerBlockProps {
  summary: string;
  answer: AnswerPayload | null;
  error: string | null;
}

/** The answer anatomy: summary + widgets + "How I got this" trace + "as of" + notices. */
export function AnswerBlock({ summary, answer, error }: AnswerBlockProps) {
  if (error) {
    return <PermissionDenied subject="this data" message={error} />;
  }
  return (
    <div className="space-y-4">
      {summary ? <p className="text-sm">{summary}</p> : null}

      {answer?.notices.length ? (
        <p className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {answer.notices.join(" ")}{" "}
          <a href="/reconciliation" className="underline">
            Reconcile these first.
          </a>
        </p>
      ) : null}

      {answer?.widgets.map((w, i) => (
        <WidgetRenderer key={i} widget={w} />
      ))}

      {answer?.trace.length ? (
        <details className="text-xs text-muted-foreground">
          <summary className="cursor-pointer underline">How I got this</summary>
          <div className="mt-1 space-y-1">
            {answer.trace.map((t, i) => (
              <p key={i}>
                {t.tool} — {t.description}
              </p>
            ))}
            <p>As of {answer.asOf}</p>
          </div>
        </details>
      ) : null}
    </div>
  );
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd frontend && bun run test -- widget-renderer.test.tsx answer-block.test.tsx`
Expected: PASS.

- [ ] **Step 6: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`

```bash
git add frontend/components/ask-goldys/widget-renderer.tsx frontend/components/ask-goldys/answer-block.tsx frontend/components/ask-goldys/widget-renderer.test.tsx frontend/components/ask-goldys/answer-block.test.tsx
git commit -m "feat: render Ask Goldy's widgets and answer anatomy"
```

---

## Task 11: The "Ask Goldy's" drawer + header button

**Files:**
- Create: `frontend/components/ask-goldys/ask-goldys-drawer.tsx`
- Modify: `frontend/components/app-shell/app-header.tsx`
- Test: `frontend/components/ask-goldys/ask-goldys-drawer.test.tsx`

**Interfaces:**
- Consumes: `useAskGoldys` (Task 9), `AnswerBlock` (Task 10), `Sheet*` (existing `@/components/ui/sheet`), `useCurrentUser` (existing).
- Produces: `AskGoldysDrawer({ seniority })` — fail-closed on non-Owner; renders the global drawer.

- [ ] **Step 1: Write the failing test**

`frontend/components/ask-goldys/ask-goldys-drawer.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AskGoldysDrawer } from "./ask-goldys-drawer";

describe("AskGoldysDrawer", () => {
  it("shows the Ask button for an Owner", () => {
    render(<AskGoldysDrawer seniority="Owner" />);
    expect(screen.getByRole("button", { name: /ask/i })).toBeInTheDocument();
  });

  it("hides itself for a non-Owner", () => {
    render(<AskGoldysDrawer seniority="Manager" />);
    expect(screen.queryByRole("button", { name: /ask/i })).not.toBeInTheDocument();
  });

  it("fails closed when seniority is absent", () => {
    render(<AskGoldysDrawer />);
    expect(screen.queryByRole("button", { name: /ask/i })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- ask-goldys-drawer.test.tsx`
Expected: FAIL — `AskGoldysDrawer` does not exist.

- [ ] **Step 3: Write the drawer**

`frontend/components/ask-goldys/ask-goldys-drawer.tsx`:

```tsx
"use client";

import { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAskGoldys } from "./use-ask-goldys";
import { AnswerBlock } from "./answer-block";

interface AskGoldysDrawerProps {
  seniority?: string;
}

const SUGGESTIONS = [
  "What were sales last week?",
  "Show sales for the last 7 days",
  "How much did we sell yesterday?",
];

/** The global "Ask Goldy's" drawer. Fail-closed: renders nothing for non-Owners. */
export function AskGoldysDrawer({ seniority }: AskGoldysDrawerProps) {
  const isOwner = seniority === "Owner";
  const [open, setOpen] = useState(false);
  const { messages, summary, answer, error, working, ask, reset } = useAskGoldys();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  if (!isOwner) return null;

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm" className="gap-1.5">
          <Sparkles className="h-4 w-4" aria-hidden="true" />
          Ask Goldy&apos;s
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="flex w-full flex-col gap-4 sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Ask Goldy&apos;s</SheetTitle>
        </SheetHeader>

        <div className="flex-1 space-y-4 overflow-y-auto">
          {messages.length === 0 && !summary ? (
            <div className="space-y-2">
              <p className="text-sm text-muted-foreground">Ask a reporting question.</p>
              {SUGGESTIONS.map((s) => (
                <button
                  key={s}
                  onClick={() => ask(s)}
                  className="block w-full rounded-md border px-3 py-2 text-left text-sm hover:bg-muted"
                >
                  {s}
                </button>
              ))}
            </div>
          ) : null}

          {working ? <p className="text-sm text-muted-foreground">Working…</p> : null}

          {summary || answer || error ? (
            <AnswerBlock summary={summary} answer={answer} error={error} />
          ) : null}
        </div>

        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            const input = e.currentTarget.elements.namedItem("question") as HTMLInputElement;
            const q = input.value.trim();
            if (q) ask(q);
            input.value = "";
          }}
        >
          <Input name="question" placeholder="e.g. What were sales last week?" />
          <Button type="submit" disabled={working}>
            Ask
          </Button>
        </form>
      </SheetContent>
    </Sheet>
  );
}
```

Note: the trace uses a native `<details>/<summary>` element (no new dependency), so the "How I got this" content is in the DOM and visible to tests even when collapsed.

- [ ] **Step 4: Wire the button into the header**

Replace `frontend/components/app-shell/app-header.tsx` with:

```tsx
"use client";

import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { AskGoldysDrawer } from "@/components/ask-goldys/ask-goldys-drawer";
import { useCurrentUser } from "./current-user-provider";

export function AppHeader() {
  const { user } = useCurrentUser();
  return (
    <header className="flex h-16 shrink-0 items-center gap-2 border-b px-4">
      <SidebarTrigger className="-ml-1" />
      <Separator orientation="vertical" className="mr-2 h-4" />
      <span className="text-sm font-medium">Goldy&apos;s Data Platform</span>
      <div className="ml-auto">
        <AskGoldysDrawer seniority={user?.seniority} />
      </div>
    </header>
  );
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && bun run test -- ask-goldys-drawer.test.tsx`
Expected: PASS.

- [ ] **Step 6: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`

```bash
git add frontend/components/ask-goldys/ask-goldys-drawer.tsx frontend/components/app-shell/app-header.tsx frontend/components/ask-goldys/ask-goldys-drawer.test.tsx
git commit -m "feat: add the Ask Goldy's drawer and header entry point"
```

---

## Task 12: Full verification pass

**Files:**
- None (verification only).

- [ ] **Step 1: Run the full backend gate**

Run: `cd backend && ./gradlew test spotlessCheck build`
Expected: all pass.

- [ ] **Step 2: Run the full frontend gate**

Run: `cd frontend && bun run typecheck && bun run lint && bun run test`
Expected: all pass.

- [ ] **Step 3: Commit any fixes**

If a check surfaced a fix, commit it atomically with a `fix:` message; otherwise there is nothing to commit.
