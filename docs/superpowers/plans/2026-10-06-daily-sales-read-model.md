# Daily Sales Read Model — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Materialize the daily-sales resolved values and exceptions into disposable projection tables maintained by a projector, and migrate the dashboard, `GET /api/sales/latest`, Ask Goldy's, and the exceptions list to read them.

**Architecture:** Synchronous same-transaction projection. `CanonicalDailySalesService.record()` publishes a `DailySalesRecorded` event; a listener in `reconciliation` runs `DailySalesProjector.recompute(date)` in the same transaction. Override/rule services (already in `reconciliation`) call the projector directly; a daily-sales rule change triggers `recomputeAll()`. Reads go through `ResolvedDailySalesQuery` and `ReconciliationExceptionQuery`.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL 16 (Testcontainers), JUnit 5 + AssertJ + Mockito, Spotless (googleJavaFormat 1.28.0).

**Spec:** `docs/superpowers/specs/2026-10-06-daily-sales-read-model-design.md`

## Global Constraints

- Java 25; Spring Boot 3.5; PostgreSQL 16; schema owned by Flyway (`spring.jpa.hibernate.ddl-auto: validate` — no Hibernate auto-DDL).
- Spotless must pass (`./gradlew spotlessCheck`); googleJavaFormat 1.28.0.
- Backend tests run with `./gradlew test`; Testcontainers-backed integration tests use `@SpringBootTest` + `@Import(PostgresContainerConfiguration.class)`.
- Atomic Conventional Commits (`feat:`, `test:`, `docs:`, `fix:`); never commit directly to `main`; work on the `docs/daily-sales-read-model` branch (this plan's tasks).
- Canonical rows, overrides, and rules are append-only and authoritative; projection tables are disposable and reconstructible.
- Preserve the resolution semantics exactly: override → agreement (1-cent tolerance) → rule → unresolved (`conflict` when ≥2 sources disagree, `missing` when <2 sources).
- Reads must consume projections, never recompute at read time.
- The projector must inject **repositories**, not the services that trigger it (`ResolutionRuleService`, `DailySalesOverrideService`), to avoid a Spring bean cycle.

## Review Focus

These are the five failure modes the spec implies but no happy-path test catches. Each is pinned by a test in the owning task.

1. **A single-source date must surface as `missing` (needs a decision), never auto-resolve.** — pinned by `DailySalesResolverTest` (Task 3) and `DailySalesProjectorIntegrationTest` (Task 4).
2. **A rule save/delete must recompute every date, not just changed ones.** — pinned by `DailySalesTriggerIntegrationTest` (Task 5).
3. **`recomputeAll()` must be idempotent** (running it twice reproduces identical rows). — pinned by `DailySalesProjectorIntegrationTest` (Task 4).
4. **An override naming a source with no current canonical row must not NPE.** — pinned by `DailySalesResolverTest` (Task 3).
5. **`detected_at` must survive recomputes while an exception stays open, and reset when it clears then reopens.** — pinned by `DailySalesProjectorIntegrationTest` (Task 4).

---

### Task 1: Projection schema (Flyway migration)

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__daily_sales_read_model.sql`
- Test: `backend/src/test/java/com/goldys/platform/DatabaseMigrationTest.java` (existing, runs all migrations)

**Interfaces:**
- Produces: tables `resolved_daily_sales` and `reconciliation_exception`.

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V15__daily_sales_read_model.sql`:

```sql
-- Daily-sales read model: disposable resolved-value and exception projections.
-- Reconstructed from canonical_daily_sales + daily_sales_override + resolution_rule by
-- DailySalesProjector. Never a source of truth; safe to truncate and replay.

CREATE TABLE resolved_daily_sales (
    trading_date date PRIMARY KEY,
    total_sales numeric(14,4),
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE reconciliation_exception (
    entity_type text NOT NULL,
    entity_key text NOT NULL,
    trading_date date NOT NULL,
    field_key text NOT NULL,
    status text NOT NULL,
    detected_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (entity_type, entity_key, field_key)
);
```

- [ ] **Step 2: Run the migration test**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.DatabaseMigrationTest" --console=plain`
Expected: PASS (Flyway applies V1–V15 cleanly against the Testcontainers Postgres).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V15__daily_sales_read_model.sql
git commit -m "feat: add daily-sales read-model projection tables"
```

---

### Task 2: Projection entities and repositories

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedDailySales.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedDailySalesRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionRow.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionRowRepository.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ProjectionRepositoryIntegrationTest.java`

**Interfaces:**
- Produces: entity `ResolvedDailySales`; repository `ResolvedDailySalesRepository` (`findTopByOrderByTradingDateDesc()`, `findByTradingDateBetweenOrderByTradingDateAsc(from,to)`, `findByTradingDateIn(dates)`, `countByHasConflictTrue()`); entity `ReconciliationExceptionRow` (with nested `Id`); repository `ReconciliationExceptionRowRepository` (`findByEntityTypeOrderByTradingDateAsc(entityType)`, `findByEntityTypeAndTradingDateIn(entityType, dates)`).

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/ProjectionRepositoryIntegrationTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProjectionRepositoryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedDailySalesRepository resolved;
  @Autowired ReconciliationExceptionRowRepository exceptions;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_daily_sales");
    jdbc.update("truncate table reconciliation_exception");
  }

  @Test
  void resolvedRowsRoundTripAndConflictCountWorks() {
    resolved.save(
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 13), new BigDecimal("27650.66"), "agreed", "agreed", false,
            Instant.EPOCH));
    resolved.save(
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 14), null, "conflict", null, true, Instant.EPOCH));

    assertThat(resolved.findTopByOrderByTradingDateDesc().get().tradingDate())
        .isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(resolved.countByHasConflictTrue()).isEqualTo(1);
    assertThat(
            resolved.findByTradingDateBetweenOrderByTradingDateAsc(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .hasSize(2);
  }

  @Test
  void exceptionRowsRoundTripByIdClass() {
    exceptions.save(
        new ReconciliationExceptionRow(
            "daily_sales", "2026-09-13", "daily_sales", LocalDate.of(2026, 9, 13), "conflict",
            Instant.EPOCH));

    List<ReconciliationExceptionRow> rows =
        exceptions.findByEntityTypeOrderByTradingDateAsc("daily_sales");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).status()).isEqualTo("conflict");
    assertThat(rows.get(0).detectedAt()).isEqualTo(Instant.EPOCH);
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProjectionRepositoryIntegrationTest" --console=plain`
Expected: compilation FAIL — `ResolvedDailySales`, `ResolvedDailySalesRepository`, `ReconciliationExceptionRow`, `ReconciliationExceptionRowRepository` do not exist.

- [ ] **Step 3: Write the entities and repositories**

Create `ResolvedDailySales.java`:

```java
package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One trading date's resolved daily-sales value. Disposable projection; {@code totalSales} is
 * null while the date is unresolved. */
@Entity
@Table(name = "resolved_daily_sales")
class ResolvedDailySales {
  @Id
  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @Column(name = "total_sales", precision = 14, scale = 4)
  private BigDecimal totalSales;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source")
  private String authoritativeSource;

  @Column(name = "has_conflict", nullable = false)
  private boolean hasConflict;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedDailySales() {}

  ResolvedDailySales(
      LocalDate tradingDate,
      BigDecimal totalSales,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.tradingDate = tradingDate;
    this.totalSales = totalSales;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  LocalDate tradingDate() { return tradingDate; }
  BigDecimal totalSales() { return totalSales; }
  String resolutionType() { return resolutionType; }
  String authoritativeSource() { return authoritativeSource; }
  boolean hasConflict() { return hasConflict; }
  Instant resolvedAt() { return resolvedAt; }
}
```

Create `ResolvedDailySalesRepository.java`:

```java
package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ResolvedDailySalesRepository extends JpaRepository<ResolvedDailySales, LocalDate> {
  Optional<ResolvedDailySales> findTopByOrderByTradingDateDesc();

  List<ResolvedDailySales> findByTradingDateBetweenOrderByTradingDateAsc(LocalDate from, LocalDate to);

  List<ResolvedDailySales> findByTradingDateIn(Collection<LocalDate> dates);

  long countByHasConflictTrue();
}
```

Create `ReconciliationExceptionRow.java`:

```java
package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** One open reconciliation exception. Disposable projection; {@code detectedAt} is the time the
 * exception first opened and is preserved while it stays open. */
@Entity
@Table(name = "reconciliation_exception")
@IdClass(ReconciliationExceptionRow.Id.class)
class ReconciliationExceptionRow {
  @Id
  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Id
  @Column(name = "entity_key", nullable = false)
  private String entityKey;

  @Id
  @Column(name = "field_key", nullable = false)
  private String fieldKey;

  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "detected_at", nullable = false)
  private Instant detectedAt;

  protected ReconciliationExceptionRow() {}

  ReconciliationExceptionRow(
      String entityType,
      String entityKey,
      String fieldKey,
      LocalDate tradingDate,
      String status,
      Instant detectedAt) {
    this.entityType = entityType;
    this.entityKey = entityKey;
    this.fieldKey = fieldKey;
    this.tradingDate = tradingDate;
    this.status = status;
    this.detectedAt = detectedAt;
  }

  void changeStatus(String newStatus) {
    this.status = newStatus;
  }

  String entityType() { return entityType; }
  String entityKey() { return entityKey; }
  String fieldKey() { return fieldKey; }
  LocalDate tradingDate() { return tradingDate; }
  String status() { return status; }
  Instant detectedAt() { return detectedAt; }

  static class Id implements Serializable {
    private String entityType;
    private String entityKey;
    private String fieldKey;

    public Id() {}

    Id(String entityType, String entityKey, String fieldKey) {
      this.entityType = entityType;
      this.entityKey = entityKey;
      this.fieldKey = fieldKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Id other)) return false;
      return Objects.equals(entityType, other.entityType)
          && Objects.equals(entityKey, other.entityKey)
          && Objects.equals(fieldKey, other.fieldKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(entityType, entityKey, fieldKey);
    }
  }
}
```

Create `ReconciliationExceptionRowRepository.java`:

```java
package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReconciliationExceptionRowRepository
    extends JpaRepository<ReconciliationExceptionRow, ReconciliationExceptionRow.Id> {
  List<ReconciliationExceptionRow> findByEntityTypeOrderByTradingDateAsc(String entityType);

  List<ReconciliationExceptionRow> findByEntityTypeAndTradingDateIn(
      String entityType, Collection<LocalDate> dates);
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProjectionRepositoryIntegrationTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/ResolvedDailySales.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ResolvedDailySalesRepository.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionRow.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionRowRepository.java \
        backend/src/test/java/com/goldys/platform/reconciliation/ProjectionRepositoryIntegrationTest.java
git commit -m "feat: add daily-sales projection entities and repositories"
```

---

### Task 3: Pure resolver + canonical event

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesResolver.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/DailySalesRecorded.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesResolverTest.java`

**Interfaces:**
- Produces: `DailySalesResolver.resolve(List<SourceTotal>, Optional<String> overrideSource, Optional<ResolutionRule> rule)` → `Optional<DailySalesResolver.Result>`; `DailySalesResolver.Result(String resolutionType, String authoritativeSource, BigDecimal totalSales)` with `boolean hasConflict()`; `DailySalesResolver.classify(List<SourceTotal>)`; `DailySalesResolver.withinCent(BigDecimal, BigDecimal)`; record `canonical.DailySalesRecorded(LocalDate tradingDate)`.

- [ ] **Step 1: Write the failing unit test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesResolverTest.java`:

```java
package com.goldys.platform.reconciliation;

import static com.goldys.platform.reconciliation.DailySalesResolver.classify;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailySalesResolverTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  @Test
  void classifiesAgreedConflictAndMissing() {
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00"), st("CTB", "10.00")))).isEqualTo("agreed");
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00"), st("CTB", "10.005")))).isEqualTo("agreed");
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00"), st("CTB", "12.00")))).isEqualTo("conflict");
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00")))).isEqualTo("missing");
  }

  @Test
  void overrideResolvesToThatSource() {
    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("LIGHTSPEED", "27650.66"), st("CTB", "20990.83")),
            Optional.of("CTB"),
            Optional.empty());

    assertThat(r).isPresent();
    assertThat(r.get().resolutionType()).isEqualTo("override");
    assertThat(r.get().authoritativeSource()).isEqualTo("CTB");
    assertThat(r.get().totalSales()).isEqualByComparingTo("20990.83");
    assertThat(r.get().hasConflict()).isFalse();
  }

  @Test
  void agreementResolvesToFirstSourceValue() {
    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("LIGHTSPEED", "9694.80"), st("CTB", "9694.80")),
            Optional.empty(),
            Optional.empty());

    assertThat(r).isPresent();
    assertThat(r.get().resolutionType()).isEqualTo("agreed");
    assertThat(r.get().authoritativeSource()).isEqualTo("agreed");
    assertThat(r.get().totalSales()).isEqualByComparingTo("9694.80");
  }

  @Test
  void conflictWithNoRuleIsUnresolved() {
    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("LIGHTSPEED", "27650.66"), st("CTB", "20990.83")),
            Optional.empty(),
            Optional.empty());

    assertThat(r).isPresent();
    assertThat(r.get().resolutionType()).isEqualTo("conflict");
    assertThat(r.get().totalSales()).isNull();
    assertThat(r.get().hasConflict()).isTrue();
  }

  @Test
  void singleSourceIsMissingNotResolved() {
    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("LIGHTSPEED", "27650.66")), Optional.empty(), Optional.empty());

    assertThat(r).isPresent();
    assertThat(r.get().resolutionType()).isEqualTo("missing");
    assertThat(r.get().totalSales()).isNull();
    assertThat(r.get().hasConflict()).isTrue();
  }

  @Test
  void priorityRuleResolvesAConflict() {
    ResolutionRule rule =
        ResolutionRule.create(
            "daily_sales", "daily_sales", "priority", null, List.of("CTB"), "a@b.com",
            Instant.EPOCH);

    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("LIGHTSPEED", "27650.66"), st("CTB", "20990.83")),
            Optional.empty(),
            Optional.of(rule));

    assertThat(r).isPresent();
    assertThat(r.get().resolutionType()).isEqualTo("rule");
    assertThat(r.get().authoritativeSource()).isEqualTo("CTB");
    assertThat(r.get().totalSales()).isEqualByComparingTo("20990.83");
  }

  @Test
  void emptySourcesResolveToNothing() {
    assertThat(DailySalesResolver.resolve(List.of(), Optional.empty(), Optional.empty())).isEmpty();
  }

  @Test
  void overrideForUnknownSourceDoesNotNpe() {
    Optional<DailySalesResolver.Result> r =
        DailySalesResolver.resolve(
            List.of(st("CTB", "20990.83")), Optional.of("LIGHTSPEED"), Optional.empty());
    // The override names a source with no row; the result is empty rather than an exception.
    assertThat(r).isEmpty();
  }

  private static SourceTotal st(String source, String total) {
    return new SourceTotal(source, new BigDecimal(total), null);
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.DailySalesResolverTest" --console=plain`
Expected: compilation FAIL — `DailySalesResolver` does not exist.

- [ ] **Step 3: Write the resolver and the event**

Create `DailySalesResolver.java`:

```java
package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Pure daily-sales resolution: override → agreement (one-cent tolerance) → rule → unresolved.
 * No I/O and no permissions; the projector and any future consumer share this one implementation. */
final class DailySalesResolver {
  private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

  private DailySalesResolver() {}

  record Result(String resolutionType, String authoritativeSource, BigDecimal totalSales) {
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
          .map(s -> new Result("override", s.sourceSystem(), s.totalSales()));
    }
    if (sources.isEmpty()) {
      return Optional.empty();
    }
    String status = classify(sources);
    if ("agreed".equals(status)) {
      return Optional.of(new Result("agreed", "agreed", sources.get(0).totalSales()));
    }
    if (rule.isPresent()) {
      Optional<String> chosen = RuleEvaluator.resolve(rule.get(), toMetrics(sources));
      if (chosen.isPresent()) {
        return sources.stream()
            .filter(s -> s.sourceSystem().equals(chosen.get()))
            .findFirst()
            .map(s -> new Result("rule", s.sourceSystem(), s.totalSales()));
      }
    }
    return Optional.of(new Result(status, null, null));
  }

  static String classify(List<SourceTotal> sources) {
    if (sources.size() < 2) {
      return "missing";
    }
    BigDecimal first = sources.get(0).totalSales();
    boolean allAgree = sources.stream().allMatch(s -> withinCent(first, s.totalSales()));
    return allAgree ? "agreed" : "conflict";
  }

  static boolean withinCent(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.subtract(b).abs().compareTo(ONE_CENT) <= 0;
  }

  private static List<SourceMetric> toMetrics(List<SourceTotal> sources) {
    return sources.stream()
        .map(s -> new SourceMetric(s.sourceSystem(), s.totalSales(), s.recordedAt()))
        .toList();
  }
}
```

Create `DailySalesRecorded.java`:

```java
package com.goldys.platform.canonical;

import java.time.LocalDate;

/** Published after a canonical daily-sales fact is recorded, so the reconciliation projector can
 * update the affected date's resolved projection in the same transaction. */
public record DailySalesRecorded(LocalDate tradingDate) {}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.DailySalesResolverTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/DailySalesResolver.java \
        backend/src/main/java/com/goldys/platform/canonical/DailySalesRecorded.java \
        backend/src/test/java/com/goldys/platform/reconciliation/DailySalesResolverTest.java
git commit -m "feat: extract pure daily-sales resolver and canonical change event"
```

---

### Task 4: Projector + bulk repository methods

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalDailySalesRepository.java` (add `findCurrentByDates`)
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalDailySalesQuery.java` (add `currentDailySalesForDates`)
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesOverrideRepository.java` (add `findCurrentByDates`)
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesProjector.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesProjectorIntegrationTest.java`

**Interfaces:**
- Consumes: `ResolvedDailySales`, `ReconciliationExceptionRow`, repositories (Task 2); `DailySalesResolver` (Task 3); `CanonicalDailySalesQuery.currentDailySalesForDates(Collection<LocalDate>)` and `DailySalesOverrideRepository.findCurrentByDates(Collection<LocalDate>)` (this task).
- Produces: `DailySalesProjector.recompute(LocalDate...)` and `DailySalesProjector.recomputeAll()`.

- [ ] **Step 1: Add the bulk repository methods**

In `CanonicalDailySalesRepository.java`, add after `findCurrentByDate`:

```java
  @Query(
      "select s from CanonicalDailySales s where s.tradingDate in :dates "
          + "and s.supersededAt is null")
  List<CanonicalDailySales> findCurrentByDates(Collection<LocalDate> dates);
```

Add the import `java.util.Collection;` to that file.

In `CanonicalDailySalesQuery.java`, add after `currentDailySalesForDate`:

```java
  public List<DailySalesView> currentDailySalesForDates(Collection<LocalDate> dates) {
    return repository.findCurrentByDates(dates).stream().map(this::toView).toList();
  }
```

Add the imports `java.util.Collection;`.

In `DailySalesOverrideRepository.java`, add after `findCurrent`:

```java
  @Query(
      "select o from DailySalesOverride o where o.tradingDate in :dates "
          + "and o.supersededAt is null")
  List<DailySalesOverride> findCurrentByDates(Collection<LocalDate> dates);
```

Add the imports `java.time.LocalDate;` is already present; add `java.util.Collection;` and `java.util.List;` (verify — the file already imports `java.util.List`).

- [ ] **Step 2: Write the failing integration test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesProjectorIntegrationTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
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
class DailySalesProjectorIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesIngest ingest;
  @Autowired DailySalesProjector projector;
  @Autowired ResolvedDailySalesRepository resolved;
  @Autowired ReconciliationExceptionRowRepository exceptions;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_daily_sales");
    jdbc.update("truncate table reconciliation_exception");
    jdbc.update("truncate table canonical_daily_sales");
  }

  @Test
  void agreeingSourcesResolveWithNoException() {
    ingest.record(input("LIGHTSPEED", SEP_13, "9694.80"));
    ingest.record(input("CTB", SEP_13, "9694.80"));
    projector.recompute(SEP_13);

    ResolvedDailySales row = resolved.findTopByOrderByTradingDateDesc().get();
    assertThat(row.resolutionType()).isEqualTo("agreed");
    assertThat(row.totalSales()).isEqualByComparingTo("9694.80");
    assertThat(row.hasConflict()).isFalse();
    assertThat(exceptions.findByEntityTypeOrderByTradingDateAsc("daily_sales")).isEmpty();
  }

  @Test
  void conflictingSourcesProduceAnException() {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));
    projector.recompute(SEP_13);

    ResolvedDailySales row = resolved.findTopByOrderByTradingDateDesc().get();
    assertThat(row.hasConflict()).isTrue();
    assertThat(row.resolutionType()).isEqualTo("conflict");
    assertThat(row.totalSales()).isNull();

    var rows = exceptions.findByEntityTypeOrderByTradingDateAsc("daily_sales");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).status()).isEqualTo("conflict");
    assertThat(rows.get(0).tradingDate()).isEqualTo(SEP_13);
  }

  @Test
  void recomputeAllIsIdempotent() {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));
    projector.recomputeAll();
    var first = resolved.findAll();

    projector.recomputeAll();
    var second = resolved.findAll();

    assertThat(second).hasSize(first.size());
    assertThat(second).containsExactlyInAnyOrderElementsOf(first);
  }

  @Test
  void detectedAtSurvivesRecomputeWhileOpen() throws Exception {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));
    projector.recompute(SEP_13);
    var first = exceptions.findByEntityTypeOrderByTradingDateAsc("daily_sales").get(0).detectedAt();

    Thread.sleep(5);
    projector.recompute(SEP_13);
    var second = exceptions.findByEntityTypeOrderByTradingDateAsc("daily_sales").get(0).detectedAt();

    assertThat(second).isEqualTo(first);
  }

  private DailySalesInput input(String source, LocalDate date, String total) {
    return new DailySalesInput(
        source, date, new BigDecimal(total), new BigDecimal("0"), new BigDecimal("0"),
        rawRecord(source));
  }

  // canonical_daily_sales.raw_record_id is a NOT NULL FK to raw_record(id), so each canonical
  // row needs a real ingestion_run + raw_record row (mirrors CanonicalDailySalesIntegrationTest).
  private UUID rawRecord(String source) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, ?, 'test', 'SUCCESS', now(), 1, 1)",
        runId,
        source);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, ?, 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        source,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }
}
```

- [ ] **Step 3: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.DailySalesProjectorIntegrationTest" --console=plain`
Expected: compilation FAIL — `DailySalesProjector` does not exist.

- [ ] **Step 4: Write the projector**

Create `DailySalesProjector.java`:

```java
package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the daily-sales read model ({@code resolved_daily_sales} and
 * {@code reconciliation_exception}) from canonical rows, overrides, and rules.
 *
 * <p>Injects repositories rather than the services that trigger it
 * ({@link DailySalesOverrideService}, {@link ResolutionRuleService}) to avoid a Spring bean cycle.
 */
@Service
public class DailySalesProjector {
  private static final String ENTITY_TYPE = "daily_sales";
  private static final String FIELD_KEY = "daily_sales";
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalDailySalesQuery dailySales;
  private final DailySalesOverrideRepository overrides;
  private final ResolutionRuleRepository rules;
  private final ResolvedDailySalesRepository resolved;
  private final ReconciliationExceptionRowRepository exceptions;

  public DailySalesProjector(
      CanonicalDailySalesQuery dailySales,
      DailySalesOverrideRepository overrides,
      ResolutionRuleRepository rules,
      ResolvedDailySalesRepository resolved,
      ReconciliationExceptionRowRepository exceptions) {
    this.dailySales = dailySales;
    this.overrides = overrides;
    this.rules = rules;
    this.resolved = resolved;
    this.exceptions = exceptions;
  }

  /** Rebuild the whole read model from canonical + overrides + rules. Used on rule changes and
   * deploy backfill. */
  @Transactional
  public void recomputeAll() {
    resolved.deleteAllInBatch();
    exceptions.deleteAllInBatch();
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (DailySalesView v : dailySales.currentDailySales()) {
      dates.add(v.tradingDate());
    }
    recompute(dates);
  }

  /** Recompute just the given dates (bulk). */
  @Transactional
  public void recompute(LocalDate... dates) {
    recompute(Set.of(dates));
  }

  private void recompute(Collection<LocalDate> dates) {
    if (dates.isEmpty()) {
      return;
    }
    Map<LocalDate, List<SourceTotal>> byDate = new HashMap<>();
    for (DailySalesView v : dailySales.currentDailySalesForDates(dates)) {
      byDate
          .computeIfAbsent(v.tradingDate(), k -> new ArrayList<>())
          .add(new SourceTotal(v.sourceSystem(), v.totalSales(), v.recordedAt()));
    }
    Map<LocalDate, String> overrideByDate = new HashMap<>();
    for (DailySalesOverride o : overrides.findCurrentByDates(dates)) {
      overrideByDate.put(o.tradingDate(), o.authoritativeSource());
    }
    Optional<ResolutionRule> rule = rules.findCurrent(ENTITY_TYPE, FIELD_KEY);

    Instant now = CLOCK.instant();
    Map<LocalDate, DailySalesResolver.Result> results = new HashMap<>();
    for (LocalDate date : dates) {
      DailySalesResolver.resolve(
              byDate.getOrDefault(date, List.of()),
              Optional.ofNullable(overrideByDate.get(date)),
              rule)
          .ifPresent(r -> results.put(date, r));
    }

    List<ResolvedDailySales> rows = new ArrayList<>();
    for (Map.Entry<LocalDate, DailySalesResolver.Result> e : results.entrySet()) {
      DailySalesResolver.Result r = e.getValue();
      rows.add(
          new ResolvedDailySales(
              e.getKey(), r.totalSales(), r.resolutionType(), r.authoritativeSource(),
              r.hasConflict(), now));
    }
    resolved.saveAll(rows);

    reconcileExceptions(results, now);
  }

  private void reconcileExceptions(Map<LocalDate, DailySalesResolver.Result> results, Instant now) {
    List<ReconciliationExceptionRow> existing =
        exceptions.findByEntityTypeAndTradingDateIn(ENTITY_TYPE, results.keySet());
    Map<String, ReconciliationExceptionRow> byKey = new HashMap<>();
    for (ReconciliationExceptionRow e : existing) {
      byKey.put(e.entityKey(), e);
    }

    List<ReconciliationExceptionRow> toInsert = new ArrayList<>();
    List<ReconciliationExceptionRow> toDelete = new ArrayList<>();
    for (Map.Entry<LocalDate, DailySalesResolver.Result> e : results.entrySet()) {
      LocalDate date = e.getKey();
      DailySalesResolver.Result r = e.getValue();
      String key = date.toString();
      ReconciliationExceptionRow existingRow = byKey.remove(key);
      if (r.hasConflict()) {
        if (existingRow == null) {
          toInsert.add(
              new ReconciliationExceptionRow(
                  ENTITY_TYPE, key, FIELD_KEY, date, r.resolutionType(), now));
        } else if (!r.resolutionType().equals(existingRow.status())) {
          existingRow.changeStatus(r.resolutionType());
        }
        // else: unchanged — keep the original detectedAt
      } else if (existingRow != null) {
        toDelete.add(existingRow);
      }
    }
    toDelete.addAll(byKey.values());
    exceptions.deleteAll(toDelete);
    exceptions.saveAll(toInsert);
  }
}
```

- [ ] **Step 5: Run the test to confirm it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.DailySalesProjectorIntegrationTest" --console=plain`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalDailySalesRepository.java \
        backend/src/main/java/com/goldys/platform/canonical/CanonicalDailySalesQuery.java \
        backend/src/main/java/com/goldys/platform/reconciliation/DailySalesOverrideRepository.java \
        backend/src/main/java/com/goldys/platform/reconciliation/DailySalesProjector.java \
        backend/src/test/java/com/goldys/platform/reconciliation/DailySalesProjectorIntegrationTest.java
git commit -m "feat: add daily-sales reconciliation projector"
```

---

### Task 5: Trigger wiring

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalDailySalesService.java` (publish event)
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesProjectionListener.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesOverrideService.java` (call projector)
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleService.java` (call projector on daily-sales changes)
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesTriggerIntegrationTest.java`

**Interfaces:**
- Consumes: `DailySalesProjector` (Task 4), `canonical.DailySalesRecorded` (Task 3).
- Produces: `DailySalesProjectionListener`; modified `CanonicalDailySalesService`, `DailySalesOverrideService`, `ResolutionRuleService`.

- [ ] **Step 1: Publish the event from canonical recording**

In `CanonicalDailySalesService.java`:
- Add the field and constructor parameter `org.springframework.context.ApplicationEventPublisher publisher;`
- Change `record` to publish after recording:

```java
  @Transactional
  CanonicalDailySales record(DailySalesInput input) {
    CanonicalDailySales saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new DailySalesRecorded(input.tradingDate()));
    return saved;
  }
```

- Add import `org.springframework.context.ApplicationEventPublisher;`.

- [ ] **Step 2: Write the listener**

Create `DailySalesProjectionListener.java`:

```java
package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.DailySalesRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Projects a date's resolved value whenever a canonical daily-sales fact is recorded. Runs
 * synchronously in the publisher's transaction, so canonical and projection stay atomic. */
@Component
public class DailySalesProjectionListener {
  private final DailySalesProjector projector;

  public DailySalesProjectionListener(DailySalesProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(DailySalesRecorded event) {
    projector.recompute(event.tradingDate());
  }
}
```

- [ ] **Step 3: Wire the override and rule services**

In `DailySalesOverrideService.java`:
- Add a `DailySalesProjector projector` field and constructor parameter.
- At the end of `save(...)`, before `return`, call `projector.recompute(date);` and return the saved row:

```java
    DailySalesOverride saved =
        repository.save(DailySalesOverride.create(date, source, reason, actorEmail, now));
    projector.recompute(date);
    return saved;
```

In `ResolutionRuleService.java`:
- Add a `DailySalesProjector projector` field and constructor parameter.
- At the end of `save(...)`, before `return toView(saved);`, add:

```java
    if ("daily_sales".equals(input.entityType())) {
      projector.recomputeAll();
    }
```

- At the end of `delete(...)`, after `current.supersede(...)`, add:

```java
    if ("daily_sales".equals(current.entityType())) {
      projector.recomputeAll();
    }
```

- [ ] **Step 4: Write the failing trigger integration test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesTriggerIntegrationTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class DailySalesTriggerIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesIngest ingest;
  @Autowired DailySalesOverrideService overrideService;
  @Autowired ResolutionRuleService ruleService;
  @Autowired ResolvedDailySalesRepository resolved;
  // The real PermissionService would consult the seeded permission table; replace it so the
  // override/rule saves succeed without a permission fixture. A Mockito mock's void require(...)
  // is a no-op by default.
  @MockitoBean PermissionService permissions;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_daily_sales");
    jdbc.update("truncate table reconciliation_exception");
    jdbc.update("truncate table canonical_daily_sales");
    jdbc.update("truncate table daily_sales_override");
    jdbc.update("truncate table resolution_rule");
  }

  @Test
  void recordingCanonicalProjectsTheDateAutomatically() {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));

    // No explicit projector call — the event listener must have projected the conflict.
    ResolvedDailySales row = resolved.findTopByOrderByTradingDateDesc().get();
    assertThat(row.hasConflict()).isTrue();
    assertThat(row.resolutionType()).isEqualTo("conflict");
  }

  @Test
  void overrideSaveProjectsTheDate() {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));

    overrideService.save(OWNER, "owner@example.com", SEP_13, "LIGHTSPEED", "trust lightspeed");

    ResolvedDailySales row = resolved.findTopByOrderByTradingDateDesc().get();
    assertThat(row.hasConflict()).isFalse();
    assertThat(row.resolutionType()).isEqualTo("override");
    assertThat(row.authoritativeSource()).isEqualTo("LIGHTSPEED");
  }

  @Test
  void dailySalesRuleChangeRecomputesAllDates() {
    LocalDate sep14 = LocalDate.of(2026, 9, 14);
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));
    ingest.record(input("LIGHTSPEED", sep14, "1000.00"));
    ingest.record(input("CTB", sep14, "1000.00"));

    ruleService.save(
        OWNER,
        "owner@example.com",
        new ResolutionRuleService.RuleInput(
            "daily_sales", "daily_sales", "priority", null, List.of("CTB")));

    assertThat(resolved.findTopByOrderByTradingDateDesc().get().hasConflict()).isFalse();
    for (ResolvedDailySales row : resolved.findAll()) {
      assertThat(row.hasConflict()).isFalse();
      assertThat(row.authoritativeSource()).isEqualTo("CTB");
    }
  }

  private DailySalesInput input(String source, LocalDate date, String total) {
    return new DailySalesInput(
        source, date, new BigDecimal(total), new BigDecimal("0"), new BigDecimal("0"),
        rawRecord(source));
  }

  // canonical_daily_sales.raw_record_id is a NOT NULL FK to raw_record(id) — insert real rows.
  private UUID rawRecord(String source) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, ?, 'test', 'SUCCESS', now(), 1, 1)",
        runId,
        source);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, ?, 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        source,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }
}
```

- [ ] **Step 5: Run it to confirm it fails then passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.DailySalesTriggerIntegrationTest" --console=plain`
Expected: FAIL before wiring (the listener/triggers don't exist yet — canonical recording does not project); PASS after Steps 1–3.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalDailySalesService.java \
        backend/src/main/java/com/goldys/platform/reconciliation/DailySalesProjectionListener.java \
        backend/src/main/java/com/goldys/platform/reconciliation/DailySalesOverrideService.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleService.java \
        backend/src/test/java/com/goldys/platform/reconciliation/DailySalesTriggerIntegrationTest.java
git commit -m "feat: trigger daily-sales projection from canonical, override, and rule writes"
```

---

### Task 6: Read queries

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolvedDailySalesQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ResolvedDailySalesQueryIntegrationTest.java`

**Interfaces:**
- Consumes: repositories (Task 2), `CanonicalDailySalesQuery.currentDailySalesForDates` (Task 4), `SourceTotal`.
- Produces: `ResolvedDailySalesView(LocalDate tradingDate, BigDecimal totalSales, String resolutionType, String authoritativeSource, boolean hasConflict)`; `ResolvedDailySalesQuery.latest()`, `.between(LocalDate, LocalDate)`, `.countOpenConflicts()`; `DailyException(LocalDate tradingDate, String status, List<SourceTotal> sources)`; `ReconciliationExceptionQuery.listDaily()`.

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/ResolvedDailySalesQueryIntegrationTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ResolvedDailySalesQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedDailySalesRepository repository;
  @Autowired ResolvedDailySalesQuery query;

  @BeforeEach
  void seed() {
    jdbc.update("truncate table resolved_daily_sales");
    repository.save(
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 13), new BigDecimal("9694.80"), "agreed", "agreed", false,
            Instant.EPOCH));
    repository.save(
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 14), null, "conflict", null, true, Instant.EPOCH));
  }

  @Test
  void latestReturnsTheMostRecentRow() {
    var latest = query.latest();
    assertThat(latest).isPresent();
    assertThat(latest.get().tradingDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(latest.get().totalSales()).isNull();
  }

  @Test
  void betweenReturnsRowsInRange() {
    var rows =
        query.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).tradingDate()).isEqualTo(LocalDate.of(2026, 9, 13));
  }

  @Test
  void countOpenConflictsCountsOnlyConflicts() {
    assertThat(query.countOpenConflicts()).isEqualTo(1);
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ResolvedDailySalesQueryIntegrationTest" --console=plain`
Expected: compilation FAIL — `ResolvedDailySalesQuery` does not exist.

- [ ] **Step 3: Write the query facades**

Create `ResolvedDailySalesQuery.java`:

```java
package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Read-only facade over the resolved daily-sales projection. */
@Service
public class ResolvedDailySalesQuery {
  private final ResolvedDailySalesRepository repository;

  public ResolvedDailySalesQuery(ResolvedDailySalesRepository repository) {
    this.repository = repository;
  }

  public Optional<ResolvedDailySalesView> latest() {
    return repository.findTopByOrderByTradingDateDesc().map(this::toView);
  }

  public List<ResolvedDailySalesView> between(LocalDate from, LocalDate to) {
    return repository.findByTradingDateBetweenOrderByTradingDateAsc(from, to).stream()
        .map(this::toView)
        .toList();
  }

  public long countOpenConflicts() {
    return repository.countByHasConflictTrue();
  }

  private ResolvedDailySalesView toView(ResolvedDailySales r) {
    return new ResolvedDailySalesView(
        r.tradingDate(),
        r.totalSales(),
        r.resolutionType(),
        r.authoritativeSource(),
        r.hasConflict());
  }

  public record ResolvedDailySalesView(
      LocalDate tradingDate,
      BigDecimal totalSales,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict) {}
}
```

Create `ReconciliationExceptionQuery.java`:

```java
package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Read-only facade over the daily-sales exception projection, joined back to canonical for the
 * per-source values shown in the exceptions UI. */
@Service
public class ReconciliationExceptionQuery {
  private static final String ENTITY_TYPE = "daily_sales";

  private final ReconciliationExceptionRowRepository exceptions;
  private final CanonicalDailySalesQuery dailySales;

  public ReconciliationExceptionQuery(
      ReconciliationExceptionRowRepository exceptions, CanonicalDailySalesQuery dailySales) {
    this.exceptions = exceptions;
    this.dailySales = dailySales;
  }

  public List<DailyException> listDaily() {
    List<ReconciliationExceptionRow> rows =
        exceptions.findByEntityTypeOrderByTradingDateAsc(ENTITY_TYPE);
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (ReconciliationExceptionRow row : rows) {
      dates.add(row.tradingDate());
    }
    Map<LocalDate, List<SourceTotal>> byDate = new HashMap<>();
    for (DailySalesView v : dailySales.currentDailySalesForDates(dates)) {
      byDate
          .computeIfAbsent(v.tradingDate(), k -> new ArrayList<>())
          .add(new SourceTotal(v.sourceSystem(), v.totalSales(), v.recordedAt()));
    }
    List<DailyException> out = new ArrayList<>();
    for (ReconciliationExceptionRow row : rows) {
      out.add(
          new DailyException(
              row.tradingDate(), row.status(), byDate.getOrDefault(row.tradingDate(), List.of())));
    }
    return out;
  }

  public record DailyException(LocalDate tradingDate, String status, List<SourceTotal> sources) {}
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ResolvedDailySalesQueryIntegrationTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/ResolvedDailySalesQuery.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionQuery.java \
        backend/src/test/java/com/goldys/platform/reconciliation/ResolvedDailySalesQueryIntegrationTest.java
git commit -m "feat: add resolved daily-sales and exception read queries"
```

---

### Task 7: Consumer migration + delete the on-demand service

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/api/SalesController.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/DashboardController.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/ReconciliationController.java`
- Modify: `backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodTool.java`
- Delete: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesReconciliationService.java`
- Delete: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesResolved.java`
- Delete: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesConflict.java`
- Delete: `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesReconciliationTest.java`
- Modify: `backend/src/test/java/com/goldys/platform/api/SalesControllerTest.java`
- Modify: `backend/src/test/java/com/goldys/platform/api/DashboardControllerTest.java`
- Modify: `backend/src/test/java/com/goldys/platform/api/ReconciliationControllerTest.java`
- Modify: `backend/src/test/java/com/goldys/platform/reporting/GetSalesByPeriodToolTest.java`

**Interfaces:**
- Consumes: `ResolvedDailySalesQuery`, `ReconciliationExceptionQuery` (Task 6), `ProductSalesReconciliationService` (unchanged).

- [ ] **Step 1: Migrate `SalesController.latest()`**

In `SalesController.java`, replace the `DailySalesReconciliationService` + `DailySalesResolved` dependency with `ResolvedDailySalesQuery`, and rewrite `latest()`:

```java
  @GetMapping("/latest")
  LatestSalesDto latest(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return resolvedDailySales
        .latest()
        .map(
            v ->
                new LatestSalesDto(
                    v.tradingDate().toString(), v.totalSales(), v.authoritativeSource()))
        .orElseGet(() -> new LatestSalesDto(null, null, null));
  }
```

Update imports: remove `DailySalesReconciliationService`, `DailySalesResolved`, `LocalDate`, `Optional` (if now unused); add `ResolvedDailySalesQuery`. Update the constructor to take `ResolvedDailySalesQuery resolvedDailySales` instead of `DailySalesReconciliationService reconciliation`.

- [ ] **Step 2: Migrate `DashboardController.summary()`**

In `DashboardController.java`, replace `DailySalesReconciliationService reconciliation` with `ResolvedDailySalesQuery resolvedDailySales`, and change the `openConflicts` computation:

```java
    int openConflicts = (int) (resolvedDailySales.countOpenConflicts() + productSales.conflicts().size());
```

Keep `SummaryDto.openConflicts` as `Integer` (the frontend `DashboardSummary.openConflicts` is a `number`; `countOpenConflicts()` returns `long` and `conflicts().size()` returns `int`, so the cast to `int` keeps the record field unchanged).

- [ ] **Step 3: Migrate `ReconciliationController.exceptions()`**

In `ReconciliationController.java`, replace the `DailySalesReconciliationService reconciliation` dependency with `ReconciliationExceptionQuery exceptionsQuery`, and rewrite `exceptions()`:

```java
  @GetMapping("/exceptions")
  List<ExceptionDto> exceptions(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return exceptionsQuery.listDaily().stream().map(this::toException).toList();
  }

  private ExceptionDto toException(ReconciliationExceptionQuery.DailyException e) {
    List<SourceValueDto> sources =
        e.sources().stream()
            .map(s -> new SourceValueDto(s.sourceSystem(), plain(s.totalSales())))
            .toList();
    return new ExceptionDto(
        e.tradingDate() + ":daily_sales",
        e.tradingDate().toString(),
        e.tradingDate().toString(),
        "daily_sales",
        sources,
        e.status());
  }
```

Remove the now-unused `DailySalesConflict`, `SourceTotal`, `DailySalesReconciliationService` imports and the old `toException(DailySalesConflict)` method.

- [ ] **Step 4: Migrate `GetSalesByPeriodTool`**

In `GetSalesByPeriodTool.java`, replace `DailySalesReconciliationService reconciliation` with `ResolvedDailySalesQuery resolved`, and rewrite `execute()` to use `between`:

```java
  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetSalesByPeriodInput in)) {
      throw new IllegalArgumentException(
          "Expected GetSalesByPeriodInput, got " + input.getClass().getSimpleName());
    }
    List<Map<String, Object>> points = new ArrayList<>();
    List<LocalDate> unresolved = new ArrayList<>();
    for (ResolvedDailySalesQuery.ResolvedDailySalesView v : resolved.between(in.startDate(), in.endDate())) {
      if (v.totalSales() == null) {
        unresolved.add(v.tradingDate());
      } else {
        points.add(point(v));
      }
    }
    List<String> notices =
        unresolved.isEmpty()
            ? List.of()
            : List.of(unresolved.size() + " date(s) have no resolved total (unresolved conflict).");
    WidgetSpec widget =
        new WidgetSpec(1, "line-chart", "Daily sales", "Resolved gross sales per day.", points);
    return new ToolResult(widget, notices);
  }

  private static Map<String, Object> point(ResolvedDailySalesQuery.ResolvedDailySalesView v) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("date", v.tradingDate().toString());
    m.put("grossSales", v.totalSales());
    m.put("source", v.authoritativeSource());
    return m;
  }
```

Update imports: remove `DailySalesReconciliationService`, `DailySalesResolved`; add `ResolvedDailySalesQuery`.

- [ ] **Step 5: Delete the on-demand service and its dead types**

Delete `DailySalesReconciliationService.java`, `DailySalesResolved.java`, `DailySalesConflict.java`, and `DailySalesReconciliationTest.java`.

Verify no remaining references:
Run: `cd backend && grep -rn "DailySalesReconciliationService\|DailySalesResolved\|DailySalesConflict" src/`
Expected: no matches (aside from nothing — all consumers migrated).

- [ ] **Step 6: Update the controller and tool unit tests**

`SalesControllerTest.java`:
- Replace `@MockitoBean DailySalesReconciliationService reconciliation;` with `@MockitoBean ResolvedDailySalesQuery resolvedDailySales;`.
- In the `latest*` tests, replace `dailySales.latestTradingDate()` + `reconciliation.resolved(...)` stubbing with `resolvedDailySales.latest()` returning `Optional.of(new ResolvedDailySalesQuery.ResolvedDailySalesView(date, total, "agreed", "agreed", false))` (and `Optional.empty()` / a conflict view for the null cases).
- The `latestReturnsNullsWhenThereIsNoData` test now stubs `when(resolvedDailySales.latest()).thenReturn(Optional.empty());`.
- The `latestReturnsTheResolvedTotal` test stubs `latest()` → a view with `totalSales = 10865.72`, `authoritativeSource = "agreed"`.
- The `latestReturnsNullTotalWhenTheLatestDateIsUnresolved` test stubs `latest()` → a view with `totalSales = null`, `resolutionType = "conflict"`.

`DashboardControllerTest.java`:
- Replace `@MockitoBean DailySalesReconciliationService reconciliation;` with `@MockitoBean ResolvedDailySalesQuery resolvedDailySales;`.
- In `summaryPopulatesIngestionMetrics` and `summaryReturnsNullMetricsWhenLedgerIsEmpty`, replace `when(reconciliation.conflicts()).thenReturn(List.of())` with `when(resolvedDailySales.countOpenConflicts()).thenReturn(0L);`.

`ReconciliationControllerTest.java`:
- Replace `@MockitoBean DailySalesReconciliationService reconciliation;` with `@MockitoBean ReconciliationExceptionQuery exceptionsQuery;`.
- In `exceptionsReturnsTheConflict`, replace `when(reconciliation.conflicts())...` with `when(exceptionsQuery.listDaily()).thenReturn(List.of(new ReconciliationExceptionQuery.DailyException(LocalDate.of(2026,9,13), "conflict", List.of(new SourceTotal("LIGHTSPEED", new BigDecimal("27650.66"), null), new SourceTotal("CTB", new BigDecimal("20990.83"), null)))));`.

`GetSalesByPeriodToolTest.java`:
- Replace the `DailySalesReconciliationService` mock with a `ResolvedDailySalesQuery` mock; stub `resolved.between(...)` with a `List.of(view(SEP_13, "27650.66", "agreed"), view(SEP_14, null, null))`. Keep the assertions on `widget.data()` size and `notices`.

- [ ] **Step 7: Run the full backend suite**

Run: `cd backend && ./gradlew test --console=plain`
Expected: PASS (all tests, including the updated controller/tool tests).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/api/SalesController.java \
        backend/src/main/java/com/goldys/platform/api/DashboardController.java \
        backend/src/main/java/com/goldys/platform/api/ReconciliationController.java \
        backend/src/main/java/com/goldys/platform/reporting/GetSalesByPeriodTool.java \
        backend/src/main/java/com/goldys/platform/reconciliation/DailySalesReconciliationService.java \
        backend/src/main/java/com/goldys/platform/reconciliation/DailySalesResolved.java \
        backend/src/main/java/com/goldys/platform/reconciliation/DailySalesConflict.java \
        backend/src/test/java/com/goldys/platform/reconciliation/DailySalesReconciliationTest.java \
        backend/src/test/java/com/goldys/platform/api/SalesControllerTest.java \
        backend/src/test/java/com/goldys/platform/api/DashboardControllerTest.java \
        backend/src/test/java/com/goldys/platform/api/ReconciliationControllerTest.java \
        backend/src/test/java/com/goldys/platform/reporting/GetSalesByPeriodToolTest.java
git commit -m "refactor: migrate daily-sales reads to the projection and remove on-demand reconciliation"
```

> The `git add` above lists deletions; `git add -A` on the listed paths, or `git add` each with `git rm` for the deleted files, is equivalent. Use `git add -A backend/src/main/java/com/goldys/platform/reconciliation backend/src/main/java/com/goldys/platform/api backend/src/main/java/com/goldys/platform/reporting backend/src/test/java/com/goldys/platform/reconciliation backend/src/test/java/com/goldys/platform/api backend/src/test/java/com/goldys/platform/reporting` if simpler, then verify the diff is scoped.

---

### Task 8: Startup backfill seeder

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/StartupProjectionSeeder.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/StartupProjectionSeederIntegrationTest.java`

**Interfaces:**
- Consumes: `DailySalesProjector.recomputeAll()` (Task 4), `ResolvedDailySalesRepository.count()` (Task 2).

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/StartupProjectionSeederIntegrationTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class StartupProjectionSeederIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedDailySalesRepository repository;
  @Autowired StartupProjectionSeeder seeder;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_daily_sales");
    jdbc.update("truncate table reconciliation_exception");
    jdbc.update("truncate table canonical_daily_sales");
  }

  @Test
  void seedsWhenProjectionIsEmpty() {
    // Seed canonical directly via SQL so the projection-listener does not run and pre-populate the
    // projection — the point is to exercise the seeder's own recomputeAll().
    seedCanonical("CTB", LocalDate.of(2026, 9, 13), "100.00");

    seeder.run(mockApplicationArguments());

    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.findTopByOrderByTradingDateDesc().get().resolutionType())
        .isEqualTo("missing");
  }

  @Test
  void doesNotRecomputeWhenProjectionIsAlreadyPopulated() {
    repository.save(
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 13), new BigDecimal("100.00"), "agreed", "agreed", false,
            Instant.EPOCH));
    jdbc.update("truncate table canonical_daily_sales");

    seeder.run(mockApplicationArguments());

    // Projection untouched because it was not empty.
    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.findTopByOrderByTradingDateDesc().get().resolutionType())
        .isEqualTo("agreed");
  }

  // canonical_daily_sales.raw_record_id is a NOT NULL FK to raw_record(id) — insert real rows.
  private void seedCanonical(String source, LocalDate date, String total) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    UUID entityId = UUID.randomUUID();
    UUID logicalId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, ?, 'test', 'SUCCESS', now(), 1, 1)",
        runId,
        source);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, ?, 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        source,
        new byte[] {1},
        "0".repeat(64),
        1);
    jdbc.update(
        "insert into canonical_daily_sales (id, logical_entity_id, trading_date, source_system, source_record_ref, raw_record_id, total_sales, gst_total, net_total, valid_from, recorded_at) "
            + "values (?, ?, ?, ?, 'ref', ?, ?, 0, 0, now(), now())",
        entityId,
        logicalId,
        date,
        source,
        recordId,
        new BigDecimal(total));
  }

  private static ApplicationArguments mockApplicationArguments() {
    return org.mockito.Mockito.mock(ApplicationArguments.class);
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.StartupProjectionSeederIntegrationTest" --console=plain`
Expected: compilation FAIL — `StartupProjectionSeeder` does not exist.

- [ ] **Step 3: Write the seeder**

Create `StartupProjectionSeeder.java`:

```java
package com.goldys.platform.reconciliation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds the resolved projection on first boot of the read-model tables; a no-op once populated. */
@Component
public class StartupProjectionSeeder implements ApplicationRunner {
  private final ResolvedDailySalesRepository resolved;
  private final DailySalesProjector projector;

  public StartupProjectionSeeder(ResolvedDailySalesRepository resolved, DailySalesProjector projector) {
    this.resolved = resolved;
    this.projector = projector;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (resolved.count() == 0) {
      projector.recomputeAll();
    }
  }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.StartupProjectionSeederIntegrationTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Run the full backend suite, then Spotless**

Run: `cd backend && ./gradlew test spotlessCheck --console=plain`
Expected: PASS (all tests + formatting).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/StartupProjectionSeeder.java \
        backend/src/test/java/com/goldys/platform/reconciliation/StartupProjectionSeederIntegrationTest.java
git commit -m "feat: seed the daily-sales projection on first boot"
```

---

## Self-review notes (already applied)

- Spec §5 (schema) → Task 1; §6 (resolution semantics) → Task 3 + pinned by Review Focus 1, 4; §7 (projector) → Task 4; §8 (trigger) → Task 5; §9 (queries) → Task 6; §10 (consumers) → Task 7; §11 (removal) → Task 7; §12 (replay) → Task 8; §13 (testing) → Tasks 2–8.
- Bean-cycle risk (projector → service → projector) avoided by injecting `ResolutionRuleRepository`/`DailySalesOverrideRepository` into the projector, documented in Global Constraints and Task 4.
- Review Focus items 1–5 are each pinned by a test named in the corresponding task.
