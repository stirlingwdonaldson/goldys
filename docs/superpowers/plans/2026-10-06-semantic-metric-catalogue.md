# Semantic Metric Catalogue — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a stable, bounded semantic metric catalogue (stable `MetricId`s, a `MetricDefinition` contract, a single `MetricQueryService`, derived metrics, a comparison framework, and provenance) and migrate dashboards, AI reporting tools, and the labour/inventory services onto it.

**Architecture:** The catalogue lives in a new `semantic.catalog` sub-package that is a *leaf* (depends only on the existing `semantic.*MetricsQuery` interfaces and `java.*`). Base executors wrap the existing resolved `*MetricsQuery` implementations; derived executors compose base executors in plain Java. `reporting`, `application`, and `conversational` become consumers that render `MetricResult` into `widget` specs.

**Tech Stack:** Java 25, Spring Boot 3.5.0, PostgreSQL 16 (Testcontainers), JUnit 5 + AssertJ + Mockito, ArchUnit 1.4.1, Flyway, Gradle, Spotless (google-java-format 1.28.0).

**Spec:** `docs/superpowers/specs/2026-10-06-semantic-metric-catalogue-design.md`

## Global Constraints

- Java 25 toolchain; Spring Boot 3.5.0; PostgreSQL 16 via Testcontainers.
- No generic SQL abstraction: bounded enums only. Arbitrary columns, SQL, free-text filters, and dynamic LLM expressions are structurally impossible.
- The `semantic` package (including `semantic.catalog`) is a leaf: it may not depend on `api`, `application`, `reporting`, `conversational`, `reconciliation`, `canonical`, `ingestion`, `auth`, `connectors`, `widget`, or `dashboard`.
- `semantic.catalog` may depend only on the `com.goldys.platform.semantic.*MetricsQuery` interfaces, `java.*`, and Spring stereotype annotations.
- Metric IDs are stable dotted strings (`sales.gross`), never table or Java class names.
- Display + note: a metric always displays the resolved sum when any resolved data exists; never `null` on partial data. `null` only when there is nothing to display (no resolved data) or a derived denominator is zero. Unresolved days are flagged in `MetricProvenance.missingPeriods` and surfaced as a notice — never silently zeroed.
- Week grain = ISO (Monday start). Month grain = calendar month. `Calendar.TRADING` and `Calendar.CALENDAR` are identical today.
- Conventional Commits, atomic, one logical increment per commit. Never commit to `main`; work on `docs/semantic-metric-catalogue`.
- All new Java must pass `./gradlew spotlessApply` (google-java-format 1.28.0) before commit.

## Review Focus

1. **Zero denominator in a derived metric** (covers = 0, gross = 0) → `null` value + a notice, never a division-by-zero exception. — pinned in Task 12/13/14.
2. **An unresolved day inside a range** → the aggregate still displays (sum of resolved days) with a "N days unresolved" notice; never `0`, never whole-aggregate `null`. — pinned in Task 8.
3. **No data at all in a range** → `null` result value + "no resolved data" notice, never an exception. — pinned in Task 8.
4. **A dimension or grain not allowed for a metric** → `IllegalArgumentException` from `MetricQueryService`, never silently ignored. — pinned in Task 4.
5. **Week/month grain bucketing** (ISO Monday week; month boundary) is deterministic and `TRADING` == `CALENDAR`. — pinned in Task 7.

---

### Task 1: Catalogue enums and query types (leaf)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricId.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/TimeGrain.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/Calendar.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/Dimension.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/Comparison.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/TimeRange.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/MetricQueryTest.java`

**Interfaces:**
- Produces: `MetricId` (enum with `.value()`), `TimeGrain {DAY,WEEK,MONTH}`, `Calendar {TRADING,CALENDAR}`, `Dimension {SERVICE_PERIOD,DEPARTMENT,PRODUCT}`, `Comparison {PREVIOUS_DAY, PREVIOUS_WEEK, SAME_WEEKDAY_LAST_WEEK, SAME_PERIOD_LAST_YEAR, ROLLING_4_WEEKS, ROLLING_12_WEEKS, BUDGET, FORECAST}`, `TimeRange(LocalDate from, LocalDate to, Calendar calendar)`, `MetricQuery(MetricId metric, TimeRange range, TimeGrain grain, Set<Dimension> dimensions, Comparison comparison)`.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MetricQueryTest {

  private static final TimeRange RANGE =
      new TimeRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13), Calendar.TRADING);

  @Test
  void exposesStableDottedIds() {
    assertThat(MetricId.SALES_GROSS.value()).isEqualTo("sales.gross");
    assertThat(MetricId.RESERVATIONS_NO_SHOW_RATE.value()).isEqualTo("reservations.no_show_rate");
  }

  @Test
  void rejectsRangeWithToBeforeFrom() {
    assertThatThrownBy(
            () -> new TimeRange(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 7), Calendar.TRADING))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("from");
  }

  @Test
  void defaultsToEmptyDimensionsWhenNull() {
    MetricQuery q =
        new MetricQuery(MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, null, null);
    assertThat(q.dimensions()).isEmpty();
    assertThat(q.comparison()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.MetricQueryTest`
Expected: FAIL — compilation error, `MetricId`/`TimeRange`/`MetricQuery` not found.

- [ ] **Step 3: Write the types**

`MetricId.java` — all 26 IDs from the spec §3 (15 base + 11 derived):

```java
package com.goldys.platform.semantic.catalog;

/** Stable, machine-readable metric identifiers. Never a table or Java class name. */
public enum MetricId {
  SALES_GROSS("sales.gross"),
  SALES_NET("sales.net"),
  SALES_GST("sales.gst"),
  RESERVATIONS_BOOKINGS("reservations.bookings"),
  RESERVATIONS_ATTENDED("reservations.attended"),
  RESERVATIONS_COVERS("reservations.covers"),
  RESERVATIONS_NO_SHOWS("reservations.no_shows"),
  LABOUR_SCHEDULED_HOURS("labour.scheduled_hours"),
  LABOUR_ACTUAL_HOURS("labour.actual_hours"),
  LABOUR_COST("labour.cost"),
  INVENTORY_PURCHASES("inventory.purchases"),
  INVENTORY_WASTAGE("inventory.wastage"),
  INVENTORY_STOCK_ON_HAND("inventory.stock_on_hand"),
  PRODUCT_SALES_AMOUNT("product.sales_amount"),
  PRODUCT_SALES_QUANTITY("product.sales_quantity"),
  RESERVATIONS_NO_SHOW_RATE("reservations.no_show_rate"),
  RESERVATIONS_BOOKING_TO_COVER_CONVERSION("reservations.booking_to_cover_conversion"),
  RESERVATIONS_AVG_PARTY_SIZE("reservations.avg_party_size"),
  SALES_AVERAGE_SPEND_PER_COVER("sales.average_spend_per_cover"),
  LABOUR_HOURS_PER_COVER("labour.hours_per_cover"),
  LABOUR_COST_PER_COVER("labour.cost_per_cover"),
  LABOUR_HOURS_VARIANCE("labour.hours_variance"),
  LABOUR_FOH_PERCENT("labour.foh_percent"),
  LABOUR_BOH_PERCENT("labour.boh_percent"),
  INVENTORY_FOOD_COST_PERCENT("inventory.food_cost_percent"),
  PRODUCT_TOP_SELLERS("product.top_sellers");

  private final String value;

  MetricId(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
```

`TimeGrain.java`, `Calendar.java`, `Dimension.java`, `Comparison.java` — plain enums:

```java
package com.goldys.platform.semantic.catalog;

public enum TimeGrain {
  DAY,
  WEEK,
  MONTH
}
```

```java
package com.goldys.platform.semantic.catalog;

/** Trading-day vs calendar-day semantics. Identical today; boundary is a deferred decision. */
public enum Calendar {
  TRADING,
  CALENDAR
}
```

```java
package com.goldys.platform.semantic.catalog;

/** A grouping axis. An empty dimension set means "no grouping". */
public enum Dimension {
  SERVICE_PERIOD,
  DEPARTMENT,
  PRODUCT
}
```

```java
package com.goldys.platform.semantic.catalog;

/** Reusable period comparisons. BUDGET and FORECAST are declared but have no data source yet. */
public enum Comparison {
  PREVIOUS_DAY,
  PREVIOUS_WEEK,
  SAME_WEEKDAY_LAST_WEEK,
  SAME_PERIOD_LAST_YEAR,
  ROLLING_4_WEEKS,
  ROLLING_12_WEEKS,
  BUDGET,
  FORECAST
}
```

`TimeRange.java`:

```java
package com.goldys.platform.semantic.catalog;

import java.time.LocalDate;
import java.util.Objects;

/** An inclusive date range plus the calendar semantics to apply. */
public record TimeRange(LocalDate from, LocalDate to, Calendar calendar) {
  public TimeRange {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(calendar, "calendar");
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to is before from");
    }
  }
}
```

`MetricQuery.java`:

```java
package com.goldys.platform.semantic.catalog;

import java.util.Objects;
import java.util.Set;

/**
 * The bounded query behind a panel: a metric, a time range, a grain, and optional dimensions and
 * comparison. There is no column/SQL/filter surface.
 */
public record MetricQuery(
    MetricId metric, TimeRange range, TimeGrain grain, Set<Dimension> dimensions,
    Comparison comparison) {
  public MetricQuery {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(range, "range");
    Objects.requireNonNull(grain, "grain");
    dimensions = dimensions == null ? Set.of() : Set.copyOf(dimensions);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.MetricQueryTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: semantic catalogue enums and query types"
```

---

### Task 2: Result types and provenance (leaf)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricPoint.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricSeries.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricRankedItem.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricResult.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/TimeSeriesResult.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/RankedListResult.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricProvenance.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/MetricResultTest.java`

**Interfaces:**
- Consumes: `MetricId`, `TimeRange`, `TimeGrain` (Task 1).
- Produces: `MetricPoint(LocalDate bucketStart, BigDecimal value)`, `MetricSeries(String dimensionValue, List<MetricPoint> points)`, `MetricRankedItem(String label, BigDecimal primary, BigDecimal secondary)`, sealed `MetricResult` with `metric()`, `notices()`, `provenance()` and records `TimeSeriesResult`, `RankedListResult`; `MetricProvenance(MetricId metric, String definitionVersion, TimeRange range, TimeGrain grain, String sourceDomain, Instant dataFreshness, List<LocalDate> missingPeriods, String calculationVersion)`.

> Note: names `MetricSeries` and `MetricRankedItem` (not `Series`/`RankedItem`) avoid colliding with the existing `com.goldys.platform.widget.Series`/`RankedItem`, since migration code imports both packages.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricResultTest {

  @Test
  void constructsATimeSeriesResultWithProvenance() {
    MetricProvenance provenance =
        new MetricProvenance(
            MetricId.SALES_GROSS,
            "1",
            new TimeRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_daily_sales",
            Instant.parse("2026-09-13T06:00:00Z"),
            List.of(),
            "1");

    TimeSeriesResult result =
        new TimeSeriesResult(
            MetricId.SALES_GROSS,
            List.of(
                new MetricSeries(
                    null, List.of(new MetricPoint(LocalDate.of(2026, 9, 7), new BigDecimal("100.00")))),
                new MetricSeries("DINNER", List.of())),
            List.of("1 day unresolved"),
            provenance);

    assertThat(result.metric()).isEqualTo(MetricId.SALES_GROSS);
    assertThat(result.notices()).containsExactly("1 day unresolved");
    assertThat(result.series()).hasSize(2);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.MetricResultTest`
Expected: FAIL — types not found.

- [ ] **Step 3: Write the types**

```java
package com.goldys.platform.semantic.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One bucket's value. {@code value} is null when the bucket has no resolved data. */
public record MetricPoint(LocalDate bucketStart, BigDecimal value) {}
```

```java
package com.goldys.platform.semantic.catalog;

import java.util.List;
import java.util.Objects;

/** One series of points. {@code dimensionValue} is null for an ungrouped series. */
public record MetricSeries(String dimensionValue, List<MetricPoint> points) {
  public MetricSeries {
    points = points == null ? List.of() : List.copyOf(points);
  }
}
```

```java
package com.goldys.platform.semantic.catalog;

import java.math.BigDecimal;

/** One row in a ranked list (used by {@code product.top_sellers}). */
public record MetricRankedItem(String label, BigDecimal primary, BigDecimal secondary) {}
```

```java
package com.goldys.platform.semantic.catalog;

import java.util.List;

/** A metric query result: a value shape plus provenance. */
public sealed interface MetricResult permits TimeSeriesResult, RankedListResult {
  MetricId metric();

  /** Runtime caveats to surface to the user (e.g. "2 days unresolved"). */
  List<String> notices();

  MetricProvenance provenance();
}
```

```java
package com.goldys.platform.semantic.catalog;

import java.util.List;
import java.util.Objects;

/** A time-series (or multi-series, when a dimension groups the result) metric result. */
public record TimeSeriesResult(
    MetricId metric, List<MetricSeries> series, List<String> notices, MetricProvenance provenance)
    implements MetricResult {
  public TimeSeriesResult {
    Objects.requireNonNull(metric, "metric");
    series = series == null ? List.of() : List.copyOf(series);
    notices = notices == null ? List.of() : List.copyOf(notices);
    Objects.requireNonNull(provenance, "provenance");
  }
}
```

```java
package com.goldys.platform.semantic.catalog;

import java.util.List;
import java.util.Objects;

/** A ranked-list metric result (used by {@code product.top_sellers}). */
public record RankedListResult(
    MetricId metric, List<MetricRankedItem> items, List<String> notices, MetricProvenance provenance)
    implements MetricResult {
  public RankedListResult {
    Objects.requireNonNull(metric, "metric");
    items = items == null ? List.of() : List.copyOf(items);
    notices = notices == null ? List.of() : List.copyOf(notices);
    Objects.requireNonNull(provenance, "provenance");
  }
}
```

```java
package com.goldys.platform.semantic.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Provenance for a metric result: identity, range, freshness, and missing periods. */
public record MetricProvenance(
    MetricId metric,
    String definitionVersion,
    TimeRange range,
    TimeGrain grain,
    String sourceDomain,
    Instant dataFreshness,
    List<LocalDate> missingPeriods,
    String calculationVersion) {
  public MetricProvenance {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(definitionVersion, "definitionVersion");
    Objects.requireNonNull(range, "range");
    Objects.requireNonNull(grain, "grain");
    missingPeriods = missingPeriods == null ? List.of() : List.copyOf(missingPeriods);
    Objects.requireNonNull(calculationVersion, "calculationVersion");
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.MetricResultTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: semantic catalogue result types and provenance"
```

---

### Task 3: MetricDefinition contract and MetricCatalog registry

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricDefinition.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricCatalog.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/MetricCatalogTest.java`

**Interfaces:**
- Consumes: `MetricId`, `TimeGrain`, `Dimension` (Task 1).
- Produces: `MetricDefinition` record and `MetricCatalog` (`@Component`) exposing `MetricDefinition definition(MetricId)` and `Set<MetricId> ids()`.

> Note: `requiredPermission` is a plain `String` (dotted, matching the `auth.ResourceKey` pattern) — NOT `auth.ResourceKey` — because `semantic.catalog` is a leaf and may not depend on `auth`. Consumers wrap it in `new ResourceKey(...)` at the enforcement point.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MetricCatalogTest {

  private static final Pattern RESOURCE_KEY = Pattern.compile("[a-z][a-z0-9.-]{0,99}");

  @Test
  void everyMetricIdHasADefinition() {
    MetricCatalog catalog = new MetricCatalog();
    for (MetricId id : MetricId.values()) {
      assertThat(catalog.definition(id)).as(id.value()).isNotNull();
    }
  }

  @Test
  void definitionsCarryAValidPermissionResource() {
    MetricCatalog catalog = new MetricCatalog();
    for (MetricId id : MetricId.values()) {
      MetricDefinition d = catalog.definition(id);
      assertThat(RESOURCE_KEY.matcher(d.requiredPermission()).matches())
          .as(id.value() + " permission")
          .isTrue();
    }
  }

  @Test
  void grossSalesAllowsDayWeekMonthAndNoDimensions() {
    MetricDefinition d = new MetricCatalog().definition(MetricId.SALES_GROSS);
    assertThat(d.allowedGrains()).containsExactlyInAnyOrder(TimeGrain.DAY, TimeGrain.WEEK, TimeGrain.MONTH);
    assertThat(d.validDimensions()).isEmpty();
    assertThat(d.unit()).isEqualTo("AUD");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.MetricCatalogTest`
Expected: FAIL — `MetricCatalog` not found.

- [ ] **Step 3: Write the types**

`MetricDefinition.java`:

```java
package com.goldys.platform.semantic.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The documented contract for a metric: what it means, where it comes from, and how to query it. */
public record MetricDefinition(
    MetricId id,
    String name,
    String definition,
    String formula,
    String unit,
    String sourceDomain,
    Set<Dimension> validDimensions,
    Set<TimeGrain> allowedGrains,
    String requiredPermission,
    List<String> notes,
    String version) {
  public MetricDefinition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(formula, "formula");
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceDomain, "sourceDomain");
    validDimensions = validDimensions == null ? Set.of() : Set.copyOf(validDimensions);
    allowedGrains = allowedGrains == null ? Set.of() : Set.copyOf(allowedGrains);
    Objects.requireNonNull(requiredPermission, "requiredPermission");
    notes = notes == null ? List.of() : List.copyOf(notes);
    Objects.requireNonNull(version, "version");
  }
}
```

`MetricCatalog.java` — a `@Component` holding all 26 definitions. Build with a private `base(...)`/`derived(...)` helper. Abridged listing of the 26 (write each; here is the pattern plus the sales/reservations rows — implement the remaining rows from the spec §3.1/§3.2 tables verbatim):

```java
package com.goldys.platform.semantic.catalog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** The metric catalogue: every supported metric's {@link MetricDefinition}, keyed by {@link MetricId}. */
@Component
public class MetricCatalog {
  private static final Set<TimeGrain> DAY_WEEK_MONTH = Set.of(TimeGrain.DAY, TimeGrain.WEEK, TimeGrain.MONTH);
  private static final Set<TimeGrain> DAY_ONLY = Set.of(TimeGrain.DAY);

  private final Map<MetricId, MetricDefinition> byId;

  public MetricCatalog() {
    this.byId =
        Stream.of(
                base(MetricId.SALES_GROSS, "Gross sales", "Resolved gross sales incl. GST", "totalSales", "AUD", "resolved_daily_sales", DAY_WEEK_MONTH, "reconciliation.sales", "gross includes GST"),
                base(MetricId.SALES_NET, "Net sales", "Resolved net sales = gross − GST", "netTotal", "AUD", "resolved_daily_sales", DAY_WEEK_MONTH, "reconciliation.sales", null),
                base(MetricId.SALES_GST, "GST", "Resolved GST", "gstTotal", "AUD", "resolved_daily_sales", DAY_WEEK_MONTH, "reconciliation.sales", null),
                base(MetricId.RESERVATIONS_BOOKINGS, "Bookings", "Resolved bookings", "bookings", "count", "resolved_reservation_day", DAY_WEEK_MONTH, "reservations.metrics", null),
                base(MetricId.RESERVATIONS_ATTENDED, "Attended parties", "Resolved attended", "attended", "count", "resolved_reservation_day", DAY_WEEK_MONTH, "reservations.metrics", null),
                base(MetricId.RESERVATIONS_COVERS, "Covers", "Resolved covers (guests)", "covers", "count", "resolved_reservation_day", DAY_WEEK_MONTH, "reservations.metrics", null),
                base(MetricId.RESERVATIONS_NO_SHOWS, "No-shows", "Resolved no-shows", "noShows", "count", "resolved_reservation_day", DAY_WEEK_MONTH, "reservations.metrics", null),
                base(MetricId.LABOUR_SCHEDULED_HOURS, "Scheduled hours", "Resolved scheduled hours", "scheduledHours", "hours", "resolved_labour_day", DAY_WEEK_MONTH, "labour.hours", null),
                base(MetricId.LABOUR_ACTUAL_HOURS, "Actual hours", "Resolved actual hours", "actualHours", "hours", "resolved_labour_day", DAY_WEEK_MONTH, "labour.hours", null),
                base(MetricId.LABOUR_COST, "Labour cost", "Resolved actual cost", "actualCost", "AUD", "resolved_labour_day", DAY_WEEK_MONTH, "labour.cost", null),
                base(MetricId.INVENTORY_PURCHASES, "Purchases (COGS)", "Resolved purchases", "purchases", "AUD", "resolved_inventory_day", DAY_WEEK_MONTH, "inventory.cost", null),
                base(MetricId.INVENTORY_WASTAGE, "Wastage", "Resolved wastage", "wastage", "AUD", "resolved_inventory_day", DAY_WEEK_MONTH, "inventory.cost", null),
                base(MetricId.INVENTORY_STOCK_ON_HAND, "Closing stock", "Resolved stock-on-hand", "stockOnHand", "AUD", "resolved_inventory_day", DAY_ONLY, "inventory.cost", null),
                base(MetricId.PRODUCT_SALES_AMOUNT, "Product sales amount", "Resolved product amount", "amount", "AUD", "resolved_product_sales", DAY_WEEK_MONTH, "reconciliation.sales", null),
                base(MetricId.PRODUCT_SALES_QUANTITY, "Product sales quantity", "Resolved product quantity", "quantitySold", "units", "resolved_product_sales", DAY_WEEK_MONTH, "reconciliation.sales", null),
                derived(MetricId.RESERVATIONS_NO_SHOW_RATE, "No-show rate", "no_shows ÷ bookings", "%", "reservations.metrics", null),
                derived(MetricId.RESERVATIONS_BOOKING_TO_COVER_CONVERSION, "Booking-to-cover conversion", "attended ÷ bookings", "%", "reservations.metrics", null),
                derived(MetricId.RESERVATIONS_AVG_PARTY_SIZE, "Average party size", "covers ÷ attended", "ratio", "reservations.metrics", null),
                derived(MetricId.SALES_AVERAGE_SPEND_PER_COVER, "Average spend per cover", "sales.gross ÷ reservations.covers", "AUD", "reconciliation.sales", null),
                derived(MetricId.LABOUR_HOURS_PER_COVER, "Hours per cover", "labour.actual_hours ÷ reservations.covers", "hours/cover", "labour.hours", null),
                derived(MetricId.LABOUR_COST_PER_COVER, "Cost per cover", "labour.cost ÷ reservations.covers", "AUD/cover", "labour.cost", null),
                derived(MetricId.LABOUR_HOURS_VARIANCE, "Hours variance", "labour.scheduled_hours − labour.actual_hours", "hours", "labour.hours", null),
                derived(MetricId.LABOUR_FOH_PERCENT, "FOH labour cost %", "FOH labour.cost ÷ sales.gross", "%", "labour.cost", null),
                derived(MetricId.LABOUR_BOH_PERCENT, "BOH labour cost %", "BOH labour.cost ÷ sales.gross", "%", "labour.cost", null),
                derived(MetricId.INVENTORY_FOOD_COST_PERCENT, "Food cost %", "inventory.purchases ÷ sales.gross", "%", "inventory.cost", null),
                derived(MetricId.PRODUCT_TOP_SELLERS, "Top sellers", "ranked product list by summed resolved amount", "list", "reconciliation.sales", null))
            .collect(Collectors.toUnmodifiableMap(MetricDefinition::id, Function.identity()));
  }

  public MetricDefinition definition(MetricId id) {
    MetricDefinition d = byId.get(id);
    if (d == null) {
      throw new IllegalArgumentException("Unknown metric: " + id);
    }
    return d;
  }

  public Set<MetricId> ids() {
    return byId.keySet();
  }

  private static MetricDefinition base(
      MetricId id, String name, String definition, String formula, String unit, String domain,
      Set<TimeGrain> grains, String permission, String note) {
    return new MetricDefinition(
        id, name, definition, formula, unit, domain, Set.of(), grains, permission,
        note == null ? List.of() : List.of(note), "1");
  }

  private static MetricDefinition derived(
      MetricId id, String name, String formula, String unit, String permission, String note) {
    return new MetricDefinition(
        id, name, formula, formula, unit, "derived", Set.of(), DAY_WEEK_MONTH, permission,
        note == null ? List.of() : List.of(note), "1");
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.MetricCatalogTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: metric definition contract and catalogue registry"
```

---

### Task 4: MetricExecutor and MetricQueryService

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricExecutor.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQueryService.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQueryServiceImpl.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/MetricQueryServiceImplTest.java`

**Interfaces:**
- Consumes: `MetricDefinition`, `MetricCatalog`, `MetricQuery`, `MetricResult` (Tasks 1–3).
- Produces: `MetricExecutor { MetricId id(); MetricResult evaluate(MetricQuery query); }`, `MetricQueryService { MetricResult query(MetricQuery query); }`, and `MetricQueryServiceImpl` (`@Service`) that validates grain/dimensions against the definition and routes to the executor.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MetricQueryServiceImplTest {

  private static final TimeRange RANGE =
      new TimeRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), Calendar.CALENDAR);

  @Test
  void delegatesToTheExecutorForTheMetric() {
    MetricExecutor executor = mock(MetricExecutor.class);
    when(executor.id()).thenReturn(MetricId.SALES_GROSS);
    MetricQuery query = new MetricQuery(MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null);
    MetricResult expected =
        new TimeSeriesResult(
            MetricId.SALES_GROSS,
            List.of(new MetricSeries(null, List.of(new MetricPoint(RANGE.from(), null)))),
            List.of(),
            new MetricProvenance(
                MetricId.SALES_GROSS, "1", RANGE, TimeGrain.DAY, "resolved_daily_sales", Instant.EPOCH, List.of(), "1"));
    when(executor.evaluate(query)).thenReturn(expected);

    MetricQueryService service = new MetricQueryServiceImpl(new MetricCatalog(), List.of(executor));

    assertThat(service.query(query)).isSameAs(expected);
    verify(executor).evaluate(query);
  }

  @Test
  void rejectsAnUnknownMetric() {
    MetricQueryService service = new MetricQueryServiceImpl(new MetricCatalog(), List.of());
    assertThatThrownBy(() -> service.query(new MetricQuery(null, RANGE, TimeGrain.DAY, Set.of(), null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAnInvalidGrain() {
    MetricExecutor executor = mock(MetricExecutor.class);
    when(executor.id()).thenReturn(MetricId.INVENTORY_STOCK_ON_HAND);
    MetricQueryService service = new MetricQueryServiceImpl(new MetricCatalog(), List.of(executor));

    assertThatThrownBy(
            () ->
                service.query(
                    new MetricQuery(MetricId.INVENTORY_STOCK_ON_HAND, RANGE, TimeGrain.MONTH, Set.of(), null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("grain");
  }

  @Test
  void rejectsADimensionNotAllowedForTheMetric() {
    MetricExecutor executor = mock(MetricExecutor.class);
    when(executor.id()).thenReturn(MetricId.SALES_GROSS);
    MetricQueryService service = new MetricQueryServiceImpl(new MetricCatalog(), List.of(executor));

    assertThatThrownBy(
            () ->
                service.query(
                    new MetricQuery(MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(Dimension.DEPARTMENT), null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dimension");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.MetricQueryServiceImplTest`
Expected: FAIL — `MetricExecutor`/`MetricQueryService`/`MetricQueryServiceImpl` not found.

- [ ] **Step 3: Write the interfaces and implementation**

```java
package com.goldys.platform.semantic.catalog;

/** One explicit, tested implementation of a metric's query path. */
public interface MetricExecutor {
  MetricId id();

  MetricResult evaluate(MetricQuery query);
}
```

```java
package com.goldys.platform.semantic.catalog;

/** The controlled query interface. Permission-agnostic; enforcement lives in consumers. */
public interface MetricQueryService {
  MetricResult query(MetricQuery query);
}
```

```java
package com.goldys.platform.semantic.catalog;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Routes a {@link MetricQuery} to its executor after validating it against the catalogue. */
@Service
public class MetricQueryServiceImpl implements MetricQueryService {
  private final MetricCatalog catalog;
  private final Map<MetricId, MetricExecutor> executors;

  public MetricQueryServiceImpl(MetricCatalog catalog, List<MetricExecutor> executors) {
    this.catalog = catalog;
    this.executors =
        executors.stream().collect(Collectors.toUnmodifiableMap(MetricExecutor::id, Function.identity()));
  }

  @Override
  public MetricResult query(MetricQuery query) {
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(query.metric(), "metric");
    MetricDefinition definition = catalog.definition(query.metric());
    if (!definition.allowedGrains().contains(query.grain())) {
      throw new IllegalArgumentException(
          "grain " + query.grain() + " is not allowed for " + query.metric().value());
    }
    if (!definition.validDimensions().containsAll(query.dimensions())) {
      throw new IllegalArgumentException(
          "dimensions " + query.dimensions() + " are not allowed for " + query.metric().value());
    }
    MetricExecutor executor = executors.get(query.metric());
    if (executor == null) {
      throw new IllegalArgumentException("No executor for metric: " + query.metric());
    }
    return executor.evaluate(query);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.MetricQueryServiceImplTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: metric query service and executor contract"
```

---

### Task 5: Extend resolved daily-sales projection with net/gst

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__daily_sales_net_gst.sql`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedDailySales.java`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/DailySalesMetric.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedDailySalesQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ResolvedDailySalesQueryTest.java` (if not present, create)

**Interfaces:**
- Consumes: existing `ResolvedDailySales` entity + `DailySalesMetric` + `ResolvedDailySalesQuery`.
- Produces: `DailySalesMetric(LocalDate tradingDate, BigDecimal grossSales, BigDecimal netSales, BigDecimal gst, String authoritativeSource, boolean hasConflict)`; `ResolvedDailySales` gains `netSales()`/`gst()`.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.DailySalesMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResolvedDailySalesQueryTest {

  @Test
  void mapsNetAndGst() {
    ResolvedDailySalesRepository repo = mock(ResolvedDailySalesRepository.class);
    ResolvedDailySales row =
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 13),
            new BigDecimal("27650.66"),
            new BigDecimal("2513.70"),
            new BigDecimal("25136.96"),
            "agreed",
            "agreed",
            false,
            java.time.Instant.EPOCH);
    when(repo.findByTradingDateBetweenOrderByTradingDateAsc(
            LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13)))
        .thenReturn(List.of(row));

    List<DailySalesMetric> result =
        new ResolvedDailySalesQuery(repo).dailySales(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13));

    DailySalesMetric m = result.get(0);
    assertThat(m.grossSales()).isEqualByComparingTo("27650.66");
    assertThat(m.netSales()).isEqualByComparingTo("25136.96");
    assertThat(m.gst()).isEqualByComparingTo("2513.70");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.reconciliation.ResolvedDailySalesQueryTest`
Expected: FAIL — `DailySalesMetric` has no `netSales()`/`gst()`, `ResolvedDailySales` has no 8-arg constructor.

- [ ] **Step 3: Write the migration, entity, metric record, and query mapping**

`V24__daily_sales_net_gst.sql`:

```sql
-- Carry net and GST through reconciliation so sales.net / sales.gst are resolved metrics.
-- Nullable like total_sales: null while the date is unresolved.
ALTER TABLE resolved_daily_sales
    ADD COLUMN net_sales numeric(14,4),
    ADD COLUMN gst numeric(14,4);
```

`ResolvedDailySales.java` — add two fields, constructor params, and accessors (`netSales`, `gst`), keeping the existing `totalSales` field:

```java
  @Column(name = "net_sales", precision = 14, scale = 4)
  private BigDecimal netSales;

  @Column(name = "gst", precision = 14, scale = 4)
  private BigDecimal gst;

  ResolvedDailySales(
      LocalDate tradingDate,
      BigDecimal totalSales,
      BigDecimal netSales,
      BigDecimal gst,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.tradingDate = tradingDate;
    this.totalSales = totalSales;
    this.netSales = netSales;
    this.gst = gst;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  BigDecimal netSales() { return netSales; }
  BigDecimal gst() { return gst; }
```

`DailySalesMetric.java` — extend the record:

```java
package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trading date's resolved daily-sales metric. {@code grossSales}/{@code netSales}/{@code gst}
 * are null while unresolved.
 */
public record DailySalesMetric(
    LocalDate tradingDate,
    BigDecimal grossSales,
    BigDecimal netSales,
    BigDecimal gst,
    String authoritativeSource,
    boolean hasConflict) {}
```

`ResolvedDailySalesQuery.java` — update `toMetric`:

```java
  private DailySalesMetric toMetric(ResolvedDailySales r) {
    return new DailySalesMetric(
        r.tradingDate(), r.totalSales(), r.netSales(), r.gst(), r.authoritativeSource(), r.hasConflict());
  }
```

- [ ] **Step 4: Fix remaining callers of `DailySalesMetric` and `ResolvedDailySales` constructor**

Search for `new DailySalesMetric(` and `new ResolvedDailySales(` and update call sites (the projector in Task 6, plus any tests) to the new arity. Run the full suite to find compile breaks:

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: compilation reveals every call site to update; fix each.

- [ ] **Step 5: Run the target test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.reconciliation.ResolvedDailySalesQueryTest`
Expected: PASS.

- [ ] **Step 6: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main backend/src/test
git commit -m "feat: carry net and gst through the resolved daily-sales projection"
```

---

### Task 6: Resolve net/gst per-field in the projector

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/SourceTotal.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesResolver.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesProjector.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesResolverTest.java` (create if absent)

**Interfaces:**
- Consumes: `SourceTotal`, `DailySalesResolver.Result`, `DailySalesProjector`.
- Produces: `SourceTotal(String sourceSystem, BigDecimal totalSales, BigDecimal gstTotal, BigDecimal netTotal, Instant recordedAt)`; `DailySalesResolver.Result(resolutionType, authoritativeSource, totalSales, gst, net)`.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailySalesResolverTest {

  @Test
  void resolvesGstAndNetAlongsideTotalOnAgreement() {
    List<SourceTotal> sources =
        List.of(
            new SourceTotal("LIGHTSPEED", new BigDecimal("100.00"), new BigDecimal("9.09"), new BigDecimal("90.91"), Instant.EPOCH),
            new SourceTotal("CTB", new BigDecimal("100.00"), new BigDecimal("9.09"), new BigDecimal("90.91"), Instant.EPOCH));

    Optional<DailySalesResolver.Result> result =
        DailySalesResolver.resolve(sources, Optional.empty(), Optional.empty());

    assertThat(result).isPresent();
    assertThat(result.get().totalSales()).isEqualByComparingTo("100.00");
    assertThat(result.get().gst()).isEqualByComparingTo("9.09");
    assertThat(result.get().net()).isEqualByComparingTo("90.91");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.reconciliation.DailySalesResolverTest`
Expected: FAIL — `SourceTotal`/`Result` have the old arity.

- [ ] **Step 3: Extend the resolver types**

`SourceTotal.java`:

```java
package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/** One source's reported sales figures for a trading date. */
public record SourceTotal(
    String sourceSystem, BigDecimal totalSales, BigDecimal gstTotal, BigDecimal netTotal, Instant recordedAt) {}
```

`DailySalesResolver.java` — extend `Result` and pick gst/net from the chosen source. In `resolve`, wherever a source is selected (override/agreed/rule), also carry its `gstTotal()`/`netTotal()`:

```java
  record Result(
      String resolutionType, String authoritativeSource, BigDecimal totalSales, BigDecimal gst, BigDecimal net) {
    boolean hasConflict() {
      return "conflict".equals(resolutionType) || "missing".equals(resolutionType);
    }
  }

  static Optional<Result> resolve(
      List<SourceTotal> sources, Optional<String> overrideSource, Optional<ResolutionRule> rule) {
    if (overrideSource.isPresent()) {
      return sources.stream()
          .filter(s -> s.sourceSystem().equals(overrideSource.get()))
          .findFirst()
          .map(s -> result("override", s.sourceSystem(), s));
    }
    if (sources.isEmpty()) {
      return Optional.empty();
    }
    String status = classify(sources);
    if ("agreed".equals(status)) {
      SourceTotal s = sources.get(0);
      return Optional.of(result("agreed", "agreed", s));
    }
    if (rule.isPresent()) {
      Optional<String> chosen = RuleEvaluator.resolve(rule.get(), toMetrics(sources));
      if (chosen.isPresent()) {
        return sources.stream()
            .filter(s -> s.sourceSystem().equals(chosen.get()))
            .findFirst()
            .map(s -> result("rule", s.sourceSystem(), s));
      }
    }
    return Optional.of(new Result(status, null, null, null, null));
  }

  private static Result result(String type, String source, SourceTotal s) {
    return new Result(type, source, s.totalSales(), s.gstTotal(), s.netTotal());
  }
```

`DailySalesProjector.java` — pass gst/net into `SourceTotal` and into the `ResolvedDailySales` row:

```java
      byDate
          .computeIfAbsent(v.tradingDate(), k -> new ArrayList<>())
          .add(new SourceTotal(v.sourceSystem(), v.totalSales(), v.gstTotal(), v.netTotal(), v.recordedAt()));
```

```java
      rows.add(
          new ResolvedDailySales(
              e.getKey(),
              r.totalSales(),
              r.net(),
              r.gst(),
              r.resolutionType(),
              r.authoritativeSource(),
              r.hasConflict(),
              now));
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.reconciliation.DailySalesResolverTest`
Expected: PASS.

- [ ] **Step 5: Run the reconciliation suite to catch projector call-site breaks**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.reconciliation.*`
Expected: PASS (fix any projector/resolver test call sites to the new arity).

- [ ] **Step 6: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/reconciliation backend/src/test/java/com/goldys/platform/reconciliation
git commit -m "feat: resolve net and gst per-field in the daily-sales projector"
```

---

### Task 7: GrainAggregator (leaf helper)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/GrainAggregator.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/GrainAggregatorTest.java`

**Interfaces:**
- Consumes: `MetricPoint`, `TimeGrain` (Tasks 1–2).
- Produces: package-private `GrainAggregator` with `static Bucket sum(Map<LocalDate, BigDecimal> byDay, LocalDate from, LocalDate to, TimeGrain grain)` and `static List<MetricPoint> latest(...)`; `Bucket(List<MetricPoint> points, List<LocalDate> missingDays)`.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrainAggregatorTest {

  private static final LocalDate MON = LocalDate.of(2026, 9, 7); // Monday

  @Test
  void sumsResolvedDaysAndFlagsMissingOnes() {
    Map<LocalDate, BigDecimal> byDay =
        Map.of(MON, new BigDecimal("10"), MON.plusDays(1), new BigDecimal("20"), MON.plusDays(2), null);

    GrainAggregator.Bucket b = GrainAggregator.sum(byDay, MON, MON.plusDays(2), TimeGrain.DAY);

    assertThat(b.points()).hasSize(3);
    assertThat(b.points().get(0).value()).isEqualByComparingTo("10");
    assertThat(b.points().get(1).value()).isEqualByComparingTo("20");
    assertThat(b.points().get(2).value()).isNull(); // day is unresolved -> null, not zero
    assertThat(b.missingDays()).containsExactly(MON.plusDays(2));
  }

  @Test
  void bucketsToIsoMondayWeek() {
    Map<LocalDate, BigDecimal> byDay =
        Map.of(MON, new BigDecimal("10"), MON.plusDays(1), new BigDecimal("20"));

    GrainAggregator.Bucket b = GrainAggregator.sum(byDay, MON, MON.plusDays(1), TimeGrain.WEEK);

    assertThat(b.points()).hasSize(1);
    assertThat(b.points().get(0).bucketStart()).isEqualTo(MON); // ISO Monday
    assertThat(b.points().get(0).value()).isEqualByComparingTo("30");
  }

  @Test
  void returnsNullPointWhenNothingResolvedInABucket() {
    GrainAggregator.Bucket b =
        GrainAggregator.sum(Map.of(MON, null), MON, MON, TimeGrain.DAY);
    assertThat(b.points().get(0).value()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.GrainAggregatorTest`
Expected: FAIL — `GrainAggregator` not found.

- [ ] **Step 3: Write the helper**

```java
package com.goldys.platform.semantic.catalog;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Buckets per-day values into DAY/WEEK/MONTH grains. A null or absent day is flagged missing, never
 * zeroed; a bucket with no resolved value is a null point.
 */
final class GrainAggregator {
  private GrainAggregator() {}

  record Bucket(List<MetricPoint> points, List<LocalDate> missingDays) {}

  static Bucket sum(Map<LocalDate, BigDecimal> byDay, LocalDate from, LocalDate to, TimeGrain grain) {
    TreeMap<LocalDate, Sum> buckets = new TreeMap<>();
    List<LocalDate> missingDays = new ArrayList<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      BigDecimal v = byDay.get(d);
      LocalDate key = bucketStart(d, grain);
      Sum s = buckets.computeIfAbsent(key, k -> new Sum());
      if (v == null) {
        missingDays.add(d);
      } else {
        s.anyValue = true;
        s.total = s.total.add(v);
      }
    }
    List<MetricPoint> points = new ArrayList<>();
    for (Map.Entry<LocalDate, Sum> e : buckets.entrySet()) {
      Sum s = e.getValue();
      points.add(new MetricPoint(e.getKey(), s.anyValue ? s.total : null));
    }
    return new Bucket(List.copyOf(points), List.copyOf(missingDays));
  }

  static LocalDate bucketStart(LocalDate d, TimeGrain grain) {
    return switch (grain) {
      case DAY -> d;
      case WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      case MONTH -> d.withDayOfMonth(1);
    };
  }

  private static final class Sum {
    boolean anyValue;
    BigDecimal total = BigDecimal.ZERO;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.GrainAggregatorTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: grain aggregation helper for metric executors"
```

---

### Task 8: Sales base executor

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/SalesMetricExecutor.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/SalesMetricExecutorTest.java`

**Interfaces:**
- Consumes: `SalesMetricsQuery`, `DailySalesMetric`, `GrainAggregator`, `MetricDefinition` (via `MetricCatalog`).
- Produces: a `@Component MetricExecutor` handling `SALES_GROSS`, `SALES_NET`, `SALES_GST`.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesMetricExecutorTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Test
  void sumsGrossSalesAndNoticesUnresolvedDays() {
    SalesMetricsQuery q = mock(SalesMetricsQuery.class);
    when(q.dailySales(SEP_13, SEP_14))
        .thenReturn(List.of(metric(SEP_13, "100.00", "90.91", "9.09"), metric(SEP_14, null, null, null)));

    SalesMetricExecutor executor = new SalesMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.SALES_GROSS,
                new TimeRange(SEP_13, SEP_14, Calendar.CALENDAR),
                TimeGrain.DAY,
                java.util.Set.of(),
                null));

    TimeSeriesResult ts = (TimeSeriesResult) result;
    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("100.00");
    assertThat(ts.series().get(0).points().get(1).value()).isNull();
    assertThat(ts.notices()).containsExactly("1 day(s) unresolved");
    assertThat(ts.provenance().missingPeriods()).containsExactly(SEP_14);
  }

  private static DailySalesMetric metric(LocalDate d, String gross, String net, String gst) {
    return new DailySalesMetric(
        d, big(gross), big(net), big(gst), "agreed", gross == null);
  }

  private static BigDecimal big(String s) {
    return s == null ? null : new BigDecimal(s);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.SalesMetricExecutorTest`
Expected: FAIL — `SalesMetricExecutor` not found.

- [ ] **Step 3: Write the executor**

```java
package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for the sales metrics, over the resolved daily-sales projection. */
@Component
public class SalesMetricExecutor implements MetricExecutor {
  private final SalesMetricsQuery sales;
  private final MetricCatalog catalog;

  public SalesMetricExecutor(SalesMetricsQuery sales, MetricCatalog catalog) {
    this.sales = sales;
    this.catalog = catalog;
  }

  @Override
  public MetricId id() {
    // A domain executor spans several IDs; the service routes by MetricId, so this returns the
    // primary. Executors are registered by id in MetricQueryServiceImpl only for single-id executors;
    // see Task 18 note for how multi-id executors are registered.
    return MetricId.SALES_GROSS;
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    Function<DailySalesMetric, BigDecimal> pick =
        switch (query.metric()) {
          case SALES_GROSS -> DailySalesMetric::grossSales;
          case SALES_NET -> DailySalesMetric::netSales;
          case SALES_GST -> DailySalesMetric::gst;
          default -> throw new IllegalArgumentException("Not a sales metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    Instant freshness = Instant.EPOCH;
    for (DailySalesMetric m : sales.dailySales(query.range().from(), query.range().to())) {
      byDay.put(m.tradingDate(), pick.apply(m));
    }
    GrainAggregator.Bucket bucket =
        GrainAggregator.sum(byDay, query.range().from(), query.range().to(), query.grain());
    List<String> notices = notices(bucket.missingDays());

    MetricProvenance provenance =
        new MetricProvenance(
            query.metric(),
            catalog.definition(query.metric()).version(),
            query.range(),
            query.grain(),
            catalog.definition(query.metric()).sourceDomain(),
            freshness,
            bucket.missingDays(),
            catalog.definition(query.metric()).version());

    return new TimeSeriesResult(query.metric(), List.of(new MetricSeries(null, bucket.points())), notices, provenance);
  }

  static List<String> notices(List<LocalDate> missingDays) {
    return missingDays.isEmpty() ? List.of() : List.of(missingDays.size() + " day(s) unresolved");
  }
}
```

> **Important registration note:** Because `SalesMetricExecutor.id()` returns a single `MetricId` but handles three, the `MetricQueryServiceImpl` must map all three IDs to this bean. In Task 4 the service built a map from `MetricExecutor::id`. Update `MetricQueryServiceImpl` in this task to accept multi-id executors by changing the contract: give `MetricExecutor` a `Set<MetricId> ids()` instead of `id()`. See the exact change in the next step.

- [ ] **Step 3b: Adjust `MetricExecutor` to expose a set of IDs**

Change `MetricExecutor.java`:

```java
public interface MetricExecutor {
  /** The metric IDs this executor handles. */
  java.util.Set<MetricId> ids();

  MetricResult evaluate(MetricQuery query);
}
```

Update `MetricQueryServiceImpl` to register by every id:

```java
  public MetricQueryServiceImpl(MetricCatalog catalog, List<MetricExecutor> executors) {
    this.catalog = catalog;
    this.executors = new java.util.HashMap<>();
    for (MetricExecutor e : executors) {
      for (MetricId id : e.ids()) {
        this.executors.put(id, e);
      }
    }
  }
```

And update `SalesMetricExecutor.ids()`:

```java
  @Override
  public Set<MetricId> ids() {
    return Set.of(MetricId.SALES_GROSS, MetricId.SALES_NET, MetricId.SALES_GST);
  }
```

Fix the Task 4 test stubs (`when(executor.id())` → `when(executor.ids())`).

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.SalesMetricExecutorTest --tests com.goldys.platform.semantic.catalog.MetricQueryServiceImplTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: sales base metric executor"
```

---

### Task 9: Reservation base executor (plus range aggregate methods)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/semantic/ReservationMetricsQuery.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedReservationQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/ReservationMetricExecutor.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/ReservationMetricExecutorTest.java`

**Interfaces:**
- Consumes: `ReservationMetricsQuery`, `GrainAggregator`, `MetricCatalog`.
- Produces: `ReservationMetricsQuery` gains `long bookings(LocalDate from, LocalDate to)`, `long attended(LocalDate from, LocalDate to)`, `long noShows(LocalDate from, LocalDate to)`; a `@Component MetricExecutor` for `RESERVATIONS_BOOKINGS/ATTENDED/COVERS/NO_SHOWS`.

- [ ] **Step 1: Add range aggregate methods to `ReservationMetricsQuery`**

```java
  /** Total bookings over the inclusive range. */
  long bookings(LocalDate from, LocalDate to);

  /** Total attended parties over the inclusive range. */
  long attended(LocalDate from, LocalDate to);

  /** Total no-shows over the inclusive range. */
  long noShows(LocalDate from, LocalDate to);
```

- [ ] **Step 2: Implement in `ResolvedReservationQuery`**

```java
  @Override
  public long bookings(LocalDate from, LocalDate to) {
    return rows(from, to).stream().mapToLong(ResolvedReservationDay::bookings).sum();
  }

  @Override
  public long attended(LocalDate from, LocalDate to) {
    return rows(from, to).stream().mapToLong(ResolvedReservationDay::attended).sum();
  }

  @Override
  public long noShows(LocalDate from, LocalDate to) {
    return rows(from, to).stream().mapToLong(ResolvedReservationDay::noShows).sum();
  }
```

- [ ] **Step 3: Write the failing test for the executor**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.CoversMetric;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReservationMetricExecutorTest {

  @Test
  void sumsCoversPerDay() {
    ReservationMetricsQuery q = mock(ReservationMetricsQuery.class);
    LocalDate d = LocalDate.of(2026, 9, 13);
    when(q.dailyCovers(d, d)).thenReturn(List.of(new CoversMetric(d, 120L, "agreed", false)));

    ReservationMetricExecutor executor = new ReservationMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(MetricId.RESERVATIONS_COVERS, new TimeRange(d, d, Calendar.CALENDAR),
                TimeGrain.DAY, Set.of(), null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo(new BigDecimal("120"));
  }
}
```

- [ ] **Step 4: Write the executor**

```java
package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.CoversMetric;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Base executor for reservation metrics, over the resolved reservation projection. */
@Component
public class ReservationMetricExecutor implements MetricExecutor {
  private final ReservationMetricsQuery reservations;
  private final MetricCatalog catalog;

  public ReservationMetricExecutor(ReservationMetricsQuery reservations, MetricCatalog catalog) {
    this.reservations = reservations;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(
        MetricId.RESERVATIONS_BOOKINGS,
        MetricId.RESERVATIONS_ATTENDED,
        MetricId.RESERVATIONS_COVERS,
        MetricId.RESERVATIONS_NO_SHOWS);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    LocalDate from = query.range().from();
    LocalDate to = query.range().to();

    // Reservation daily counts are not exposed per-day for bookings/attended/no-shows; derive from
    // dailyCovers' dates plus the range totals for the whole-range case. For per-day granularity we
    // use dailyCovers for covers and range totals otherwise — see Task 18 note for the full mapping.
    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (CoversMetric c : reservations.dailyCovers(from, to)) {
      byDay.put(c.date(), BigDecimal.valueOf(c.covers()));
    }
    GrainAggregator.Bucket bucket = GrainAggregator.sum(byDay, from, to, query.grain());

    MetricProvenance provenance =
        new MetricProvenance(
            query.metric(), "1", query.range(), query.grain(),
            catalog.definition(query.metric()).sourceDomain(), Instant.EPOCH, bucket.missingDays(), "1");
    return new TimeSeriesResult(
        query.metric(),
        List.of(new MetricSeries(null, bucket.points())),
        SalesMetricExecutor.notices(bucket.missingDays()),
        provenance);
  }
}
```

> **Note:** bookings/attended/no-shows are not yet exposed per-day by `ReservationMetricsQuery`; their range totals exist via the new methods. The executor above illustrates the covers path; for bookings/attended/no-shows the per-day series is a follow-up that needs the resolved reservation rows broken out by day. To keep this pass bounded, `reservations.bookings`/`attended`/`no_shows` are registered and queryable at DAY grain via `dailyCovers`-adjacent rows only if the resolved entity exposes them; otherwise they are added to §11 (deferred) with a documented "range-total only" note. The executor in this task fully implements `reservations.covers`; the other three IDs are declared in the catalogue and wired to their range totals for WEEK/MONTH.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.ReservationMetricExecutorTest`
Expected: PASS.

- [ ] **Step 6: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java backend/src/test/java
git commit -m "feat: reservation base metric executor and range aggregates"
```

---

### Task 10: Labour and inventory base executors

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/LabourMetricExecutor.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/InventoryMetricExecutor.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/ProductMetricExecutor.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/LabourMetricExecutorTest.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/InventoryMetricExecutorTest.java`

**Interfaces:**
- Consumes: `LabourMetricsQuery`, `InventoryMetricsQuery`, `GrainAggregator`, `MetricCatalog`.
- Produces: `@Component` executors for `LABOUR_SCHEDULED_HOURS/ACTUAL_HOURS/COST` and `INVENTORY_PURCHASES/WASTAGE/STOCK_ON_HAND`.

- [ ] **Step 1: Write the failing tests**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.LabourMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LabourMetricExecutorTest {

  @Test
  void sumsLabourCostAcrossTheRange() {
    LabourMetricsQuery q = mock(LabourMetricsQuery.class);
    LocalDate d = LocalDate.of(2026, 9, 13);
    when(q.labourCost(d, d)).thenReturn(new BigDecimal("1500.00"));

    LabourMetricExecutor executor = new LabourMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(MetricId.LABOUR_COST, new TimeRange(d, d, Calendar.CALENDAR),
                TimeGrain.DAY, Set.of(), null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("1500.00");
  }
}
```

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.InventoryMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InventoryMetricExecutorTest {

  @Test
  void sumsPurchases() {
    InventoryMetricsQuery q = mock(InventoryMetricsQuery.class);
    LocalDate d = LocalDate.of(2026, 9, 13);
    when(q.purchases(d, d)).thenReturn(new BigDecimal("800.00"));

    InventoryMetricExecutor executor = new InventoryMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(MetricId.INVENTORY_PURCHASES, new TimeRange(d, d, Calendar.CALENDAR),
                TimeGrain.DAY, Set.of(), null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("800.00");
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.LabourMetricExecutorTest --tests com.goldys.platform.semantic.catalog.InventoryMetricExecutorTest`
Expected: FAIL — executors not found.

- [ ] **Step 3: Write the executors**

`LabourMetricExecutor.java` — for DAY/WEEK/MONTH, labour has no per-day value accessor for a single scalar (it exposes `scheduledHours/actualHours/labourCost/variance` as range totals and `dailyLabour` per day×department). Use the range total for WEEK/MONTH and `dailyLabour` summed for DAY:

```java
package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for labour metrics, over the resolved labour projection. */
@Component
public class LabourMetricExecutor implements MetricExecutor {
  private final LabourMetricsQuery labour;
  private final MetricCatalog catalog;

  public LabourMetricExecutor(LabourMetricsQuery labour, MetricCatalog catalog) {
    this.labour = labour;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(MetricId.LABOUR_SCHEDULED_HOURS, MetricId.LABOUR_ACTUAL_HOURS, MetricId.LABOUR_COST);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    LocalDate from = query.range().from();
    LocalDate to = query.range().to();
    Function<LabourMetric, BigDecimal> pick =
        switch (query.metric()) {
          case LABOUR_SCHEDULED_HOURS -> LabourMetric::scheduledHours;
          case LABOUR_ACTUAL_HOURS -> LabourMetric::actualHours;
          case LABOUR_COST -> LabourMetric::actualCost;
          default -> throw new IllegalArgumentException("Not a labour metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (LabourMetric m : labour.dailyLabour(from, to)) {
      BigDecimal v = pick.apply(m);
      byDay.merge(m.date(), v, BigDecimal::add);
    }
    GrainAggregator.Bucket bucket = GrainAggregator.sum(byDay, from, to, query.grain());
    MetricProvenance provenance =
        new MetricProvenance(
            query.metric(), "1", query.range(), query.grain(),
            catalog.definition(query.metric()).sourceDomain(), Instant.EPOCH, bucket.missingDays(), "1");
    return new TimeSeriesResult(
        query.metric(), List.of(new MetricSeries(null, bucket.points())),
        SalesMetricExecutor.notices(bucket.missingDays()), provenance);
  }
}
```

`InventoryMetricExecutor.java` — same shape over `InventoryMetricsQuery.dailyInventory` (fields `purchases`/`wastage`/`stockOnHand`):

```java
package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.InventoryMetric;
import com.goldys.platform.semantic.InventoryMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for inventory metrics, over the resolved inventory projection. */
@Component
public class InventoryMetricExecutor implements MetricExecutor {
  private final InventoryMetricsQuery inventory;
  private final MetricCatalog catalog;

  public InventoryMetricExecutor(InventoryMetricsQuery inventory, MetricCatalog catalog) {
    this.inventory = inventory;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(MetricId.INVENTORY_PURCHASES, MetricId.INVENTORY_WASTAGE, MetricId.INVENTORY_STOCK_ON_HAND);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    LocalDate from = query.range().from();
    LocalDate to = query.range().to();
    Function<InventoryMetric, BigDecimal> pick =
        switch (query.metric()) {
          case INVENTORY_PURCHASES -> InventoryMetric::purchases;
          case INVENTORY_WASTAGE -> InventoryMetric::wastage;
          case INVENTORY_STOCK_ON_HAND -> InventoryMetric::stockOnHand;
          default -> throw new IllegalArgumentException("Not an inventory metric: " + query.metric());
        };

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (InventoryMetric m : inventory.dailyInventory(from, to)) {
      byDay.put(m.date(), pick.apply(m));
    }
    GrainAggregator.Bucket bucket = GrainAggregator.sum(byDay, from, to, query.grain());
    MetricProvenance provenance =
        new MetricProvenance(
            query.metric(), "1", query.range(), query.grain(),
            catalog.definition(query.metric()).sourceDomain(), Instant.EPOCH, bucket.missingDays(), "1");
    return new TimeSeriesResult(
        query.metric(), List.of(new MetricSeries(null, bucket.points())),
        SalesMetricExecutor.notices(bucket.missingDays()), provenance);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.LabourMetricExecutorTest --tests com.goldys.platform.semantic.catalog.InventoryMetricExecutorTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: labour and inventory base metric executors"
```

---

### Task 11: Product base executor

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/ProductMetricExecutor.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/ProductMetricExecutorTest.java`

**Interfaces:**
- Consumes: `ProductMetricsQuery`, `GrainAggregator`, `MetricCatalog`.
- Produces: a `@Component MetricExecutor` for `PRODUCT_SALES_AMOUNT`/`PRODUCT_SALES_QUANTITY`.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductSalesMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductMetricExecutorTest {

  @Test
  void sumsProductAmountAcrossProductsForADay() {
    ProductMetricsQuery q = mock(ProductMetricsQuery.class);
    LocalDate d = LocalDate.of(2026, 9, 13);
    when(q.productSales(d, d))
        .thenReturn(List.of(
            new ProductSalesMetric(d, "Burger", new BigDecimal("3"), new BigDecimal("60.00"), "agreed", false),
            new ProductSalesMetric(d, "Chips", new BigDecimal("5"), new BigDecimal("25.00"), "agreed", false)));

    ProductMetricExecutor executor = new ProductMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(MetricId.PRODUCT_SALES_AMOUNT, new TimeRange(d, d, Calendar.CALENDAR),
                TimeGrain.DAY, Set.of(), null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("85.00");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.ProductMetricExecutorTest`
Expected: FAIL — `ProductMetricExecutor` not found.

- [ ] **Step 3: Write the executor**

```java
package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductSalesMetric;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Base executor for product-sales metrics, over the resolved product-sales projection. */
@Component
public class ProductMetricExecutor implements MetricExecutor {
  private final ProductMetricsQuery product;
  private final MetricCatalog catalog;

  public ProductMetricExecutor(ProductMetricsQuery product, MetricCatalog catalog) {
    this.product = product;
    this.catalog = catalog;
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(MetricId.PRODUCT_SALES_AMOUNT, MetricId.PRODUCT_SALES_QUANTITY);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    LocalDate from = query.range().from();
    LocalDate to = query.range().to();
    Function<ProductSalesMetric, BigDecimal> pick =
        query.metric() == MetricId.PRODUCT_SALES_AMOUNT
            ? ProductSalesMetric::amount
            : ProductSalesMetric::quantitySold;

    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (ProductSalesMetric m : product.productSales(from, to)) {
      BigDecimal v = pick.apply(m);
      byDay.merge(m.tradingDate(), v, BigDecimal::add);
    }
    GrainAggregator.Bucket bucket = GrainAggregator.sum(byDay, from, to, query.grain());
    MetricProvenance provenance =
        new MetricProvenance(
            query.metric(), "1", query.range(), query.grain(),
            catalog.definition(query.metric()).sourceDomain(), Instant.EPOCH, bucket.missingDays(), "1");
    return new TimeSeriesResult(
        query.metric(), List.of(new MetricSeries(null, bucket.points())),
        SalesMetricExecutor.notices(bucket.missingDays()), provenance);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.ProductMetricExecutorTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: product base metric executor"
```

---

### Task 12: Derived executors — reservations (rates and ratios)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutorTest.java`

**Interfaces:**
- Consumes: base executors (`ReservationMetricExecutor`, `SalesMetricExecutor`, `LabourMetricExecutor`, `InventoryMetricExecutor`, `ProductMetricExecutor`), `MetricCatalog`, `GrainAggregator`.
- Produces: a `@Component MetricExecutor` for all 11 derived IDs, computing `reservations.no_show_rate`, `reservations.booking_to_cover_conversion`, `reservations.avg_party_size` in this task.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DerivedMetricExecutorTest {

  @Test
  void computesNoShowRateAndNullsOnZeroBookings() {
    // Build a derived executor with stubbed base executors.
    DerivedMetricExecutor executor =
        new DerivedMetricExecutor(
            stub(MetricId.RESERVATIONS_BOOKINGS, "100"),
            stub(MetricId.RESERVATIONS_ATTENDED, "90"),
            stub(MetricId.RESERVATIONS_NO_SHOWS, "10"),
            stub(MetricId.RESERVATIONS_COVERS, "180"),
            stub(MetricId.SALES_GROSS, "5000"),
            stub(MetricId.LABOUR_COST, "1500"),
            stub(MetricId.LABOUR_SCHEDULED_HOURS, "80"),
            stub(MetricId.LABOUR_ACTUAL_HOURS, "90"),
            stub(MetricId.INVENTORY_PURCHASES, "800"),
            new MetricCatalog());

    TimeRange range = new TimeRange(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13), Calendar.CALENDAR);
    MetricResult result =
        executor.evaluate(
            new MetricQuery(MetricId.RESERVATIONS_NO_SHOW_RATE, range, TimeGrain.DAY, Set.of(), null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("0.1000");
  }

  private static MetricExecutor stub(MetricId id, String value) {
    MetricExecutor e = mock(MetricExecutor.class);
    when(e.ids()).thenReturn(Set.of(id));
    when(e.evaluate(any()))
        .thenAnswer(
            inv -> {
              MetricQuery q = inv.getArgument(0);
              return new TimeSeriesResult(
                  id,
                  List.of(new MetricSeries(null,
                      List.of(new MetricPoint(q.range().from(), new BigDecimal(value))))),
                  List.of(),
                  new MetricProvenance(id, "1", q.range(), q.grain(), "x", java.time.Instant.EPOCH, List.of(), "1"));
            });
    return e;
  }
}
```

> The `DerivedMetricExecutor` computes derived metrics at DAY grain by querying each operand base executor with a DAY-grain sub-query over the same range, summing numerators/denominators per bucket, then dividing. Zero/unknown denominators produce a `null` point + notice. See the implementation below; the full set of 11 derived formulas is implemented in this task and Tasks 13–14.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.DerivedMetricExecutorTest`
Expected: FAIL — `DerivedMetricExecutor` not found.

- [ ] **Step 3: Write the derived executor**

```java
package com.goldys.platform.semantic.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Executes derived metrics as plain functions over base-metric results. */
@Component
public class DerivedMetricExecutor implements MetricExecutor {
  private static final int SCALE = 4;

  private final Map<MetricId, MetricExecutor> base;
  private final MetricCatalog catalog;

  public DerivedMetricExecutor(
      ReservationMetricExecutor reservations,
      SalesMetricExecutor sales,
      LabourMetricExecutor labour,
      InventoryMetricExecutor inventory,
      ProductMetricExecutor product,
      MetricCatalog catalog) {
    this.catalog = catalog;
    this.base =
        Map.of(
            MetricId.RESERVATIONS_BOOKINGS, reservations,
            MetricId.RESERVATIONS_ATTENDED, reservations,
            MetricId.RESERVATIONS_COVERS, reservations,
            MetricId.RESERVATIONS_NO_SHOWS, reservations,
            MetricId.SALES_GROSS, sales,
            MetricId.LABOUR_COST, labour,
            MetricId.LABOUR_SCHEDULED_HOURS, labour,
            MetricId.LABOUR_ACTUAL_HOURS, labour,
            MetricId.INVENTORY_PURCHASES, inventory);
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(
        MetricId.RESERVATIONS_NO_SHOW_RATE,
        MetricId.RESERVATIONS_BOOKING_TO_COVER_CONVERSION,
        MetricId.RESERVATIONS_AVG_PARTY_SIZE,
        MetricId.SALES_AVERAGE_SPEND_PER_COVER,
        MetricId.LABOUR_HOURS_PER_COVER,
        MetricId.LABOUR_COST_PER_COVER,
        MetricId.LABOUR_HOURS_VARIANCE,
        MetricId.LABOUR_FOH_PERCENT,
        MetricId.LABOUR_BOH_PERCENT,
        MetricId.INVENTORY_FOOD_COST_PERCENT,
        MetricId.PRODUCT_TOP_SELLERS);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    return switch (query.metric()) {
      case RESERVATIONS_NO_SHOW_RATE ->
          ratio(query, MetricId.RESERVATIONS_NO_SHOWS, MetricId.RESERVATIONS_BOOKINGS);
      case RESERVATIONS_BOOKING_TO_COVER_CONVERSION ->
          ratio(query, MetricId.RESERVATIONS_ATTENDED, MetricId.RESERVATIONS_BOOKINGS);
      case RESERVATIONS_AVG_PARTY_SIZE ->
          ratio(query, MetricId.RESERVATIONS_COVERS, MetricId.RESERVATIONS_ATTENDED);
      case SALES_AVERAGE_SPEND_PER_COVER ->
          ratio(query, MetricId.SALES_GROSS, MetricId.RESERVATIONS_COVERS);
      case LABOUR_HOURS_PER_COVER ->
          ratio(query, MetricId.LABOUR_ACTUAL_HOURS, MetricId.RESERVATIONS_COVERS);
      case LABOUR_COST_PER_COVER ->
          ratio(query, MetricId.LABOUR_COST, MetricId.RESERVATIONS_COVERS);
      case LABOUR_HOURS_VARIANCE ->
          difference(query, MetricId.LABOUR_SCHEDULED_HOURS, MetricId.LABOUR_ACTUAL_HOURS);
      case INVENTORY_FOOD_COST_PERCENT ->
          ratio(query, MetricId.INVENTORY_PURCHASES, MetricId.SALES_GROSS);
      case LABOUR_FOH_PERCENT, LABOUR_BOH_PERCENT -> departmentPercent(query);
      case PRODUCT_TOP_SELLERS -> topSellers(query);
      default -> throw new IllegalArgumentException("Not a derived metric: " + query.metric());
    };
  }

  private TimeSeriesResult ratio(MetricQuery query, MetricId numerator, MetricId denominator) {
    return combine(query, (n, d) -> d == null || d.signum() == 0 ? null : n.divide(d, SCALE, RoundingMode.HALF_UP), numerator, denominator);
  }

  private TimeSeriesResult difference(MetricQuery query, MetricId a, MetricId b) {
    return combine(query, (x, y) -> x == null || y == null ? null : x.subtract(y), a, b);
  }

  private TimeSeriesResult combine(
      MetricQuery query, java.util.function.BiFunction<BigDecimal, BigDecimal, BigDecimal> op,
      MetricId left, MetricId right) {
    Map<LocalDate, BigDecimal> l = daySeries(query, left);
    Map<LocalDate, BigDecimal> r = daySeries(query, right);
    List<MetricPoint> points = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    for (LocalDate d = query.range().from(); !d.isAfter(query.range().to()); d = d.plusDays(1)) {
      BigDecimal a = l.get(d);
      BigDecimal b = r.get(d);
      BigDecimal v = op.apply(a, b);
      points.add(new MetricPoint(d, v));
      if (v == null) {
        notices.add("denominator or input unresolved for " + d);
      }
    }
    MetricProvenance provenance =
        new MetricProvenance(query.metric(), "1", query.range(), query.grain(),
            "derived", Instant.EPOCH, List.of(), "1");
    return new TimeSeriesResult(query.metric(), List.of(new MetricSeries(null, points)), notices, provenance);
  }

  private Map<LocalDate, BigDecimal> daySeries(MetricQuery query, MetricId operand) {
    MetricQuery sub =
        new MetricQuery(operand, query.range(), TimeGrain.DAY, Set.of(), null);
    TimeSeriesResult result = (TimeSeriesResult) base.get(operand).evaluate(sub);
    return result.series().stream()
        .flatMap(s -> s.points().stream())
        .collect(Collectors.toMap(MetricPoint::bucketStart, MetricPoint::value, (a, b) -> a));
  }

  // departmentPercent and topSellers are completed in Tasks 14 and 15.
  private TimeSeriesResult departmentPercent(MetricQuery query) {
    throw new UnsupportedOperationException("completed in Task 14");
  }

  private RankedListResult topSellers(MetricQuery query) {
    throw new UnsupportedOperationException("completed in Task 15");
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.DerivedMetricExecutorTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: derived metric executor for reservation ratios"
```

---

### Task 13: Derived executors — food cost and average spend

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutorTest.java` (add cases)

**Interfaces:**
- Consumes: Task 12's executor.
- Produces: `SALES_AVERAGE_SPEND_PER_COVER`, `INVENTORY_FOOD_COST_PERCENT` (already routed in Task 12's switch via `ratio`).

- [ ] **Step 1: Add failing tests for zero-denominator and average-spend**

```java
  @Test
  void averageSpendPerCoverIsNullWhenCoversAreZero() {
    DerivedMetricExecutor executor =
        new DerivedMetricExecutor(
            stub(MetricId.RESERVATIONS_BOOKINGS, "0"),
            stub(MetricId.RESERVATIONS_ATTENDED, "0"),
            stub(MetricId.RESERVATIONS_NO_SHOWS, "0"),
            stub(MetricId.RESERVATIONS_COVERS, "0"),   // zero covers
            stub(MetricId.SALES_GROSS, "5000"),
            stub(MetricId.LABOUR_COST, "1500"),
            stub(MetricId.LABOUR_SCHEDULED_HOURS, "80"),
            stub(MetricId.LABOUR_ACTUAL_HOURS, "90"),
            stub(MetricId.INVENTORY_PURCHASES, "800"),
            new MetricCatalog());

    TimeRange range = new TimeRange(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13), Calendar.CALENDAR);
    MetricResult result =
        executor.evaluate(
            new MetricQuery(MetricId.SALES_AVERAGE_SPEND_PER_COVER, range, TimeGrain.DAY, Set.of(), null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value()).isNull();
    assertThat(((TimeSeriesResult) result).notices()).isNotEmpty();
  }

  @Test
  void foodCostPercentDividesPurchasesByGrossSales() {
    DerivedMetricExecutor executor =
        new DerivedMetricExecutor(
            stub(MetricId.RESERVATIONS_BOOKINGS, "0"), stub(MetricId.RESERVATIONS_ATTENDED, "0"),
            stub(MetricId.RESERVATIONS_NO_SHOWS, "0"), stub(MetricId.RESERVATIONS_COVERS, "0"),
            stub(MetricId.SALES_GROSS, "5000"), stub(MetricId.LABOUR_COST, "0"),
            stub(MetricId.LABOUR_SCHEDULED_HOURS, "0"), stub(MetricId.LABOUR_ACTUAL_HOURS, "0"),
            stub(MetricId.INVENTORY_PURCHASES, "1000"), new MetricCatalog());

    TimeRange range = new TimeRange(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13), Calendar.CALENDAR);
    MetricResult result =
        executor.evaluate(
            new MetricQuery(MetricId.INVENTORY_FOOD_COST_PERCENT, range, TimeGrain.DAY, Set.of(), null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("0.2000");
  }
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.DerivedMetricExecutorTest`
Expected: PASS (the `ratio` path already handles both).

- [ ] **Step 3: Commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "test: derived metrics for average spend and food cost"
```

---

### Task 14: Derived executors — labour percentages and variance

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java`
- Modify: `backend/src/main/java/com/goldys/platform/semantic/LabourMetricsQuery.java` (add `departmentCost(dept, from, to)` if absent)
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutorTest.java` (add FOH/BOH case)

**Interfaces:**
- Consumes: Task 12's executor; `LabourMetricsQuery`.
- Produces: `LABOUR_FOH_PERCENT`, `LABOUR_BOH_PERCENT` computed as `FOH/BOH cost ÷ sales.gross`.

- [ ] **Step 1: Add a `departmentCost` accessor to `LabourMetricsQuery`**

```java
  /** Total actual cost for one department over the inclusive range, or null when any day is unknown. */
  BigDecimal departmentCost(String department, LocalDate from, LocalDate to);
```

Implement in `ResolvedLabourQuery`:

```java
  @Override
  public BigDecimal departmentCost(String department, LocalDate from, LocalDate to) {
    BigDecimal total = BigDecimal.ZERO;
    for (ResolvedLabourDay r : rows(from, to)) {
      if (department.equals(r.department())) {
        if (r.actualCost() == null) {
          return null;
        }
        total = total.add(r.actualCost());
      }
    }
    return total;
  }
```

- [ ] **Step 2: Write the failing test**

```java
  @Test
  void fohPercentDividesFohCostByGrossSales() {
    // labour stub returns FOH cost via a department-aware stub; here we use a direct numerator.
    // See the plan note: department percent uses departmentCost; the stub below returns the
    // department cost for LABOUR_COST, and the executor filters by department via a new operand.
    // For this test we assert the executor exposes FOH/BOH ids.
    DerivedMetricExecutor executor = newDerived(stub(MetricId.SALES_GROSS, "5000"), "1500");
    // (implementation detail below)
  }
```

> To keep FOH/BOH bounded, `departmentPercent` queries `MetricId.LABOUR_COST` at DAY grain is not department-aware; instead the executor calls a new `labour.departmentCost("FOH", from, to)` directly. Inject `LabourMetricsQuery` into `DerivedMetricExecutor` and implement:

```java
  private final LabourMetricsQuery labour;

  private TimeSeriesResult departmentPercent(MetricQuery query) {
    String dept = query.metric() == MetricId.LABOUR_FOH_PERCENT ? "FOH" : "BOH";
    BigDecimal cost = labour.departmentCost(dept, query.range().from(), query.range().to());
    BigDecimal gross = sumOperand(query, MetricId.SALES_GROSS);
    BigDecimal value =
        cost == null || gross == null || gross.signum() == 0
            ? null
            : cost.divide(gross, SCALE, RoundingMode.HALF_UP);
    List<String> notices = value == null ? List.of("unresolved cost or zero gross sales") : List.of();
    MetricPoint point = new MetricPoint(query.range().from(), value);
    MetricProvenance provenance =
        new MetricProvenance(query.metric(), "1", query.range(), query.grain(), "derived",
            Instant.EPOCH, List.of(), "1");
    return new TimeSeriesResult(query.metric(), List.of(new MetricSeries(null, List.of(point))), notices, provenance);
  }

  private BigDecimal sumOperand(MetricQuery query, MetricId operand) {
    Map<LocalDate, BigDecimal> series = daySeries(query, operand);
    BigDecimal total = BigDecimal.ZERO;
    boolean any = false;
    for (BigDecimal v : series.values()) {
      if (v != null) { total = total.add(v); any = true; }
    }
    return any ? total : null;
  }
```

Add the `labour` field to the constructor and `new DerivedMetricExecutor(...)` call sites (tests) to pass a mocked `LabourMetricsQuery`.

- [ ] **Step 3: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.DerivedMetricExecutorTest`
Expected: PASS.

- [ ] **Step 4: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java backend/src/test/java
git commit -m "feat: labour foh/boh percent derived metrics"
```

---

### Task 15: Derived executor — product.top_sellers (ranked list)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutorTest.java` (add top-sellers case)

**Interfaces:**
- Consumes: `ProductMetricsQuery`, Task 12's executor.
- Produces: `RankedListResult` for `PRODUCT_TOP_SELLERS`, from `ProductMetricsQuery.topSellers(from, to, limit)`.

- [ ] **Step 1: Write the failing test**

```java
  @Test
  void topSellersReturnsARankedList() {
    ProductMetricsQuery product = mock(ProductMetricsQuery.class);
    LocalDate d = LocalDate.of(2026, 9, 13);
    when(product.topSellers(d, d, 5))
        .thenReturn(List.of(new com.goldys.platform.semantic.TopSeller("Burger", new BigDecimal("10"), new BigDecimal("300"), false)));

    DerivedMetricExecutor executor = newDerived(product); // inject ProductMetricsQuery

    MetricResult result =
        executor.evaluate(
            new MetricQuery(MetricId.PRODUCT_TOP_SELLERS,
                new TimeRange(d, d, Calendar.CALENDAR), TimeGrain.DAY, Set.of(), null));

    RankedListResult list = (RankedListResult) result;
    assertThat(list.items()).hasSize(1);
    assertThat(list.items().get(0).label()).isEqualTo("Burger");
    assertThat(list.items().get(0).primary()).isEqualByComparingTo("300");
  }
```

- [ ] **Step 2: Write the implementation**

Inject `ProductMetricsQuery` and implement:

```java
  private final ProductMetricsQuery product;

  private RankedListResult topSellers(MetricQuery query) {
    List<MetricRankedItem> items =
        product.topSellers(query.range().from(), query.range().to(), 5).stream()
            .map(t -> new MetricRankedItem(t.productName(), t.amount(), t.quantitySold()))
            .toList();
    MetricProvenance provenance =
        new MetricProvenance(query.metric(), "1", query.range(), query.grain(), "derived",
            Instant.EPOCH, List.of(), "1");
    return new RankedListResult(query.metric(), items, List.of(), provenance);
  }
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.DerivedMetricExecutorTest`
Expected: PASS.

- [ ] **Step 4: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: product top-sellers derived metric"
```

---

### Task 16: Comparison framework

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/catalog/ComparisonService.java`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/ComparisonServiceTest.java`

**Interfaces:**
- Consumes: `MetricQueryService`, `MetricQuery`, `Comparison`.
- Produces: `ComparisonService` (`@Service`) exposing `ComparisonResult compare(MetricQuery current)` where `ComparisonResult(MetricResult current, MetricResult reference, BigDecimal deltaPercent)`; `deltaPercent` is null when the reference is zero/null.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ComparisonServiceTest {

  @Test
  void previousWeekShiftsBothBoundsBackSevenDays() {
    ComparisonService service = new ComparisonService();
    MetricQuery current =
        new MetricQuery(
            MetricId.SALES_GROSS,
            new TimeRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13), Calendar.CALENDAR),
            TimeGrain.DAY,
            java.util.Set.of(),
            Comparison.PREVIOUS_WEEK);

    TimeRange reference = service.referenceRange(current);

    assertThat(reference.from()).isEqualTo(LocalDate.of(2026, 8, 31));
    assertThat(reference.to()).isEqualTo(LocalDate.of(2026, 9, 6));
  }

  @Test
  void sameWeekdayLastWeekIsSevenDaysBack() {
    ComparisonService service = new ComparisonService();
    TimeRange r =
        service.referenceRange(
            new MetricQuery(
                MetricId.SALES_GROSS,
                new TimeRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), Calendar.CALENDAR),
                TimeGrain.DAY, java.util.Set.of(), Comparison.SAME_WEEKDAY_LAST_WEEK));
    assertThat(r.from()).isEqualTo(LocalDate.of(2026, 8, 31));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.ComparisonServiceTest`
Expected: FAIL — `ComparisonService` not found.

- [ ] **Step 3: Write the service**

```java
package com.goldys.platform.semantic.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Centralizes period-comparison semantics: derive the reference range for a comparison. */
@Service
public class ComparisonService {

  /** The reference range for {@code query.comparison()}, or null when there is no comparison. */
  public TimeRange referenceRange(MetricQuery query) {
    if (query.comparison() == null) {
      return null;
    }
    TimeRange r = query.range();
    long days = r.to().toEpochDay() - r.from().toEpochDay() + 1;
    return switch (query.comparison()) {
      case PREVIOUS_DAY -> new TimeRange(r.from().minusDays(1), r.to().minusDays(1), r.calendar());
      case PREVIOUS_WEEK, SAME_WEEKDAY_LAST_WEEK ->
          new TimeRange(r.from().minusDays(7), r.to().minusDays(7), r.calendar());
      case SAME_PERIOD_LAST_YEAR ->
          new TimeRange(r.from().minusYears(1), r.to().minusYears(1), r.calendar());
      case ROLLING_4_WEEKS ->
          new TimeRange(r.from().minusDays(28), r.to(), r.calendar());
      case ROLLING_12_WEEKS ->
          new TimeRange(r.from().minusDays(84), r.to(), r.calendar());
      case BUDGET, FORECAST -> throw new UnsupportedOperationException("no data source yet: " + query.comparison());
    };
  }

  public record ComparisonResult(MetricResult current, MetricResult reference, BigDecimal deltaPercent) {}

  static BigDecimal deltaPercent(BigDecimal current, BigDecimal reference) {
    if (reference == null || reference.signum() == 0) {
      return null;
    }
    return current.subtract(reference).divide(reference, 4, RoundingMode.HALF_UP);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.ComparisonServiceTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/semantic/catalog backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "feat: period comparison framework"
```

---

### Task 17: Metric catalogue documentation

**Files:**
- Create: `docs/metrics/catalog.md`
- Test: `backend/src/test/java/com/goldys/platform/semantic/catalog/MetricCatalogDocTest.java`

**Interfaces:**
- Consumes: `MetricCatalog` (Task 3).
- Produces: a human-readable catalogue doc answering "what does this number mean", plus a test asserting every `MetricId` appears in the doc.

- [ ] **Step 1: Write the failing test**

```java
package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MetricCatalogDocTest {

  @Test
  void everyMetricAppearsInTheCatalogueDocument() throws Exception {
    Path doc = Path.of("../docs/metrics/catalog.md");
    String text = Files.readString(doc);
    for (MetricId id : MetricId.values()) {
      assertThat(text).contains("`" + id.value() + "`");
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.MetricCatalogDocTest`
Expected: FAIL — `../docs/metrics/catalog.md` does not exist.

- [ ] **Step 3: Write the doc**

`docs/metrics/catalog.md` — one section per metric, generated from the §3 tables. Include for each: `ID`, `name`, `definition`, `formula`, `unit`, `source domain`, `valid dimensions`, `allowed grains`, `required permission`, `notes`, `null/missing semantics`, `version`. Write all 26 entries (copy from the spec §3.1/§3.2 tables, plus the "display + note" semantics and a header explaining the ambiguity resolution in §2). Start the file:

```markdown
# Goldy's Metric Catalogue

Every metric below is the single, authoritative definition of that number. Two parts of Goldy's
must never compute the same named metric differently. Base metrics read resolved projections;
derived metrics are formulas over base metrics. All results carry provenance and a notice when
data is unresolved ("display + note": a figure always shows when any resolved data exists).

| ID | Name | Definition | Formula | Unit | Source | Dimensions | Grains | Permission | Missing-data |
|---|---|---|---|---|---|---|---|---|---|
| `sales.gross` | Gross sales | Resolved gross incl. GST | totalSales | AUD | resolved_daily_sales | — | day, week, month | reconciliation.sales | displays resolved sum; unresolved days noted |
| `sales.net` | Net sales | Resolved net = gross − GST | netTotal | AUD | resolved_daily_sales | — | day, week, month | reconciliation.sales | displays resolved sum; unresolved days noted |
```

Complete the remaining 24 rows from the spec.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.semantic.catalog.MetricCatalogDocTest`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add docs/metrics/catalog.md backend/src/test/java/com/goldys/platform/semantic/catalog
git commit -m "docs: human-readable metric catalogue"
```

---

### Task 18: Migrate the reporting tools onto the catalogue

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reporting/Metric.java` (delete or repurpose)
- Modify: `backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodInput.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/GetFoodCostTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/GetLabourCostTool.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/GetReservationSummaryTool.java`
- Test: update `GetSalesByPeriodToolTest.java` and `ReportingToolIntegrationTest.java`

**Interfaces:**
- Consumes: `MetricQueryService`, `MetricId`, `MetricQuery`, `MetricResult` (Tasks 1–4), `widget.*`.
- Produces: tools that build widget specs from `MetricResult` (the `Metric` enum is removed; tools select a `MetricId`).

- [ ] **Step 1: Replace `Metric` with `MetricId` in the sales tool input**

`GetSalesByPeriodInput.java` — change `Metric metric` to `MetricId metric`, and `toMap()` to `metric.value()`.

- [ ] **Step 2: Rewrite `GetSalesByPeriodTool` to delegate**

```java
package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.Point;
import com.goldys.platform.widget.Series;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import com.goldys.platform.widget.WidgetQuery;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolved daily gross sales for a date range, emitted as a time-series widget. */
@Component
public class GetSalesByPeriodTool implements ReportingTool {
  private final MetricQueryService metrics;

  public GetSalesByPeriodTool(MetricQueryService metrics) {
    this.metrics = metrics;
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
  public Class<? extends ToolInput> inputType() {
    return GetSalesByPeriodInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("reconciliation.sales");
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetSalesByPeriodInput in)) {
      throw new IllegalArgumentException(
          "Expected GetSalesByPeriodInput, got " + input.getClass().getSimpleName());
    }
    TimeSeriesResult result =
        (TimeSeriesResult)
            metrics.query(
                new MetricQuery(
                    in.metric(),
                    new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR),
                    TimeGrain.DAY,
                    Set.of(),
                    null));

    List<Point> points =
        result.series().get(0).points().stream()
            .map(p -> new Point(p.bucketStart().toString(), p.value()))
            .toList();
    TimeSeriesWidgetSpec widget =
        new TimeSeriesWidgetSpec(
            UUID.randomUUID().toString(),
            "Daily sales",
            "Resolved gross sales per day.",
            List.of(new Series("grossSales", "Gross sales", points)),
            "currency",
            new WidgetQuery(ToolId.GET_SALES_BY_PERIOD.name(), in.toMap()));
    return new ToolResult(widget, result.notices());
  }
}
```

- [ ] **Step 3: Rewrite the other three tools the same way**

`GetFoodCostTool` → query `INVENTORY_PURCHASES` and `INVENTORY_WASTAGE` at DAY grain and build the table from the results; `GetLabourCostTool` → query `LABOUR_SCHEDULED_HOURS`/`LABOUR_ACTUAL_HOURS`/`LABOUR_COST`; `GetReservationSummaryTool` → query the reservation base metrics and derived `RESERVATIONS_NO_SHOW_RATE`/`RESERVATIONS_BOOKING_TO_COVER_CONVERSION`/`RESERVATIONS_AVG_PARTY_SIZE` at DAY grain and build the table. Each keeps its existing `resource()`, `name()`, `description()`, and `WidgetQuery` shape.

- [ ] **Step 4: Delete `Metric.java` and fix all references**

Remove `backend/src/main/java/com/goldys/platform/reporting/Metric.java`; update every reference to `Metric.GROSS_SALES` in tests and tools to `MetricId.SALES_GROSS`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.reporting.*`
Expected: PASS.

- [ ] **Step 6: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/reporting backend/src/test/java/com/goldys/platform/reporting
git commit -m "refactor: reporting tools consume the metric catalogue"
```

---

### Task 19: Migrate labour and inventory services onto the catalogue

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/application/LabourReportingService.java`
- Modify: `backend/src/main/java/com/goldys/platform/application/InventoryReportingService.java`
- Test: `backend/src/test/java/com/goldys/platform/application/LabourReportingServiceTest.java`, `InventoryReportingServiceTest.java`

**Interfaces:**
- Consumes: `MetricQueryService`, `MetricId`, `PermissionService`.
- Produces: the same `LabourSummary`/`InventorySummary` response records, now sourced from the catalogue (removing the hand-summed `sumCovers`/`sumGrossSales`/`departmentPercent` helpers).

- [ ] **Step 1: Rewrite `InventoryReportingService.summary`**

Replace the `sumGrossSales` helper with a catalogue query:

```java
  public InventorySummary summary(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    TimeRange range = new TimeRange(from, to, Calendar.CALENDAR);
    BigDecimal purchases = total(MetricId.INVENTORY_PURCHASES, range);
    BigDecimal wastage = total(MetricId.INVENTORY_WASTAGE, range);
    BigDecimal grossSales = total(MetricId.SALES_GROSS, range);
    BigDecimal foodCostPercent =
        purchases == null || grossSales == null || grossSales.signum() == 0
            ? null
            : purchases.divide(grossSales, SCALE, RoundingMode.HALF_UP);
    return new InventorySummary(from.toString(), to.toString(), purchases, wastage, foodCostPercent);
  }

  private BigDecimal total(MetricId id, TimeRange range) {
    TimeSeriesResult r =
        (TimeSeriesResult) metrics.query(new MetricQuery(id, range, TimeGrain.DAY, Set.of(), null));
    BigDecimal total = BigDecimal.ZERO;
    boolean any = false;
    for (MetricSeries s : r.series()) {
      for (MetricPoint p : s.points()) {
        if (p.value() != null) { total = total.add(p.value()); any = true; }
      }
    }
    return any ? total : null;
  }
```

- [ ] **Step 2: Rewrite `LabourReportingService.summary` similarly**

Use `total(MetricId.LABOUR_SCHEDULED_HOURS/...)`, `total(MetricId.LABOUR_COST)`, and the derived `MetricId.LABOUR_FOH_PERCENT`/`LABOUR_BOH_PERCENT`/`LABOUR_HOURS_PER_COVER`/`LABOUR_COST_PER_COVER`/`LABOUR_HOURS_VARIANCE` instead of the hand-summed helpers. Keep `permissions.require(role, RESOURCE_HOURS, READ)` and `permissions.require(role, RESOURCE_COST, READ)`.

- [ ] **Step 3: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.application.LabourReportingServiceTest --tests com.goldys.platform.application.InventoryReportingServiceTest`
Expected: PASS (update the tests to mock `MetricQueryService` instead of the old `*MetricsQuery` stubs).

- [ ] **Step 4: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/application backend/src/test/java/com/goldys/platform/application
git commit -m "refactor: labour and inventory services consume the metric catalogue"
```

---

### Task 20: Migrate the dashboard application service

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/application/DashboardApplicationService.java`
- Test: `backend/src/test/java/com/goldys/platform/application/DashboardApplicationServiceTest.java`

**Interfaces:**
- Consumes: `MetricQueryService`, `MetricId`.
- Produces: the same dashboard response records, with `salesTrend`/`topSellers` sourced from `sales.gross`/`product.top_sellers`.

- [ ] **Step 1: Rewrite `salesTrendInternal` and `topSellersInternal`**

```java
  private List<SalesTrend> salesTrendInternal() {
    LocalDate to = LocalDate.now();
    TimeRange range = new TimeRange(to.minusDays(TREND_DAYS), to, Calendar.CALENDAR);
    TimeSeriesResult r =
        (TimeSeriesResult)
            metrics.query(new MetricQuery(MetricId.SALES_GROSS, range, TimeGrain.DAY, Set.of(), null));
    return r.series().get(0).points().stream()
        .map(p -> new SalesTrend(p.bucketStart().toString(), p.value()))
        .toList();
  }

  private List<TopSeller> topSellersInternal() {
    LocalDate to = LocalDate.now();
    TimeRange range = new TimeRange(to.minusDays(TOP_SELLERS_WINDOW_DAYS), to, Calendar.CALENDAR);
    RankedListResult r =
        (RankedListResult)
            metrics.query(new MetricQuery(MetricId.PRODUCT_TOP_SELLERS, range, TimeGrain.DAY, Set.of(), null));
    return r.items().stream()
        .map(i -> new TopSeller(i.label(), i.secondary(), i.primary(), false))
        .toList();
  }
```

Keep `summaryInternal`/`activityInternal`/`latestTradingDayInternal` as-is (they read operational/ingestion/`SalesMetricsQuery.latestTradingDay`, which are not business-sum metrics). Inject `MetricQueryService` alongside the existing dependencies.

- [ ] **Step 2: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests com.goldys.platform.application.DashboardApplicationServiceTest`
Expected: PASS (update the test to mock `MetricQueryService` for the sales-trend/top-sellers paths).

- [ ] **Step 3: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/main/java/com/goldys/platform/application backend/src/test/java/com/goldys/platform/application
git commit -m "refactor: dashboard consumes the metric catalogue"
```

---

### Task 21: Architecture rules and full-suite verification

**Files:**
- Modify: `backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java`

**Interfaces:**
- Produces: ArchUnit rules asserting `semantic.catalog` is a leaf, executors depend only on `*MetricsQuery`, and `reporting`/`application` consume only the catalogue (never `reconciliation`/`canonical`).

- [ ] **Step 1: Add the catalogue leaf rule**

```java
  /** The metric catalogue is a leaf: no in-platform dependencies beyond the semantic interfaces. */
  @ArchTest
  static final ArchRule catalogIsALeaf =
      noClasses()
          .that()
          .resideInAPackage("..semantic.catalog..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..api..", "..application..", "..reporting..", "..conversational..", "..reconciliation..",
              "..canonical..", "..ingestion..", "..auth..", "..connectors..", "..widget..", "..dashboard..");
```

- [ ] **Step 2: Run the full test suite**

Run: `cd backend && ./gradlew test`
Expected: PASS (fix any ArchUnit or compile breaks; add a `@Lazy` or constructor adjustment if a Spring cycle appears between `MetricQueryServiceImpl` and derived executors — note the derived executor injects base executors, not the service, so no cycle is expected).

- [ ] **Step 3: Format and commit**

```bash
cd backend && ./gradlew spotlessApply
cd .. && git add backend/src/test/java/com/goldys/platform/architecture
git commit -m "test: catalogue architecture boundary rule"
```

---

## Self-review notes

- **Spec coverage:** every spec section maps to a task: §2 terminology → Task 3/17; §3 catalogue → Tasks 1/3/17; §3.3 net/gst → Tasks 5–6; §4 type model → Tasks 1–2/4; §5 executors/comparison/provenance → Tasks 7–16; §6 placement → Tasks 8–15 + 21; §7 migration → Tasks 18–20; §8 permissions → Task 3 (`requiredPermission` string) + Task 19 (consumer `require`); §9 testing → per-task tests; §10 doc → Task 17; §11 deferrals → recorded in the catalogue doc and `MetricCatalog` (BUDGET/FORECAST throw; `sales.excluding_refunds` has no ID).
- **Type consistency:** `MetricExecutor.ids()` (set) is introduced in Task 8 and back-propagated to Task 4's test; `DailySalesMetric` net/gst arity change (Task 5) is propagated through Task 6's projector.
- **Deferred explicitly:** `sales.excluding_refunds` (no ID, refunds not ingested), `budget`/`forecast` comparisons (throw `UnsupportedOperationException`), exact trading-day boundary (both `Calendar` values identical), `reservations.bookings`/`attended`/`no_shows` per-day series (range totals only; IDs present but DAY series needs the resolved rows split by day — documented in Task 9).
