# Ask Goldy's Analytical Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand "Ask Goldy's" from single-shot metric retrieval into multi-step analytical reasoning over the semantic layer — a 10-tool catalogue over composable primitives, server-side conversation persistence with bounded context, per-metric authorization, richer tool trace, dashboard draft create+update, and a two-tier eval suite — without introducing SQL or arbitrary expressions.

**Architecture:** Extend the existing Spring AI `ChatClient` loop (no agent harness). Analytical primitives live in `semantic` (a leaf); tools stay thin adapters in `reporting` over `MetricQueryService` + primitives; `conversational` gains a `ConversationService` (orchestration-agnostic persistence/summarization/bounded-rounds). `ToolDispatcher` remains the single authorize+execute choke point, now authorizing per-metric before execution.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring AI 1.1.x, PostgreSQL 16 (Flyway), JUnit 5 + AssertJ + Mockito + Testcontainers; Next.js + TypeScript + Vitest (Bun).

**Spec:** `docs/superpowers/specs/2026-10-07-ask-goldys-analytical-expansion-design.md` — the plan argues from the spec; executors read both.

## Global Constraints

- No SQL tool, no free-text field/filter/expression parameter. All tool inputs are typed records with enum fields (`MetricId`, `Dimension`, `TimeGrain`, `Comparison`) + `LocalDate` ranges.
- Tools read resolved projections via `MetricQueryService` / semantic query interfaces only — never `canonical`/`reconciliation`/`ingestion` directly (the `ArchitectureBoundariesTest` rule for `reporting` is `semantic` + `auth` only).
- `semantic` stays a leaf (no in-platform dependencies). `application` implements new `semantic` interfaces; `ingestion`/`reconciliation` keep implementing `semantic` interfaces.
- `conversational` may own a *conversation* store but never reaches `canonical`/`reconciliation`/`ingestion`.
- Authorization happens **before** execution, in `ToolDispatcher`, via `PermissionService.require(role, ResourceKey, PermissionAction)`.
- Permission seeds are OWNER-only (`ALL` × `OWNER`), matching the V6/V13/V23 convention.
- British spelling: `labour` (not `labor`) everywhere, matching the codebase.
- Every metric result carries `MetricProvenance`; missing data is surfaced via `missingPeriods`/notices, never silently zeroed.
- `ToolResult` must carry `provenance`; the trace must expose metrics/ranges/freshness/unresolved periods, never chain-of-thought or internal prompts.
- Spring AI is pinned to the 1.1.x line (`ToolCallback` / `FunctionToolCallback` API).

## Review Focus

The five inputs/failure modes the spec implies but no task's tests would otherwise pin (each gets a test in the named task):

1. **A date range with zero resolved data** (or `endDate` before `startDate`) — must yield an explicit validation error or an empty result + notice, never a fabricated or zeroed value. → Task 7 (`get_metric` input validation).
2. **A metric queried at an unsupported grain/dimension** (e.g. `inventory.stock_on_hand` at WEEK, or a dimension not in the metric's `validDimensions`) — must surface a structured error from `MetricQueryServiceImpl`, not a wrong result. → Task 7 (`get_metric` forwards catalogue validation).
3. **Cross-domain derived metric** (e.g. `inventory.food_cost_percent` reads `sales.gross`) — the per-metric gate authorizes the declared metric's permission, and the union-of-operands gap is documented, not silently widened. → Task 5 (dispatcher authorizes the declared metric; test asserts `inventory.cost` is required).
4. **Client-supplied assistant message / another user's `threadId`** — the server ignores client assistant messages and scopes threads to the authenticated user (cross-user read → denied/not-found). → Task 16 (thread endpoints).
5. **A tool result missing provenance** — every data tool must populate `ToolResult.provenance`; an empty provenance degrades the trace silently. → Task 4 (contract: `toMetricQueries` + `provenance` together) and each tool task asserts non-empty provenance.

---

## Task 1: Metric relatedness in the catalogue (`semantic`)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricCatalog.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/MetricCatalogTest.java`

**Interfaces:**
- Produces: `MetricCatalog.related(MetricId id) → Set<MetricId>` (curated, empty when none).

- [ ] **Step 1: Write the failing test**

Add to `MetricCatalogTest`:

```java
@Test
void relatedMetricsPointToExplanatoryPeers() {
  MetricCatalog catalog = new MetricCatalog();

  assertThat(catalog.related(MetricId.SALES_GROSS))
      .contains(
          MetricId.RESERVATIONS_COVERS,
          MetricId.SALES_AVERAGE_SPEND_PER_COVER,
          MetricId.PRODUCT_SALES_AMOUNT);
  assertThat(catalog.related(MetricId.RESERVATIONS_COVERS))
      .contains(MetricId.RESERVATIONS_BOOKINGS, MetricId.SALES_AVERAGE_SPEND_PER_COVER);
  assertThat(catalog.related(MetricId.SALES_GST)).isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*MetricCatalogTest*'`
Expected: FAIL — `cannot find symbol: method related(MetricId)`.

- [ ] **Step 3: Implement `related`**

In `MetricCatalog`, add a curated static map and the accessor (import `java.util.Map`, `java.util.Set` already present):

```java
private static final Map<MetricId, Set<MetricId>> RELATED =
    Map.of(
        MetricId.SALES_GROSS,
            Set.of(
                MetricId.RESERVATIONS_COVERS,
                MetricId.SALES_AVERAGE_SPEND_PER_COVER,
                MetricId.PRODUCT_SALES_AMOUNT,
                MetricId.LABOUR_FOH_PERCENT,
                MetricId.LABOUR_BOH_PERCENT,
                MetricId.INVENTORY_FOOD_COST_PERCENT),
        MetricId.RESERVATIONS_COVERS,
            Set.of(
                MetricId.RESERVATIONS_BOOKINGS,
                MetricId.RESERVATIONS_ATTENDED,
                MetricId.RESERVATIONS_NO_SHOWS,
                MetricId.SALES_AVERAGE_SPEND_PER_COVER),
        MetricId.RESERVATIONS_BOOKINGS,
            Set.of(
                MetricId.RESERVATIONS_ATTENDED,
                MetricId.RESERVATIONS_COVERS,
                MetricId.RESERVATIONS_NO_SHOW_RATE),
        MetricId.LABOUR_COST,
            Set.of(
                MetricId.LABOUR_SCHEDULED_HOURS,
                MetricId.LABOUR_ACTUAL_HOURS,
                MetricId.LABOUR_HOURS_VARIANCE,
                MetricId.LABOUR_FOH_PERCENT,
                MetricId.LABOUR_BOH_PERCENT),
        MetricId.INVENTORY_PURCHASES,
            Set.of(MetricId.INVENTORY_WASTAGE, MetricId.INVENTORY_FOOD_COST_PERCENT),
        MetricId.PRODUCT_SALES_AMOUNT,
            Set.of(MetricId.PRODUCT_SALES_QUANTITY, MetricId.PRODUCT_TOP_SELLERS));

/** The metrics a result's provenance points to, so the model can chain a "why" question. */
public Set<MetricId> related(MetricId id) {
  return RELATED.getOrDefault(id, Set.of());
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*MetricCatalogTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/semantic/catalog/MetricCatalog.java backend/src/test/java/com/goldys/platform/semantic/catalog/MetricCatalogTest.java
git commit -m "feat(semantic): curated metric relatedness for multi-step reasoning"
```

---

## Task 2: `RankingService` (rank groups) in `semantic`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/RankingService.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/RankingServiceTest.java`

**Interfaces:**
- Consumes: `ProductMetricsQuery.topSellers(from, to, limit) → List<TopSeller>`, `LabourMetricsQuery.dailyLabour(from, to) → List<LabourMetric>`.
- Produces: `RankedListResult rank(MetricId metric, Dimension dimension, TimeRange range, int limit)`.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.ProductMetricsQuery;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankingServiceTest {

  private final ProductMetricsQuery products = mock(ProductMetricsQuery.class);
  private final LabourMetricsQuery labour = mock(LabourMetricsQuery.class);
  private final RankingService service = new RankingService(products, labour);

  @Test
  void rejectsAnUnsupportedMetricDimensionPair() {
    assertThatThrownBy(
            () ->
                service.rank(
                    MetricId.SALES_GROSS,
                    Dimension.PRODUCT,
                    new TimeRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), Calendar.CALENDAR),
                    5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no ranked read");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*RankingServiceTest*'`
Expected: FAIL — `cannot find symbol: class RankingService`.

- [ ] **Step 3: Implement `RankingService`**

```java
package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.TopSeller;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Ranks the groups of a dimension by a metric, for the (metric, dimension) pairs that have a grouped
 * read. Ranked-list results are the composable "rank groups" primitive; {@code get_top_products} and
 * {@code rank_dimension} are thin adapters over this.
 */
@Component
public class RankingService {
  private final ProductMetricsQuery products;
  private final LabourMetricsQuery labour;

  public RankingService(ProductMetricsQuery products, LabourMetricsQuery labour) {
    this.products = products;
    this.labour = labour;
  }

  public RankedListResult rank(
      MetricId metric, Dimension dimension, TimeRange range, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    if (metric == MetricId.PRODUCT_SALES_AMOUNT && dimension == Dimension.PRODUCT) {
      return productRank(range, limit);
    }
    if ((metric == MetricId.LABOUR_COST
            || metric == MetricId.LABOUR_ACTUAL_HOURS
            || metric == MetricId.LABOUR_SCHEDULED_HOURS)
        && dimension == Dimension.DEPARTMENT) {
      return labourRank(metric, range, limit);
    }
    throw new IllegalArgumentException(
        "no ranked read for metric " + metric.value() + " by " + dimension);
  }

  private RankedListResult productRank(TimeRange range, int limit) {
    List<MetricRankedItem> items =
        products.topSellers(range.from(), range.to(), limit).stream()
            .map(
                t ->
                    new MetricRankedItem(
                        t.productName(), t.amount(), t.quantitySold(), t.hasConflict()))
            .toList();
    return new RankedListResult(
        MetricId.PRODUCT_SALES_AMOUNT, items, List.of(), provenance(MetricId.PRODUCT_SALES_AMOUNT, range));
  }

  private RankedListResult labourRank(MetricId metric, TimeRange range, int limit) {
    Map<String, BigDecimal> totals = new LinkedHashMap<>();
    for (LabourMetric m : labour.dailyLabour(range.from(), range.to())) {
      BigDecimal v = pick(metric, m);
      if (v != null) {
        totals.merge(m.department(), v, BigDecimal::add);
      }
    }
    List<MetricRankedItem> items =
        totals.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(limit)
            .map(e -> new MetricRankedItem(e.getKey(), e.getValue(), null, false))
            .toList();
    return new RankedListResult(metric, items, List.of(), provenance(metric, range));
  }

  private static BigDecimal pick(MetricId metric, LabourMetric m) {
    return switch (metric) {
      case LABOUR_COST -> m.actualCost();
      case LABOUR_ACTUAL_HOURS -> m.actualHours();
      case LABOUR_SCHEDULED_HOURS -> m.scheduledHours();
      default -> throw new IllegalArgumentException("Not a labour metric: " + metric);
    };
  }

  private MetricProvenance provenance(MetricId metric, TimeRange range) {
    return new MetricProvenance(
        metric, "1", range, TimeGrain.DAY, "derived", Instant.EPOCH, List.of(), "1");
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*RankingServiceTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/semantic/catalog/RankingService.java backend/src/test/java/com/goldys/platform/semantic/catalog/RankingServiceTest.java
git commit -m "feat(semantic): RankingService rank-groups primitive"
```

---

## Task 3: `ConnectorHealthQuery` seam (`semantic` interface + `application` impl)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/ConnectorHealth.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/ConnectorHealthQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/application/ConnectorHealthService.java`
- Test: `backend/src/test/java/com/goldys/platform/application/ConnectorHealthServiceTest.java`

**Interfaces:**
- Consumes: `IngestionService.latestRunPerSource() → List<IngestionRunSummary>` (record with `String sourceSystem()`, `Instant startedAt()`, `String status()`).
- Produces: `ConnectorHealthQuery.health() → List<ConnectorHealth>` where `record ConnectorHealth(String source, Instant lastRunAt, String status)`.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.ingestion.IngestionRunSummary;
import com.goldys.platform.ingestion.IngestionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorHealthServiceTest {

  @Test
  void mapsLatestRunsToHealth() {
    IngestionService ingestion = mock(IngestionService.class);
    when(ingestion.latestRunPerSource())
        .thenReturn(List.of(new IngestionRunSummary(
            "OPENTABLE", "opentable-csv-drop", "SUCCESS", Instant.parse("2026-10-06T10:00:00Z"), "0", null)));
    ConnectorHealthService service = new ConnectorHealthService(ingestion);

    var health = service.health();

    assertThat(health).hasSize(1);
    assertThat(health.get(0).source()).isEqualTo("OPENTABLE");
    assertThat(health.get(0).status()).isEqualTo("SUCCESS");
    assertThat(health.get(0).lastRunAt()).isEqualTo(Instant.parse("2026-10-06T10:00:00Z"));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*ConnectorHealthServiceTest*'`
Expected: FAIL — `cannot find symbol: class ConnectorHealthService`.

- [ ] **Step 3: Implement the seam**

`semantic/ConnectorHealth.java`:

```java
package com.goldys.platform.semantic;

import java.time.Instant;

/** Per-source connector freshness, exposed to the semantic layer (PASS 9 extends this). */
public record ConnectorHealth(String source, Instant lastRunAt, String status) {}
```

`semantic/ConnectorHealthQuery.java`:

```java
package com.goldys.platform.semantic;

import java.util.List;

/** Business read over connector freshness. Implemented in {@code application} from the ingestion ledger. */
public interface ConnectorHealthQuery {
  List<ConnectorHealth> health();
}
```

`application/ConnectorHealthService.java`:

```java
package com.goldys.platform.application;

import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import java.util.List;
import org.springframework.stereotype.Service;

/** Implements the semantic {@link ConnectorHealthQuery} over the ingestion ledger. */
@Service
public class ConnectorHealthService implements ConnectorHealthQuery {
  private final IngestionService ingestion;

  public ConnectorHealthService(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @Override
  public List<ConnectorHealth> health() {
    return ingestion.latestRunPerSource().stream()
        .map(r -> new ConnectorHealth(r.sourceSystem(), r.startedAt(), r.status()))
        .toList();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*ConnectorHealthServiceTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/semantic/ConnectorHealth.java backend/src/main/java/com/goldys/platform/semantic/ConnectorHealthQuery.java backend/src/main/java/com/goldys/platform/application/ConnectorHealthService.java backend/src/test/java/com/goldys/platform/application/ConnectorHealthServiceTest.java
git commit -m "feat(semantic): ConnectorHealthQuery seam for connector freshness"
```

---

## Task 4: Result envelope — `ToolResult`/`TraceEntry` carry provenance

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolResult.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ReportingTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/conversational/AnswerPayload.java`
- Modify: `backend/src/main/java/com/goldys/platform/conversational/ConversationContext.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodTool.java`, `GetReservationSummaryTool.java`, `GetLabourCostTool.java`, `GetFoodCostTool.java` (each: populate provenance)
- Test: update `backend/src/test/java/com/goldys/platform/reporting/ToolDispatcherTest.java` (`okResult()`), `backend/src/test/java/com/goldys/platform/conversational/ReportingToolCallbacksTest.java`

**Interfaces:**
- Consumes: `MetricResult.provenance() → MetricProvenance`.
- Produces: `record ToolResult(WidgetSpec widget, List<String> notices, List<MetricProvenance> provenance)`; `record TraceEntry(String tool, String description, List<MetricProvenance> provenance)`; `ReportingTool.toMetricQueries` default returns `List.of()`.

- [ ] **Step 1: Write the failing test**

In `ToolDispatcherTest`, change `okResult()` to the 3-arg form and add a provenance assertion helper:

```java
private static ToolResult okResult() {
  return new ToolResult(
      new StatWidgetSpec("id", "Sales", null, null, null, null, null), List.of(), List.of());
}
```

In `ReportingToolCallbacksTest`, add:

```java
@Test
void outcomeIncludesProvenance() {
  // (existing callback test setup) ...
  Map<String, Object> outcome = outcome(true, resultWithProvenance());
  assertThat(outcome).containsKey("provenance");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*ToolDispatcherTest*' --tests '*ReportingToolCallbacksTest*'`
Expected: FAIL — constructor mismatch (2 vs 3 args).

- [ ] **Step 3: Implement the envelope**

`ToolResult.java`:

```java
package com.goldys.platform.reporting;

import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.widget.WidgetSpec;
import java.util.List;
import java.util.Objects;

/** A tool's result: a widget spec, notices, and the metric provenance backing it. */
public record ToolResult(
    WidgetSpec widget, List<String> notices, List<MetricProvenance> provenance) {
  public ToolResult {
    Objects.requireNonNull(widget, "widget");
    notices = notices == null ? List.of() : List.copyOf(notices);
    provenance = provenance == null ? List.of() : List.copyOf(provenance);
  }
}
```

`ReportingTool.java` — change the `toMetricQueries` default from `throw` to empty:

```java
default List<com.goldys.platform.semantic.catalog.MetricQuery> toMetricQueries(ToolInput input) {
  return List.of();
}
```

`AnswerPayload.java` — change `TraceEntry`:

```java
public record TraceEntry(
    String tool, String description, List<MetricProvenance> provenance) {}
```

`ConversationContext.record(...)` — pass provenance into the trace entry:

```java
public void record(ReportingTool tool, ToolResult result) {
  trace.add(new AnswerPayload.TraceEntry(tool.name(), tool.description(), result.provenance()));
  widgets.add(result.widget());
  notices.addAll(result.notices());
}
```

In each of the four data tools, change the `execute` return to include provenance:

```java
return new ToolResult(
    widget,
    results.stream().flatMap(r -> r.notices().stream()).toList(),
    results.stream().map(MetricResult::provenance).toList());
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*ToolDispatcherTest*' --tests '*ReportingToolCallbacksTest*' --tests '*GetSalesByPeriodToolTest*' --tests '*GetReservationSummaryToolTest*' --tests '*GetLabourCostToolTest*' --tests '*GetFoodCostToolTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reporting backend/src/main/java/com/goldys/platform/conversational backend/src/test/java/com/goldys/platform/reporting backend/src/test/java/com/goldys/platform/conversational
git commit -m "feat(reporting): ToolResult and trace carry metric provenance"
```

---

## Task 5: `ToolDispatcher` per-metric authorization

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolDispatcher.java`
- Test: `backend/src/test/java/com/goldys/platform/reporting/ToolDispatcherTest.java`

**Interfaces:**
- Consumes: `MetricCatalog.definition(metric).requiredPermission()`, `ReportingTool.toMetricQueries(input)`.
- Produces: unchanged `ToolResult dispatch(ToolId, ToolInput, UserRole)`.

- [ ] **Step 1: Write the failing test**

Add to `ToolDispatcherTest`:

```java
@Test
void authorizesEachMetricBeforeExecution() {
  ReportingTool t = mock(ReportingTool.class);
  when(t.id()).thenReturn(ToolId.GET_SALES_BY_PERIOD);
  when(t.resource()).thenReturn(new ResourceKey("conversational.chat")); // capability (general tool)
  when(t.toMetricQueries(any())).thenReturn(List.of(
      new MetricQuery(MetricId.SALES_GROSS,
          new TimeRange(LocalDate.of(2026,1,1), LocalDate.of(2026,1,2), Calendar.CALENDAR),
          TimeGrain.DAY, Set.of(), null)));
  when(t.execute(any(), any())).thenReturn(okResult());
  PermissionService permissions = mock(PermissionService.class);
  ToolDispatcher dispatcher = new ToolDispatcher(
      new ToolRegistry(List.of(t)), permissions, new MetricCatalog(), mock(OperationalMetrics.class));

  dispatcher.dispatch(ToolId.GET_SALES_BY_PERIOD, mock(ToolInput.class), OWNER);

  // capability gate ...
  verify(permissions).require(OWNER, new ResourceKey("conversational.chat"), PermissionAction.READ);
  // ... and the per-metric gate (sales.gross -> reconciliation.sales), both before execution.
  verify(permissions).require(OWNER, new ResourceKey("reconciliation.sales"), PermissionAction.READ);
}
```

(Imports to add to `ToolDispatcherTest`: `java.time.LocalDate`, `java.util.Set`, and `com.goldys.platform.semantic.catalog.{MetricCatalog, MetricId, MetricQuery, TimeGrain, TimeRange, Calendar}`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*ToolDispatcherTest*'`
Expected: FAIL — `verify` finds no `require(OWNER, reconciliation.sales, READ)` (the per-metric gate is not yet wired; only the capability gate runs).

- [ ] **Step 3: Implement per-metric authorization**

```java
public ToolResult dispatch(ToolId id, ToolInput input, UserRole role) {
  ReportingTool tool =
      registry.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + id));
  permissions.require(role, tool.resource(), PermissionAction.READ);
  // Per-metric data gate: authorize every metric this tool will read, before execution.
  for (MetricQuery q : tool.toMetricQueries(input)) {
    String required = catalog.definition(q.metric()).requiredPermission();
    permissions.require(role, new ResourceKey(required), PermissionAction.READ);
  }
  Timer.Sample sample = metrics.start();
  try {
    return tool.execute(input, role);
  } catch (RuntimeException e) {
    metrics.toolFailure(id.name());
    throw e;
  } finally {
    metrics.stopTool(sample, id.name());
  }
}
```

Add the `MetricCatalog` field/constructor parameter to `ToolDispatcher`. Then update the other `ToolDispatcherTest` methods (`authorizesUsingTheToolsDeclaredResource`, `dispatchesToTheRegisteredTool`, `rejectsAnUnknownTool`, `deniesAnUnauthorizedRole`) to pass `new MetricCatalog()` as the new argument, since the constructor arity changed.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*ToolDispatcherTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reporting/ToolDispatcher.java backend/src/test/java/com/goldys/platform/reporting/ToolDispatcherTest.java
git commit -m "feat(reporting): ToolDispatcher authorizes each metric before execution"
```

---

## Task 6: Permission seeds (`reconciliation.status`, `conversational.threads`)

**Files:**
- Create: `backend/src/main/resources/db/migration/V26__seed_ask_goldys_permissions.sql`
- Test: `backend/src/test/java/com/goldys/platform/auth/PermissionSeedTest.java` (or extend an existing seed test if one exists)

**Interfaces:**
- Consumes: `permission` table schema (V6/V13/V23 convention).
- Produces: rows granting `(ALL, OWNER)` read/write on `reconciliation.status` and `conversational.threads`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void askGoldysPermissionsAreSeededForOwner() {
  // query permission where resource in ('reconciliation.status','conversational.threads')
  assertThat(rows).extracting("department", "seniority", "canRead", "canWrite")
      .containsOnly(tuple("ALL", "OWNER", true, true));
}
```

(Follow the existing seed test's query pattern; if none exists, assert via a repository/JdbcTemplate query in a Testcontainers context.)

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — no rows for the new resources.

- [ ] **Step 3: Add the migration**

```sql
-- Owner-only grants for the new Ask Goldy's surfaces (matching V6/V13/V23: ALL x OWNER).
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'reconciliation.status', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'conversational.threads', true, true);
```

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V26__seed_ask_goldys_permissions.sql backend/src/test/java/com/goldys/platform/auth/PermissionSeedTest.java
git commit -m "feat(auth): seed reconciliation.status and conversational.threads for owner"
```

---

## Task 7: `get_metric` tool

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetMetricInput.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetMetricTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` (add `GET_METRIC`)
- Test: `backend/src/test/java/com/goldys/platform/reporting/GetMetricToolTest.java`

**Interfaces:**
- Consumes: `MetricQueryService.query`, `MetricCatalog.related`.
- Produces: `get_metric` tool; input `record GetMetricInput(MetricId metric, LocalDate startDate, LocalDate endDate, TimeGrain grain, Set<Dimension> dimensions)`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void getMetricRendersAndCarriesProvenanceAndRelated() {
  // mock MetricQueryService to return a TimeSeriesResult for SALES_GROSS
  ToolResult result = new GetMetricTool(metrics, renderer, catalog).execute(
      new GetMetricInput(MetricId.SALES_GROSS, LocalDate.of(2026,1,1), LocalDate.of(2026,1,2), TimeGrain.DAY, Set.of()),
      OWNER);
  assertThat(result.provenance()).isNotEmpty();
  assertThat(result.widget()).isInstanceOf(TimeSeriesWidgetSpec.class);
}

@Test
void rejectsBackwardsDateRange() {
  assertThatThrownBy(() -> new GetMetricInput(MetricId.SALES_GROSS, LocalDate.of(2026,1,2), LocalDate.of(2026,1,1), TimeGrain.DAY, Set.of()))
      .isInstanceOf(IllegalArgumentException.class);
}

@Test
void forwardsCatalogueValidationForUnsupportedDimension() {
  // sales.gross declares no dimensions; the query service rejects SERVICE_PERIOD.
  when(metrics.query(any())).thenThrow(new IllegalArgumentException("dimensions [SERVICE_PERIOD] are not allowed for sales.gross"));
  GetMetricTool tool = new GetMetricTool(metrics, renderer, catalog);
  assertThatThrownBy(() -> tool.execute(
      new GetMetricInput(MetricId.SALES_GROSS, LocalDate.of(2026,1,1), LocalDate.of(2026,1,2), TimeGrain.DAY, Set.of(Dimension.SERVICE_PERIOD)),
      OWNER))
      .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — `GetMetricTool`/`GetMetricInput` not found.

- [ ] **Step 3: Implement**

`GetMetricInput.java` (validate `endDate >= startDate`, non-null metric/grain; `dimensions` default empty):

```java
package com.goldys.platform.reporting;

import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.TimeGrain;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/** Input for {@link ToolId#GET_METRIC}. */
public record GetMetricInput(
    MetricId metric, LocalDate startDate, LocalDate endDate, TimeGrain grain, Set<Dimension> dimensions)
    implements ToolInput {
  public GetMetricInput {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(startDate, "startDate");
    Objects.requireNonNull(endDate, "endDate");
    Objects.requireNonNull(grain, "grain");
    dimensions = dimensions == null ? Set.of() : Set.copyOf(dimensions);
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("endDate is before startDate");
    }
  }
}
```

`GetMetricTool.java` (render type derived from the result kind: `RankedListResult → ranked-list`, else `time-series`):

```java
package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.RankedListResult;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.widget.WidgetSpec;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The general single-metric tool: query any catalogue metric and render it. */
@Component
public class GetMetricTool implements ReportingTool {
  private final MetricQueryService metrics;
  private final WidgetRenderer renderer;
  private final MetricCatalog catalog;

  public GetMetricTool(MetricQueryService metrics, WidgetRenderer renderer, MetricCatalog catalog) {
    this.metrics = metrics;
    this.renderer = renderer;
    this.catalog = catalog;
  }

  @Override
  public ToolId id() { return ToolId.GET_METRIC; }

  @Override
  public String name() { return "get_metric"; }

  @Override
  public String description() {
    return "Query a single metric (by dotted id) over a date range and grain, with optional "
        + "dimensions. Returns the series/ranked list plus the metric's related metrics.";
  }

  @Override
  public Class<? extends ToolInput> inputType() { return GetMetricInput.class; }

  @Override
  public ResourceKey resource() { return new ResourceKey("conversational.chat"); }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetMetricInput in)) {
      throw new IllegalArgumentException("Expected GetMetricInput, got " + input.getClass().getSimpleName());
    }
    List<MetricResult> results = toMetricQueries(in).stream().map(metrics::query).toList();
    MetricResult r = results.get(0);
    String type = r instanceof RankedListResult ? "ranked-list" : "time-series";
    WidgetSpec widget = renderer.render(UUID.randomUUID().toString(), type, results);
    return new ToolResult(widget, r.notices(), List.of(r.provenance()));
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    GetMetricInput in = (GetMetricInput) input;
    return List.of(new MetricQuery(in.metric(), new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR), in.grain(), in.dimensions(), null));
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*GetMetricToolTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reporting/GetMetricInput.java backend/src/main/java/com/goldys/platform/reporting/GetMetricTool.java backend/src/main/java/com/goldys/platform/reporting/ToolId.java backend/src/test/java/com/goldys/platform/reporting/GetMetricToolTest.java
git commit -m "feat(reporting): get_metric general single-metric tool"
```

---

## Task 8: `compare_metric_periods` tool

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reporting/CompareMetricPeriodsInput.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/CompareMetricPeriodsTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` (add `COMPARE_METRIC_PERIODS`)
- Test: `backend/src/test/java/com/goldys/platform/reporting/CompareMetricPeriodsToolTest.java`

**Interfaces:**
- Consumes: `ComparisonService.referenceRange(MetricQuery)`, `MetricQueryService.query`.
- Produces: input `record CompareMetricPeriodsInput(MetricId metric, LocalDate startDate, LocalDate endDate, Comparison comparison, TimeGrain grain)`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void compareRendersTwoSeriesAndDelta() {
  // mock MetricQueryService: current → series A, reference → series B
  ToolResult result = tool.execute(
      new CompareMetricPeriodsInput(MetricId.SALES_GROSS, LocalDate.of(2026,1,8), LocalDate.of(2026,1,14), Comparison.PREVIOUS_WEEK, TimeGrain.DAY),
      OWNER);
  assertThat(result.provenance()).hasSize(2);
  assertThat(result.notices()).anyMatch(n -> n.contains("vs"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — `CompareMetricPeriodsTool` not found.

- [ ] **Step 3: Implement**

`CompareMetricPeriodsInput.java` — validate range + non-null metric/comparison/grain (reject `BUDGET`/`FORECAST` in the tool, not the record). `CompareMetricPeriodsTool`:

```java
@Override
public ToolResult execute(ToolInput input, UserRole role) {
  CompareMetricPeriodsInput in = (CompareMetricPeriodsInput) input;
  if (in.comparison() == Comparison.BUDGET || in.comparison() == Comparison.FORECAST) {
    throw new IllegalArgumentException("no data source yet for " + in.comparison());
  }
  TimeRange range = new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR);
  MetricQuery current = new MetricQuery(in.metric(), range, in.grain(), Set.of(), null);
  TimeRange reference = comparisonService.referenceRange(current);
  MetricQuery refQuery = new MetricQuery(in.metric(), reference, in.grain(), Set.of(), null);
  MetricResult cur = metrics.query(current);
  MetricResult ref = metrics.query(reference);
  BigDecimal delta = ComparisonService.deltaPercent(totalOf((TimeSeriesResult) cur), totalOf((TimeSeriesResult) ref));
  WidgetSpec widget = renderer.render(UUID.randomUUID().toString(), "bar-chart", List.of(cur, ref));
  return new ToolResult(widget,
      List.of("vs " + in.comparison() + (delta == null ? "" : ": " + delta.movePointRight(2).setScale(1, RoundingMode.HALF_UP) + "%")),
      List.of(cur.provenance(), ref.provenance()));
}

@Override
public List<MetricQuery> toMetricQueries(ToolInput input) {
  CompareMetricPeriodsInput in = (CompareMetricPeriodsInput) input;
  return List.of(new MetricQuery(in.metric(), new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR), in.grain(), Set.of(), in.comparison()));
}
```

(Add a private `totalOf(TimeSeriesResult)` summing non-null points; keep it consistent with `WidgetRenderer.total`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*CompareMetricPeriodsToolTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reporting/CompareMetricPeriodsInput.java backend/src/main/java/com/goldys/platform/reporting/CompareMetricPeriodsTool.java backend/src/main/java/com/goldys/platform/reporting/ToolId.java backend/src/test/java/com/goldys/platform/reporting/CompareMetricPeriodsToolTest.java
git commit -m "feat(reporting): compare_metric_periods tool"
```

---

## Task 9: `rank_dimension` tool

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reporting/RankDimensionInput.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/RankDimensionTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` (add `RANK_DIMENSION`)
- Test: `backend/src/test/java/com/goldys/platform/reporting/RankDimensionToolTest.java`

**Interfaces:**
- Consumes: `RankingService.rank`.
- Produces: input `record RankDimensionInput(MetricId metric, Dimension dimension, LocalDate startDate, LocalDate endDate, int limit)`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void rankDimensionDelegatesToRankingService() {
  RankedListResult ranked = mock(RankedListResult.class);
  when(ranked.metric()).thenReturn(MetricId.PRODUCT_SALES_AMOUNT);
  when(ranked.items()).thenReturn(List.of());
  when(ranked.notices()).thenReturn(List.of());
  when(ranked.provenance()).thenReturn(provenance());
  when(ranking.rank(any(), any(), any(), anyInt())).thenReturn(ranked);

  ToolResult result = tool.execute(
      new RankDimensionInput(MetricId.PRODUCT_SALES_AMOUNT, Dimension.PRODUCT, LocalDate.of(2026,1,1), LocalDate.of(2026,1,7), 10),
      OWNER);

  assertThat(result.widget()).isInstanceOf(RankedListWidgetSpec.class);
  verify(ranking).rank(MetricId.PRODUCT_SALES_AMOUNT, Dimension.PRODUCT, any(TimeRange.class), 10);
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — `RankDimensionTool` not found.

- [ ] **Step 3: Implement**

`RankDimensionInput.java` validates range and `limit > 0`. `RankDimensionTool`:

```java
@Override
public ToolResult execute(ToolInput input, UserRole role) {
  RankDimensionInput in = (RankDimensionInput) input;
  RankedListResult ranked = ranking.rank(in.metric(), in.dimension(),
      new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR), in.limit());
  WidgetSpec widget = renderer.render(UUID.randomUUID().toString(), "ranked-list", List.of(ranked));
  return new ToolResult(widget, ranked.notices(), List.of(ranked.provenance()));
}

@Override
public List<MetricQuery> toMetricQueries(ToolInput input) {
  RankDimensionInput in = (RankDimensionInput) input;
  return List.of(new MetricQuery(in.metric(), new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR), TimeGrain.DAY, Set.of(in.dimension()), null));
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*RankDimensionToolTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reporting/RankDimensionInput.java backend/src/main/java/com/goldys/platform/reporting/RankDimensionTool.java backend/src/main/java/com/goldys/platform/reporting/ToolId.java backend/src/test/java/com/goldys/platform/reporting/RankDimensionToolTest.java
git commit -m "feat(reporting): rank_dimension tool"
```

---

## Task 10: `get_top_products` tool

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetTopProductsInput.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetTopProductsTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` (add `GET_TOP_PRODUCTS`)
- Test: `backend/src/test/java/com/goldys/platform/reporting/GetTopProductsToolTest.java`

**Interfaces:**
- Consumes: `RankingService.rank(PRODUCT_SALES_AMOUNT, PRODUCT, range, limit)`.
- Produces: input `record GetTopProductsInput(LocalDate startDate, LocalDate endDate, int limit)`; capability resource `reconciliation.sales`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void topProductsRequiresSalesPermission() {
  assertThat(new GetTopProductsTool(ranking, renderer).resource()).isEqualTo(new ResourceKey("reconciliation.sales"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — class not found.

- [ ] **Step 3: Implement**

`GetTopProductsTool` mirrors `RankDimensionTool` but hardcodes `MetricId.PRODUCT_SALES_AMOUNT` × `Dimension.PRODUCT`, resource `reconciliation.sales`, and `toMetricQueries` returns `PRODUCT_SALES_AMOUNT` (for per-metric auth):

```java
@Override
public ToolResult execute(ToolInput input, UserRole role) {
  GetTopProductsInput in = (GetTopProductsInput) input;
  RankedListResult ranked = ranking.rank(MetricId.PRODUCT_SALES_AMOUNT, Dimension.PRODUCT,
      new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR), in.limit());
  WidgetSpec widget = renderer.render(UUID.randomUUID().toString(), "ranked-list", List.of(ranked));
  return new ToolResult(widget, ranked.notices(), List.of(ranked.provenance()));
}
```

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reporting/GetTopProductsInput.java backend/src/main/java/com/goldys/platform/reporting/GetTopProductsTool.java backend/src/main/java/com/goldys/platform/reporting/ToolId.java backend/src/test/java/com/goldys/platform/reporting/GetTopProductsToolTest.java
git commit -m "feat(reporting): get_top_products tool"
```

---

## Task 11: Rename `get_labour_cost` → `get_labour_variance` (and broaden)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` (rename `GET_LABOUR_COST` → `GET_LABOUR_VARIANCE`)
- Rename: `GetLabourCostTool.java` → `GetLabourVarianceTool.java`, `GetLabourCostInput.java` → `GetLabourVarianceInput.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ReportingToolCallbacks.java` (no change needed — it's tool-name-agnostic)
- Test: rename/update `GetLabourCostToolTest.java` → `GetLabourVarianceToolTest.java`; update `ToolDispatcherTest.java` (references `GET_LABOUR_COST`)

**Interfaces:**
- Produces: `get_labour_variance` tool returning scheduled/actual hours, cost, variance, and FOH/BOH %.

- [ ] **Step 1: Update the test**

Rename `GetLabourCostToolTest` → `GetLabourVarianceToolTest`; assert the tool name is `"get_labour_variance"` and that `toMetricQueries` now includes `LABOUR_FOH_PERCENT` and `LABOUR_BOH_PERCENT`:

```java
assertThat(tool.name()).isEqualTo("get_labour_variance");
assertThat(tool.toMetricQueries(input)).extracting(MetricQuery::metric)
    .contains(MetricId.LABOUR_FOH_PERCENT, MetricId.LABOUR_BOH_PERCENT);
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — `GET_LABOUR_COST` no longer exists / name mismatch.

- [ ] **Step 3: Rename and broaden**

- `ToolId`: `GET_LABOUR_VARIANCE`.
- `GetLabourVarianceTool`: `name() = "get_labour_variance"`, `description()` mentions variance and FOH/BOH %; `toMetricQueries` returns `LABOUR_SCHEDULED_HOURS, LABOUR_ACTUAL_HOURS, LABOUR_COST, LABOUR_HOURS_VARIANCE, LABOUR_FOH_PERCENT, LABOUR_BOH_PERCENT`.
- `GetLabourVarianceInput` unchanged fields (`startDate`, `endDate`).
- Update `ToolDispatcherTest` line 43/51 from `GET_LABOUR_COST` to `GET_LABOUR_VARIANCE`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*GetLabourVarianceToolTest*' --tests '*ToolDispatcherTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A backend/src/main/java/com/goldys/platform/reporting backend/src/test/java/com/goldys/platform/reporting
git commit -m "feat(reporting): rename get_labour_cost to get_labour_variance and broaden to FOH/BOH %"
```

---

## Task 12: Rename `get_food_cost` → `get_inventory_summary` (and broaden)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` (rename `GET_FOOD_COST` → `GET_INVENTORY_SUMMARY`)
- Rename: `GetFoodCostTool.java` → `GetInventorySummaryTool.java`, `GetFoodCostInput.java` → `GetInventorySummaryInput.java`
- Test: rename/update `GetFoodCostToolTest.java` → `GetInventorySummaryToolTest.java`

**Interfaces:**
- Produces: `get_inventory_summary` tool returning purchases (COGS), wastage, and food-cost %.

- [ ] **Step 1: Update the test**

Rename the test and assert `toMetricQueries` includes `INVENTORY_FOOD_COST_PERCENT`:

```java
assertThat(tool.name()).isEqualTo("get_inventory_summary");
assertThat(tool.toMetricQueries(input)).extracting(MetricQuery::metric)
    .contains(MetricId.INVENTORY_PURCHASES, MetricId.INVENTORY_WASTAGE, MetricId.INVENTORY_FOOD_COST_PERCENT);
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — name/method mismatch.

- [ ] **Step 3: Rename and broaden**

`GetInventorySummaryTool`: `name() = "get_inventory_summary"`; `toMetricQueries` returns `INVENTORY_PURCHASES, INVENTORY_WASTAGE, INVENTORY_FOOD_COST_PERCENT`; resource `inventory.cost`.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A backend/src/main/java/com/goldys/platform/reporting backend/src/test/java/com/goldys/platform/reporting
git commit -m "feat(reporting): rename get_food_cost to get_inventory_summary and add food cost %"
```

---

## Task 13: `get_reconciliation_status` tool

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetReconciliationStatusInput.java`
- Create: `backend/src/main/java/com/goldys/platform/reporting/GetReconciliationStatusTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` (add `GET_RECONCILIATION_STATUS`)
- Test: `backend/src/test/java/com/goldys/platform/reporting/GetReconciliationStatusToolTest.java`

**Interfaces:**
- Consumes: `SalesMetricsQuery.openConflicts()`, `ProductMetricsQuery.openConflicts()`, `MetricQueryService.query` (for `missingPeriods`), `ConnectorHealthQuery.health()`.
- Produces: input `record GetReconciliationStatusInput()`; a `TableWidgetSpec` of domain/status rows + connector freshness; capability resource `reconciliation.status`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void statusReportsDomainsAndConnectors() {
  // stub SalesMetricsQuery/ProductMetricsQuery openConflicts, MetricQueryService (missingPeriods), ConnectorHealthQuery
  ToolResult result = tool.execute(new GetReconciliationStatusInput(), OWNER);
  assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
  assertThat(result.provenance()).isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — class not found.

- [ ] **Step 3: Implement**

`GetReconciliationStatusTool` (no metric data returned → `toMetricQueries` returns empty; build a `TableWidgetSpec` directly):

```java
@Override
public ToolResult execute(ToolInput input, UserRole role) {
  long salesConflicts = sales.openConflicts();
  long productConflicts = product.openConflicts();
  List<Map<String, Object>> rows = new ArrayList<>();
  rows.add(domainRow("Sales", salesConflicts));
  rows.add(domainRow("Product", productConflicts));
  for (ConnectorHealth c : connectors.health()) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("domain", c.source());
    r.put("status", "last run " + c.lastRunAt() + " (" + c.status() + ")");
    rows.add(r);
  }
  List<Column> columns = List.of(new Column("domain", "Domain", null), new Column("status", "Status", null));
  TableWidgetSpec widget = new TableWidgetSpec(UUID.randomUUID().toString(), "Reconciliation status", null, columns, rows, null);
  return new ToolResult(widget, List.of(), List.of());
}
```

(For unresolved periods per domain, query the representative metrics via `MetricQueryService` over `[today-28, today]` and count `provenance().missingPeriods()`; add those as `rows` too. Keep the metric VALUES out of the widget — only counts.)

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reporting/GetReconciliationStatusInput.java backend/src/main/java/com/goldys/platform/reporting/GetReconciliationStatusTool.java backend/src/main/java/com/goldys/platform/reporting/ToolId.java backend/src/test/java/com/goldys/platform/reporting/GetReconciliationStatusToolTest.java
git commit -m "feat(reporting): get_reconciliation_status tool"
```

---

## Task 14: Conversation tables + entities + repositories

**Files:**
- Create: `backend/src/main/resources/db/migration/V27__conversation_threads.sql`
- Create: `backend/src/main/java/com/goldys/platform/conversational/ConversationThread.java`
- Create: `backend/src/main/java/com/goldys/platform/conversational/ConversationMessage.java`
- Create: `backend/src/main/java/com/goldys/platform/conversational/ConversationThreadRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/conversational/ConversationMessageRepository.java`
- Test: `backend/src/test/java/com/goldys/platform/conversational/ConversationRepositoryTest.java`

**Interfaces:**
- Consumes: `user_account(id uuid)`; JPA conventions from the `dashboard` package.
- Produces: `ConversationThreadRepository` (findByUserAccountIdOrderByUpdatedAtDesc, findByIdAndUserAccountId, deleteById) and `ConversationMessageRepository` (findByThreadIdOrderByCreatedAtAsc).

- [ ] **Step 1: Write the failing test**

A Testcontainers integration test asserting `save`/`find` round-trips a thread + message with a JSON `tool_trace`.

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — table not found.

- [ ] **Step 3: Implement**

`V27__conversation_threads.sql`:

```sql
CREATE TABLE conversation_thread (
    id uuid PRIMARY KEY,
    user_account_id uuid NOT NULL,
    title varchar(200) NOT NULL,
    summary text,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    last_message_at timestamp(6) with time zone,
    CONSTRAINT conversation_thread_user_fk FOREIGN KEY (user_account_id) REFERENCES user_account (id)
);

CREATE INDEX idx_conversation_thread_user ON conversation_thread (user_account_id, updated_at DESC);

CREATE TABLE conversation_message (
    id uuid PRIMARY KEY,
    thread_id uuid NOT NULL,
    role varchar(16) NOT NULL,
    content text NOT NULL,
    tool_trace jsonb,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT conversation_message_thread_fk FOREIGN KEY (thread_id) REFERENCES conversation_thread (id) ON DELETE CASCADE
);

CREATE INDEX idx_conversation_message_thread ON conversation_message (thread_id, created_at ASC);
```

Entities follow `SavedDashboard`'s `jakarta.persistence` style (`@Entity`, `@Table`, `@Id UUID`, `@Column`, `@JdbcTypeCode(SqlTypes.JSON)` for `tool_trace`). `ConversationMessage.toolTrace` is `List<AnswerPayload.TraceEntry>` (or `JsonNode` if you prefer to defer typed parsing — choose `List<TraceEntry>` and map via Jackson).

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V27__conversation_threads.sql backend/src/main/java/com/goldys/platform/conversational backend/src/test/java/com/goldys/platform/conversational/ConversationRepositoryTest.java
git commit -m "feat(conversational): conversation thread/message persistence"
```

---

## Task 15: `ConversationService` (context build + persist + CRUD + summarization)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/conversational/ConversationService.java`
- Test: `backend/src/test/java/com/goldys/platform/conversational/ConversationServiceTest.java`

**Interfaces:**
- Consumes: the repositories from Task 14; a `ChatModel` for summarization.
- Produces:
  - `List<Message> contextFor(UUID userId, UUID threadId, String message)` — load thread (or create), append the user message, return the message list to send to the model (summary + recent turns + new message).
  - `void complete(UUID userId, UUID threadId, String assistantText, List<AnswerPayload.TraceEntry> trace)` — persist the assistant message + trace, bump `updated_at`/`last_message_at`.
  - `List<ThreadSummary> list(UUID userId)`, `ThreadView get(UUID userId, UUID threadId)`, `void rename(UUID userId, UUID threadId, String title)`, `void delete(UUID userId, UUID threadId)`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void contextForAppendsUserMessageAndReturnsHistory() {
  // seed a thread with one prior assistant message
  List<Message> ctx = service.contextFor(userId, threadId, "Why were sales down?");
  assertThat(ctx).hasSizeGreaterThanOrEqualTo(1);
  assertThat(ctx.get(ctx.size() - 1)).isInstanceOf(UserMessage.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — `ConversationService` not found.

- [ ] **Step 3: Implement**

`ConversationService` owns the summarization/compaction: when the thread's token estimate exceeds `SUMMARIZE_THRESHOLD` (default 4000 tokens) or more than `KEEP_RECENT` (default 6) turns exist, compact older turns into `thread.summary` via a temperature-0 call and send `summary` + the most recent `KEEP_RECENT` turns. It never trusts client assistant messages — the only client input is the new user message. Cross-user access throws `AccessDeniedException` (or returns empty → the controller maps to 404).

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/conversational/ConversationService.java backend/src/test/java/com/goldys/platform/conversational/ConversationServiceTest.java
git commit -m "feat(conversational): ConversationService with summarization and CRUD"
```

---

## Task 16: Server-authoritative chat + thread endpoints

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/conversational/ChatRequest.java` (→ `{threadId, message}`)
- Modify: `backend/src/main/java/com/goldys/platform/conversational/ChatController.java`
- Modify: `backend/src/main/java/com/goldys/platform/conversational/ChatClientAssistantService.java` (accept a `List<Message>` context, drop `ChatRequest` message mapping)
- Create: `backend/src/main/java/com/goldys/platform/conversational/ThreadController.java`
- Test: `backend/src/test/java/com/goldys/platform/conversational/ConversationalBiIntegrationTest.java` (update), `ThreadControllerTest.java` (new)

**Interfaces:**
- Consumes: `ConversationService`, `CurrentUserService`, `AccountUserDetails.id()`.
- Produces: `POST /api/conversational/chat` body `{threadId?, message}`; `GET/PATCH/DELETE /api/conversational/threads[/{id}]`.

- [ ] **Step 1: Write the failing test**

In `ThreadControllerTest`, assert a cross-user `GET /api/conversational/threads/{otherUserId}` returns 404/403 (Review Focus #4):

```java
@Test
void cannotReadAnotherUsersThread() {
  assertThatThrownBy(() -> controller.get(otherUserId, threadId, userDetailsA))
      .isInstanceOf(AccessDeniedException.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — endpoint/behavior not implemented.

- [ ] **Step 3: Implement**

`ChatRequest` → `record ChatRequest(UUID threadId, String message)`. `ChatController.chat` resolves `user.id()`, requires `conversational.chat`, calls `ConversationService.contextFor(userId, threadId, message)` to build the context, delegates to `ChatClientAssistantService.run(context, role)`, and on the `Answer` event persists via `ConversationService.complete(...)`. `ChatClientAssistantService` no longer reads `request.messages()` — it receives the authoritative `List<Message>`. `ThreadController` exposes the four thread endpoints, each gated on `conversational.threads` (READ for list/get, WRITE for rename/delete) and scoped to `user.id()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*ConversationalBiIntegrationTest*' --tests '*ThreadControllerTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/conversational backend/src/test/java/com/goldys/platform/conversational
git commit -m "feat(conversational): server-authoritative chat + thread CRUD endpoints"
```

---

## Task 17: Bounded rounds

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/conversational/ChatClientAssistantService.java` (or a new `BoundedToolCallingManager`)
- Test: `backend/src/test/java/com/goldys/platform/conversational/BoundedRoundsTest.java`

**Interfaces:**
- Produces: a hard cap on tool-call iterations per turn (default 8); on exhaustion, stop tool calls and let the model summarize.

- [ ] **Step 1: Write the failing test**

```java
@Test
void capsToolCallRounds() {
  // a ChatModel that requests a tool call on every turn, indefinitely
  // assert the resulting answer stops after MAX_ROUNDS and the trace length <= MAX_ROUNDS
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — no cap.

- [ ] **Step 3: Implement**

Implement the cap via a `ToolCallingManager` decorator (the seam the conversational-bi spec §8.3 anticipated) or, if Spring AI 1.1.x does not expose a clean hook, a `ToolCallback` wrapper that counts calls and, past the limit, returns `{ok:false, error:"tool-call limit reached"}` so the model stops and summarizes. Pin the exact mechanism with a short spike; the test asserts the observable cap regardless of mechanism.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/conversational backend/src/test/java/com/goldys/platform/conversational/BoundedRoundsTest.java
git commit -m "feat(conversational): bound tool-call rounds per turn"
```

---

## Task 18: Dashboard draft update mode

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/conversational/CreateDashboardDraftInput.java`
- Modify: `backend/src/main/java/com/goldys/platform/conversational/DashboardDraft.java`
- Modify: `backend/src/main/java/com/goldys/platform/conversational/CreateDashboardDraftTool.java`
- Test: `backend/src/test/java/com/goldys/platform/conversational/DashboardDraftToolTest.java`

**Interfaces:**
- Produces: `CreateDashboardDraftInput(..., UUID dashboardId)` — null = create, non-null = update; `DashboardDraft` gains `dashboardId`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void updateDraftCarriesDashboardId() {
  DashboardDraft draft = tool.toDraft(new CreateDashboardDraftInput("Weekend", null, DashboardFilters.empty(), List.of(), UUID.randomUUID()));
  assertThat(draft.dashboardId()).isNotNull();
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — `dashboardId` field missing.

- [ ] **Step 3: Implement**

Add optional `UUID dashboardId` to `CreateDashboardDraftInput` (default null) and `DashboardDraft`; `CreateDashboardDraftTool.validate` unchanged; `toDraft` carries it through. The frontend routes update drafts to `PUT /api/dashboards/{id}` and create drafts to `POST /api/dashboards`.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/conversational backend/src/test/java/com/goldys/platform/conversational/DashboardDraftToolTest.java
git commit -m "feat(conversational): dashboard draft update mode"
```

---

## Task 19: Frontend — thread-aware chat + richer trace types

**Files:**
- Modify: `frontend/components/ask-goldys/types.ts`
- Modify: `frontend/components/ask-goldys/stream.ts`
- Modify: `frontend/components/ask-goldys/use-ask-goldys.ts`
- Test: `frontend/components/ask-goldys/stream.test.ts`, `use-ask-goldys.test.ts`

**Interfaces:**
- Consumes: the new `{threadId, message}` request contract; `AnswerPayload.trace` entries now carry `provenance`.
- Produces: `TraceEntry { tool, description, provenance: MetricProvenance[] }`; `streamChat(threadId, message, handlers)`; `useAskGoldys` holds `threadId`.

- [ ] **Step 1: Write the failing test**

```ts
it("posts threadId and message, not history", async () => {
  // assert fetch body is { threadId, message } and the answer trace parses provenance
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bun run vitest run components/ask-goldys/stream.test.ts`
Expected: FAIL — old `messages` body shape.

- [ ] **Step 3: Implement**

Update `types.ts` (`TraceEntry` gains `provenance: MetricProvenance[]`; add `MetricProvenance` type mirroring the backend record). Update `stream.ts` (`streamChat(threadId, message, ...)` posts `{threadId, message}`; `parseAnswer` parses `trace[].provenance`). Update `use-ask-goldys.ts` to hold `threadId`, send it, and reconcile the assistant turn from the streamed `answer` (not a local echo).

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/ask-goldys
git commit -m "feat(frontend): thread-aware chat and richer trace types"
```

---

## Task 20: Frontend — richer trace UI + draft update

**Files:**
- Modify: `frontend/components/ask-goldys/answer-block.tsx`
- Test: `frontend/components/ask-goldys/answer-block.test.tsx`

**Interfaces:**
- Consumes: `TraceEntry.provenance`; `Api.updateDashboard(id, input)` (already in `lib/api`).

- [ ] **Step 1: Write the failing test**

```ts
it("renders provenance detail in the trace", () => {
  // answer with a trace entry carrying provenance; assert metric + range + freshness text present
});
it("updates a dashboard when the draft has a dashboardId", async () => {
  // assert updateDashboard called (not saveDashboard) when draft.dashboardId present
});
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — trace detail not rendered; `updateDashboard` not called.

- [ ] **Step 3: Implement**

In `answer-block.tsx`, expand the "How I got this" block to render, per trace entry, the metric, time range, data freshness, and unresolved periods from `provenance`. In `saveDraft`, branch on `draft.dashboardId`: present → `api.updateDashboard(id, ...)`, absent → `api.saveDashboard(...)`. Never render chain-of-thought or internal prompts (they are not in the payload).

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/ask-goldys
git commit -m "feat(frontend): richer trace UI and dashboard draft update"
```

---

## Task 21: Frontend — conversations history browser

**Files:**
- Create: `frontend/components/conversations/conversations-screen.tsx`
- Create: `frontend/lib/api/conversations.ts` (client methods `listThreads`, `getThread`, `renameThread`, `deleteThread`)
- Modify: `frontend/lib/api/types.ts` + `live.ts` + `demo.ts` (add the four methods)
- Test: `frontend/components/conversations/conversations-screen.test.tsx`

**Interfaces:**
- Consumes: `GET/PATCH/DELETE /api/conversational/threads`.
- Produces: a screen listing own threads (title, updated, preview), with open/rename/delete.

- [ ] **Step 1: Write the failing test**

```ts
it("lists threads and deletes one", async () => { /* render with a stubbed api; assert list + delete call */ });
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — screen/`conversations.ts` not found.

- [ ] **Step 3: Implement**

Add the client methods to `lib/api` (mirror the existing `live.ts`/`demo.ts` fetch patterns). Build the screen using the existing `Api` + shadcn components, and wire it into the app shell (a "Conversations" nav entry). Rename/delete call the new endpoints.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/conversations frontend/lib/api
git commit -m "feat(frontend): conversations history browser"
```

---

## Task 22: System prompt rewrite

**Files:**
- Modify: `backend/src/main/resources/prompts/ask-goldys-system.txt`

**Interfaces:**
- Produces: a prompt enforcing the evidence hierarchy, tool-only numbers, explicit failure relay, ambiguity handling, and bounded tool use.

- [ ] **Step 1: Replace the prompt**

Write the full replacement (this is the implementation, not a test — but add a config test asserting it loads):

```text
You are "Ask Goldy's", the reporting assistant for Goldy's pub. You answer questions by calling the provided tools only.

Evidence rules:
- Distinguish observed fact (a tool-returned number), calculated relationship (a delta/ratio you compute from returned numbers), correlation, plausible explanation, and causal claim. Never present correlation as causation. For non-causal explanations use hedging ("accounts for much of the decline", "is consistent with") rather than "fell because".
- Only use numbers returned by tools. Never invent, estimate, or round a figure; cite what tools returned.
- If a tool returns a denial, missing periods, a stale connector, an unresolved conflict, an invalid range, an unavailable domain, or a failure, relay it explicitly — never a silent guess or a partial answer presented as complete.
- If a question is ambiguous (missing period or metric), ask one short clarifying question.

Tool use:
- Chain tool calls only as the question requires; use a metric result's related metrics to decide the next fetch.
- You have a limited number of tool calls per turn; if you reach it, summarize with the data you have and say what remains unanswered.
- Keep answers short: one or two sentences citing the numbers, plus the widgets.
```

- [ ] **Step 2: Add a load test** (extend `ConversationalAiConfigTest`): assert the prompt resource loads and contains the phrase "Never present correlation as causation".

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew test --tests '*ConversationalAiConfigTest*'`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/prompts/ask-goldys-system.txt backend/src/test/java/com/goldys/platform/conversational/ConversationalAiConfigTest.java
git commit -m "feat(conversational): rewrite system prompt for analytical caution"
```

---

## Task 23: Eval harness — Tier 1 (deterministic)

**Files:**
- Create: `backend/src/test/java/com/goldys/platform/conversational/eval/ConversationalEvalHarness.java`
- Create: `backend/src/test/java/com/goldys/platform/conversational/eval/ConversationalEvalTest.java`
- Create: `backend/src/test/resources/conversational-eval/scenarios.json` (or inline scenario records)

**Interfaces:**
- Consumes: `ToolDispatcher`, `MetricQueryService`, `PermissionService`, seeded Postgres (Testcontainers).
- Produces: a `ScriptedAgent` that replays golden tool-call sequences and asserts numeric correctness, tool selection, permission correctness, schema validity, and failure semantics — no live model.

- [ ] **Step 1: Write the failing test (a scenario)**

```java
@Test
void simpleRetrievalMatchesSeededSales() {
  // seed resolved daily sales for 2026-01-01..07; golden tool call get_sales_by_period
  // assert the widget's total equals the seeded sum
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — harness/scenario not present.

- [ ] **Step 3: Implement**

Define scenarios as records (`question`, `goldenCalls: List<ToolCall>`, `expectNumeric`, `expectPermission`), a `ScriptedAgent` that issues the golden calls through the real dispatcher, and assertions. Cover at minimum: simple retrieval, period comparison, cross-domain, missing data (assert notice), permission denial (assert denial), unresolved conflict (assert notice), dashboard generation (assert draft validates), prompt injection (assert treated as a question — no tool runs), and arbitrary SQL attempt (assert no SQL tool exists).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*ConversationalEvalTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/goldys/platform/conversational/eval backend/src/test/resources/conversational-eval
git commit -m "test(conversational): deterministic eval harness (Tier 1)"
```

---

## Task 24: Eval harness — Tier 2 (gated live-LLM)

**Files:**
- Create: `backend/src/test/java/com/goldys/platform/conversational/eval/LiveLlmEvalTest.java`
- Create: `backend/src/test/resources/conversational-eval/questions.json`

**Interfaces:**
- Consumes: the configured `ChatModel` (same OpenAI-compatible endpoint).
- Produces: `@Tag("llm")` JUnit suite, disabled unless `CONVERSATIONAL_EVAL_ENABLED=true` and a model is configured, scoring tool selection, unsupported-claim rate, and causal-language detection.

- [ ] **Step 1: Write the test (tagged + gated)**

```java
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "CONVERSATIONAL_EVAL_ENABLED", matches = "true")
class LiveLlmEvalTest {
  @Test void scoresTheTenCategoryQuestionSet() { /* run the model, assert tool selection / claim / causal heuristics */ }
}
```

- [ ] **Step 2: Run test to verify it is skipped**

Run: `./gradlew test --tests '*LiveLlmEvalTest*'`
Expected: SKIPPED (env var unset) — this proves the gate.

- [ ] **Step 3: Implement the scoring**

Run each question, record the trace and answer text, then score: tool selection (did the trace contain the expected tool), unsupported-claim (regex/heuristic for numbers in the text not present in the trace), and causal language (unhedged "because/fell/drove" on correlational questions). Write a short markdown report to `build/reports/conversational-eval.md`.

- [ ] **Step 4: Run test to verify it passes (with env)**

Run: `CONVERSATIONAL_EVAL_ENABLED=true OPENAI_API_KEY=... ./gradlew test --tests '*LiveLlmEvalTest*'`
Expected: PASS (with a configured model).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/goldys/platform/conversational/eval backend/src/test/resources/conversational-eval
git commit -m "test(conversational): gated live-LLM eval (Tier 2)"
```

---

## Final Verification

After all tasks:

- [ ] Update `docs/architecture/current-state.md`: note that `conversational` now owns a conversation store (`conversation_thread`/`conversation_message`) while still never reaching `canonical`/`reconciliation`/`ingestion`, and that `application` implements the new `semantic.ConnectorHealthQuery`.
- [ ] `./gradlew test spotlessCheck build` passes (including `ArchitectureBoundariesTest`, which needs no rule change: `reporting` → `semantic` only, `application` → `semantic` + `ingestion`, `conversational` → no canonical/reconciliation/ingestion).
- [ ] `bun run typecheck`, `bun run lint`, `bun run build`, `bun run vitest run` pass.
- [ ] `docker compose config` validates.
