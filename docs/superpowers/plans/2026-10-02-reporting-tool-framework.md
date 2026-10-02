# Reporting Tool Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the reusable reporting-tool framework (PRD Requirement 11 scaffolding) plus the first fixed tool `get_sales_by_period`, with no LLM — tools are invoked directly and tested end-to-end.

**Architecture:** A new `com.goldys.platform.reporting` package. `ReportingTool` is a contract (`id`/`name`/`description`/`execute`) implemented by typed tools whose inputs are records of structured values and enums (never free text). A `ToolRegistry` + `ToolDispatcher` route a `ToolId` + `ToolInput` through `PermissionService` (`reconciliation.sales` READ) to the tool, which returns a `ToolResult(WidgetSpec, notices)`. `get_sales_by_period` reads `DailySalesReconciliationService.resolved(date)` and emits a `line-chart` widget.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA, PostgreSQL 16 (Testcontainers), AssertJ + Mockito + JUnit 5, Gradle (committed wrapper) + Spotless.

**Spec:** `docs/superpowers/specs/2026-10-02-reporting-tool-framework-design.md`

## Global Constraints

- Java 25; run everything with the committed Gradle wrapper (`./gradlew`), never a system Gradle.
- No model invocation, no Spring AI in this slice. Tools are invoked directly.
- Tools read **resolved views only** — never raw or canonical tables directly.
- No freeform query, no SQL, no free-text field/filter parameters — inputs are typed records + enums.
- Permission: reuse `PermissionService` with `ResourceKey("reconciliation.sales")` and `PermissionAction.READ`; denial is `AccessDeniedException`, never a partial result.
- Widget specs conform to `widget-spec.schema.json` v1 (`stat`/`table`/`line-chart`/`bar-chart`); never executable code.
- Verification gates: `./gradlew test`, `./gradlew spotlessCheck`, `./gradlew build`.
- Atomic Conventional Commits. Never commit to main — branch from the latest `main`.

## Review Focus

These are the input classes / failure modes the spec implies but whose happy-path tests would not otherwise exercise. Each is pinned by a test in the named task.

1. **An unresolved date in the range** — must appear in `notices`, never silently dropped or presented as a resolved value. → Task 2.
2. **A date with no data at all** (`resolved()` returns empty) — treated as unresolved, surfaced in `notices`. → Task 2.
3. **`endDate` before `startDate`** — the input record must reject it. → Task 2.
4. **An unknown `ToolId`** — the dispatcher throws a clear error, not an NPE or a silent no-op. → Task 3.
5. **A denied role** — the dispatcher throws `AccessDeniedException`, never a filtered result. → Task 3.

---

## Task 1: Value types (ToolId, Metric, WidgetSpec, ToolResult)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/Metric.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/WidgetSpec.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/ToolResult.java`
- Test: `backend/src/test/java/com/goldys/platform/reporting/WidgetSpecTest.java`

**Interfaces:**
- Produces: `ToolId` enum (`GET_SALES_BY_PERIOD`); `Metric` enum (`GROSS_SALES`); `WidgetSpec(int version, String type, String title, String description, List<Map<String,Object>> data)`; `ToolResult(WidgetSpec widget, List<String> notices)`. Used by Tasks 2–4.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/reporting/WidgetSpecTest.java`:

```java
package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WidgetSpecTest {

  @Test
  void acceptsAValidLineChart() {
    WidgetSpec spec =
        new WidgetSpec(
            1,
            "line-chart",
            "Daily sales",
            "Resolved gross sales per day.",
            List.of(Map.of("date", "2026-09-13", "grossSales", "27650.66")));

    assertThat(spec.version()).isEqualTo(1);
    assertThat(spec.type()).isEqualTo("line-chart");
    assertThat(spec.data()).hasSize(1);
  }

  @Test
  void rejectsAnUnknownWidgetType() {
    assertThatThrownBy(() -> new WidgetSpec(1, "pie", "Sales", null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("widget type");
  }

  @Test
  void rejectsAnUnsupportedVersion() {
    assertThatThrownBy(() -> new WidgetSpec(2, "stat", "Sales", null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*WidgetSpecTest'`
Expected: FAIL — `WidgetSpec` does not exist.

- [ ] **Step 3: Write the value types**

`backend/src/main/java/com/goldys/platform/reporting/ToolId.java`:

```java
package com.goldys.platform.reporting;

/** Stable identifiers for the fixed reporting tools. */
public enum ToolId {
  GET_SALES_BY_PERIOD
}
```

`backend/src/main/java/com/goldys/platform/reporting/Metric.java`:

```java
package com.goldys.platform.reporting;

/** A metric a reporting tool can return. Only gross sales is resolved today. */
public enum Metric {
  GROSS_SALES
}
```

`backend/src/main/java/com/goldys/platform/reporting/WidgetSpec.java`:

```java
package com.goldys.platform.reporting;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A widget spec conforming to widget-spec.schema.json version 1. */
public record WidgetSpec(
    int version, String type, String title, String description, List<Map<String, Object>> data) {
  private static final List<String> TYPES = List.of("stat", "table", "line-chart", "bar-chart");

  public WidgetSpec {
    if (version != 1) {
      throw new IllegalArgumentException("Unsupported widget version: " + version);
    }
    if (type == null || !TYPES.contains(type)) {
      throw new IllegalArgumentException("Unknown widget type: " + type);
    }
    Objects.requireNonNull(title, "title");
    data = data == null ? List.of() : List.copyOf(data);
  }
}
```

`backend/src/main/java/com/goldys/platform/reporting/ToolResult.java`:

```java
package com.goldys.platform.reporting;

import java.util.List;

/** A tool's result: a widget spec plus any notices the caller must surface. */
public record ToolResult(WidgetSpec widget, List<String> notices) {
  public ToolResult {
    Objects.requireNonNull(widget, "widget");
    notices = notices == null ? List.of() : List.copyOf(notices);
  }
}
```

(Add `import java.util.Objects;` to `ToolResult.java`.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*WidgetSpecTest'`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`
Then:

```bash
git add backend/src/main/java/com/goldys/platform/reporting/ backend/src/test/java/com/goldys/platform/reporting/WidgetSpecTest.java
git commit -m "feat: add reporting tool value types"
```

---

## Task 2: Tool contract + `get_sales_by_period`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reporting/ToolInput.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/ReportingTool.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodInput.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodTool.java`
- Test: `backend/src/test/java/com/goldys/platform/reporting/GetSalesByPeriodToolTest.java`

**Interfaces:**
- Consumes: `ToolId`, `Metric`, `WidgetSpec`, `ToolResult` (Task 1); `DailySalesReconciliationService.resolved(LocalDate)` and `DailySalesResolved` (existing, in `com.goldys.platform.reconciliation`); `UserRole` (existing auth).
- Produces: `ToolInput` (marker interface); `ReportingTool` (`id()`, `name()`, `description()`, `execute(ToolInput, UserRole)`); `GetSalesByPeriodInput(LocalDate, LocalDate, Metric)`; `GetSalesByPeriodTool`. Used by Tasks 3–4.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/reporting/GetSalesByPeriodToolTest.java`:

```java
package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import com.goldys.platform.reconciliation.DailySalesResolved;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetSalesByPeriodToolTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void emitsResolvedPointsAndNoticesUnresolvedDates() {
    DailySalesReconciliationService reconciliation = mock(DailySalesReconciliationService.class);
    when(reconciliation.resolved(SEP_13))
        .thenReturn(Optional.of(new DailySalesResolved(SEP_13, new BigDecimal("27650.66"), "agreed")));
    when(reconciliation.resolved(SEP_14))
        .thenReturn(Optional.of(new DailySalesResolved(SEP_14, null, null)));

    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(reconciliation);
    ToolResult result =
        tool.execute(new GetSalesByPeriodInput(SEP_13, SEP_14, Metric.GROSS_SALES), OWNER);

    assertThat(result.widget().type()).isEqualTo("line-chart");
    assertThat(result.widget().data()).hasSize(1);
    assertThat(result.widget().data().get(0).get("date")).isEqualTo("2026-09-13");
    assertThat(result.notices()).hasSize(1);
    assertThat(result.notices().get(0)).contains("no resolved total");
  }

  @Test
  void treatsMissingDataAsUnresolved() {
    DailySalesReconciliationService reconciliation = mock(DailySalesReconciliationService.class);
    when(reconciliation.resolved(SEP_13)).thenReturn(Optional.empty());

    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(reconciliation);
    ToolResult result =
        tool.execute(new GetSalesByPeriodInput(SEP_13, SEP_13, Metric.GROSS_SALES), OWNER);

    assertThat(result.widget().data()).isEmpty();
    assertThat(result.notices()).hasSize(1);
  }

  @Test
  void rejectsEndDateBeforeStartDate() {
    assertThatThrownBy(
            () -> new GetSalesByPeriodInput(SEP_14, SEP_13, Metric.GROSS_SALES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startDate");
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*GetSalesByPeriodToolTest'`
Expected: FAIL — `GetSalesByPeriodTool` / `GetSalesByPeriodInput` do not exist.

- [ ] **Step 3: Write the contract and input**

`backend/src/main/java/com/goldys/platform/reporting/ToolInput.java`:

```java
package com.goldys.platform.reporting;

/** Marker interface for a tool's typed input. */
public interface ToolInput {}
```

`backend/src/main/java/com/goldys/platform/reporting/ReportingTool.java`:

```java
package com.goldys.platform.reporting;

import com.goldys.platform.auth.UserRole;

/** A fixed reporting tool: an LLM-facing name/description plus an execution path. */
public interface ReportingTool {
  ToolId id();

  String name();

  String description();

  ToolResult execute(ToolInput input, UserRole role);
}
```

`backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodInput.java`:

```java
package com.goldys.platform.reporting;

import java.time.LocalDate;
import java.util.Objects;

/** Input for {@link ToolId#GET_SALES_BY_PERIOD}. */
public record GetSalesByPeriodInput(LocalDate startDate, LocalDate endDate, Metric metric)
    implements ToolInput {
  public GetSalesByPeriodInput {
    Objects.requireNonNull(startDate, "startDate");
    Objects.requireNonNull(endDate, "endDate");
    Objects.requireNonNull(metric, "metric");
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("endDate is before startDate");
    }
  }
}
```

- [ ] **Step 4: Write the tool**

`backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodTool.java`:

```java
package com.goldys.platform.reporting;

import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import com.goldys.platform.reconciliation.DailySalesResolved;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolved daily gross sales for a date range, emitted as a line-chart widget. */
@Component
public class GetSalesByPeriodTool implements ReportingTool {
  private final DailySalesReconciliationService reconciliation;

  public GetSalesByPeriodTool(DailySalesReconciliationService reconciliation) {
    this.reconciliation = reconciliation;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_SALES_BY_PERIOD;
  }

  @Override
  public String name() {
    return "get_sales_by_period";
  }

  @Override
  public String description() {
    return "Resolved daily sales totals for a date range.";
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    GetSalesByPeriodInput in = (GetSalesByPeriodInput) input;
    List<Map<String, Object>> points = new ArrayList<>();
    List<LocalDate> unresolved = new ArrayList<>();
    for (LocalDate date = in.startDate(); !date.isAfter(in.endDate()); date = date.plusDays(1)) {
      reconciliation
          .resolved(date)
          .ifPresentOrElse(
              r -> {
                if (r.resolvedTotal() == null) {
                  unresolved.add(date);
                } else {
                  points.add(point(date, r));
                }
              },
              () -> unresolved.add(date));
    }
    List<String> notices =
        unresolved.isEmpty()
            ? List.of()
            : List.of(unresolved.size() + " date(s) have no resolved total (unresolved conflict).");
    WidgetSpec widget =
        new WidgetSpec(1, "line-chart", "Daily sales", "Resolved gross sales per day.", points);
    return new ToolResult(widget, notices);
  }

  private static Map<String, Object> point(LocalDate date, DailySalesResolved r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("date", date.toString());
    m.put("grossSales", r.resolvedTotal());
    m.put("source", r.authoritativeSource());
    return m;
  }
}
```

Note: `metric` is carried on the input but the tool does not branch on it — only `GROSS_SALES` exists (the resolved view has a single metric), and the enum is the forward-compatible extension point.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*GetSalesByPeriodToolTest'`
Expected: PASS.

- [ ] **Step 6: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`
Then:

```bash
git add backend/src/main/java/com/goldys/platform/reporting/ backend/src/test/java/com/goldys/platform/reporting/GetSalesByPeriodToolTest.java
git commit -m "feat: add get_sales_by_period reporting tool"
```

---

## Task 3: Registry and dispatcher

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reporting/ToolRegistry.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/ToolDispatcher.java`
- Test: `backend/src/test/java/com/goldys/platform/reporting/ToolDispatcherTest.java`

**Interfaces:**
- Consumes: `ReportingTool`, `ToolInput`, `ToolId`, `ToolResult` (Task 2); `PermissionService`/`ResourceKey`/`PermissionAction`/`UserRole` (existing).
- Produces: `ToolRegistry` (`find(ToolId): Optional<ReportingTool>`); `ToolDispatcher` (`dispatch(ToolId, ToolInput, UserRole): ToolResult`). Used by Task 4.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/reporting/ToolDispatcherTest.java`:

```java
package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolDispatcherTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private static ToolResult okResult() {
    return new ToolResult(new WidgetSpec(1, "stat", "Sales", null, List.of()), List.of());
  }

  private static ReportingTool tool(ToolId id) {
    ReportingTool t = mock(ReportingTool.class);
    when(t.id()).thenReturn(id);
    return t;
  }

  @Test
  void dispatchesToTheRegisteredTool() {
    ReportingTool t = tool(ToolId.GET_SALES_BY_PERIOD);
    when(t.execute(any(), any())).thenReturn(okResult());
    PermissionService permissions = mock(PermissionService.class);
    ToolDispatcher dispatcher = new ToolDispatcher(new ToolRegistry(List.of(t)), permissions);

    ToolResult result = dispatcher.dispatch(ToolId.GET_SALES_BY_PERIOD, mock(ToolInput.class), OWNER);

    assertThat(result.widget().type()).isEqualTo("stat");
    verify(permissions)
        .require(OWNER, new ResourceKey("reconciliation.sales"), PermissionAction.READ);
  }

  @Test
  void rejectsAnUnknownTool() {
    ToolDispatcher dispatcher =
        new ToolDispatcher(new ToolRegistry(List.of()), mock(PermissionService.class));

    assertThatThrownBy(
            () -> dispatcher.dispatch(ToolId.GET_SALES_BY_PERIOD, mock(ToolInput.class), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown tool");
  }

  @Test
  void deniesAnUnauthorizedRole() {
    ReportingTool t = tool(ToolId.GET_SALES_BY_PERIOD);
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());
    ToolDispatcher dispatcher = new ToolDispatcher(new ToolRegistry(List.of(t)), permissions);

    assertThatThrownBy(
            () -> dispatcher.dispatch(ToolId.GET_SALES_BY_PERIOD, mock(ToolInput.class), OWNER))
        .isInstanceOf(AccessDeniedException.class);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*ToolDispatcherTest'`
Expected: FAIL — `ToolRegistry` / `ToolDispatcher` do not exist.

- [ ] **Step 3: Write the registry**

`backend/src/main/java/com/goldys/platform/reporting/ToolRegistry.java`:

```java
package com.goldys.platform.reporting;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The fixed set of reporting tools, keyed by {@link ToolId}. */
@Component
public class ToolRegistry {
  private final Map<ToolId, ReportingTool> tools;

  public ToolRegistry(List<ReportingTool> tools) {
    this.tools =
        tools.stream()
            .collect(Collectors.toUnmodifiableMap(ReportingTool::id, Function.identity()));
  }

  public Optional<ReportingTool> find(ToolId id) {
    return Optional.ofNullable(tools.get(id));
  }
}
```

- [ ] **Step 4: Write the dispatcher**

`backend/src/main/java/com/goldys/platform/reporting/ToolDispatcher.java`:

```java
package com.goldys.platform.reporting;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import org.springframework.stereotype.Component;

/** The single entry point for tool calls: route, authorize, execute. */
@Component
public class ToolDispatcher {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final ToolRegistry registry;
  private final PermissionService permissions;

  public ToolDispatcher(ToolRegistry registry, PermissionService permissions) {
    this.registry = registry;
    this.permissions = permissions;
  }

  public ToolResult dispatch(ToolId id, ToolInput input, UserRole role) {
    ReportingTool tool =
        registry
            .find(id)
            .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + id));
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return tool.execute(input, role);
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ToolDispatcherTest'`
Expected: PASS.

- [ ] **Step 6: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`
Then:

```bash
git add backend/src/main/java/com/goldys/platform/reporting/ backend/src/test/java/com/goldys/platform/reporting/ToolDispatcherTest.java
git commit -m "feat: add reporting tool registry and dispatcher"
```

---

## Task 4: Integration test (PostgreSQL)

**Files:**
- Test: `backend/src/test/java/com/goldys/platform/reporting/ReportingToolIntegrationTest.java`

**Interfaces:**
- Consumes: `ToolDispatcher` (Task 3), `GetSalesByPeriodInput` (Task 2), `CanonicalDailySalesService` + `DailySalesInput` (existing canonical), `PostgresContainerConfiguration` (existing test support).

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/reporting/ReportingToolIntegrationTest.java`:

```java
package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesService;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ReportingToolIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesService dailySales;
  @Autowired ToolDispatcher dispatcher;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_daily_sales, daily_sales_override, resolution_rule");
  }

  @Test
  void returnsTheResolvedTotalEndToEnd() {
    // Two sources agree on 13 Sep, so the resolved total is that value.
    dailySales.record(
        new DailySalesInput(
            "LIGHTSPEED", SEP_13, bd("27650.66"), bd("2502.36"), bd("25148.30"), rawRecord()));
    dailySales.record(
        new DailySalesInput(
            "CTB", SEP_13, bd("27650.66"), bd("2502.36"), bd("25148.30"), rawRecord()));

    ToolResult result =
        dispatcher.dispatch(
            ToolId.GET_SALES_BY_PERIOD,
            new GetSalesByPeriodInput(SEP_13, SEP_13, Metric.GROSS_SALES),
            OWNER);

    assertThat(result.widget().type()).isEqualTo("line-chart");
    assertThat(result.widget().data()).hasSize(1);
    assertThat(result.widget().data().get(0).get("grossSales")).isEqualTo(new BigDecimal("27650.66"));
    assertThat(result.notices()).isEmpty();
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
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
```

- [ ] **Step 2: Run the test to verify the assembled path**

Run: `cd backend && ./gradlew test --tests '*ReportingToolIntegrationTest'`
Expected: PASS. This is a verification test — the tool, dispatcher, and framework already exist from Tasks 1–3, so it does not go RED first. If it fails, the Spring wiring (tool bean registration, resolved-view query, or permission seed) is wrong — debug the actual failure, don't patch the test.

- [ ] **Step 3: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ReportingToolIntegrationTest'`
Expected: PASS (seeds two agreeing sources, the dispatcher authorizes the OWNER role, and `get_sales_by_period` returns the resolved `grossSales`).

- [ ] **Step 4: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`
Then:

```bash
git add backend/src/test/java/com/goldys/platform/reporting/ReportingToolIntegrationTest.java
git commit -m "test: cover the reporting tool end-to-end"
```

---

## Task 5: Full verification pass

**Files:**
- None (verification only).

- [ ] **Step 1: Run the full backend gate**

Run: `cd backend && ./gradlew test spotlessCheck build`
Expected: all pass.

- [ ] **Step 2: Commit any fixes**

If a check surfaced a fix, commit it atomically with a `fix:` message; otherwise there is nothing to commit.
