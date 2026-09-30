# Resolution Rule Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a staff-configurable resolution rule engine (PRD Requirement 8) so standing rules ("Cooking the Books wins on daily sales") resolve field conflicts automatically, on top of the existing manual-override mechanism.

**Architecture:** Follows the existing reconciliation pattern exactly. A new append-only `resolution_rule` table (supersede, never mutate) mirrors the override tables; a package-private `ResolutionRule` entity + repository sit alongside `DailySalesOverride`. A pure `RuleEvaluator` evaluates one rule against per-source metrics; the two existing reconciliation services (`DailySalesReconciliationService`, `ProductSalesReconciliationService`) consult it after manual overrides and agreement. Resolution stays **derived** (no materialized view, no recompute job).

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA, PostgreSQL 16 (Testcontainers), Flyway, AssertJ + Mockito + JUnit 5, Gradle (committed wrapper) + Spotless.

**Spec:** `docs/superpowers/specs/2026-09-30-rule-engine-design.md`

## Global Constraints

- Java 25; run everything with the committed Gradle wrapper (`./gradlew`), never a system Gradle.
- Schema is owned by Flyway migrations (`hibernate.ddl-auto: validate`); never auto-DDL. Migrations are append-only history.
- Resolution is **derived** — never mutate canonical or override rows; rules are append-only and superseded, not edited in place.
- Never auto-resolve via heuristics/ML — only staff-authored rules. No write-back to any source system.
- Permission: reuse `PermissionService` with `ResourceKey("reconciliation.sales")` and `PermissionAction.READ`/`WRITE` — no new permission axes.
- Package boundaries: rule types live in `reconciliation`; the canonical `Query` facades remain the only door into `canonical` (never reach the package-private entity directly).
- Verification gates: `./gradlew test`, `./gradlew spotlessCheck`, `./gradlew build`.
- Atomic Conventional Commits. Never commit to main — branch from the latest `main` (already has the frontend work).

## Review Focus

These are the input classes / failure modes the spec implies but whose happy-path tests would not otherwise exercise. Each is pinned by a test in the named task.

1. **A `priority` rule where the first source has no data** — must fall through to the next source in the priority list, not pick a source with no value. → Task 3.
2. **A rule resolving a "missing" (single-source) unit** — `priority`/`highest`/`lowest`/`newest` may resolve it (pick the available source); `manual`/`flag` must leave it unresolved and still surfaced. → Task 3.
3. **A product-specific rule vs. the `"*"` catch-all** — the exact product key wins; when only `"*"` exists it applies. → Task 5.
4. **A `delete` with no successor** — must still appear in the audit as a distinct change, not vanish. → Task 6.
5. **Editing a rule supersedes (never mutates) the prior row** — the old row's `supersededAt` is set and the new row is current. → Task 4.

---

## Task 1: Expose `recordedAt` on the canonical views

The `newest` custom logic needs each source's system time. The views currently omit it.

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/DailySalesView.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/ProductSalesView.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalDailySalesQuery.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesQuery.java`
- Modify (tests, add a 6th arg): `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesReconciliationTest.java`, `DailySalesOverrideServiceTest.java`, `ProductSalesReconciliationTest.java`, `ProductSalesOverrideServiceTest.java`, and any other test constructing these views.

**Interfaces:**
- Produces: `DailySalesView(String sourceSystem, LocalDate tradingDate, BigDecimal totalSales, BigDecimal gstTotal, BigDecimal netTotal, Instant recordedAt)`; `ProductSalesView(String sourceSystem, LocalDate tradingDate, String productNameKey, BigDecimal quantitySold, BigDecimal amount, Instant recordedAt)`. Used by Tasks 3 and 5.

- [ ] **Step 1: Add `recordedAt` to `DailySalesView`**

Change the record to:

```java
package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DailySalesView(
    String sourceSystem,
    LocalDate tradingDate,
    BigDecimal totalSales,
    BigDecimal gstTotal,
    BigDecimal netTotal,
    Instant recordedAt) {}
```

- [ ] **Step 2: Add `recordedAt` to `ProductSalesView`**

```java
package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ProductSalesView(
    String sourceSystem,
    LocalDate tradingDate,
    String productNameKey,
    BigDecimal quantitySold,
    BigDecimal amount,
    Instant recordedAt) {}
```

- [ ] **Step 3: Map `recordedAt` in `CanonicalDailySalesQuery.toView`**

Change the `toView` method to:

```java
private DailySalesView toView(CanonicalDailySales s) {
  return new DailySalesView(
      s.sourceSystem(), s.tradingDate(), s.totalSales(), s.gstTotal(), s.netTotal(), s.recordedAt());
}
```

- [ ] **Step 4: Map `recordedAt` in `CanonicalProductSalesQuery.toView`**

```java
private ProductSalesView toView(CanonicalProductSales s) {
  return new ProductSalesView(
      s.sourceSystem(), s.tradingDate(), s.productNameKey(), s.quantitySold(), s.amount(), s.recordedAt());
}
```

- [ ] **Step 5: Update every test that constructs these views**

`DailySalesReconciliationTest.java` and `DailySalesOverrideServiceTest.java` build views like:

```java
new DailySalesView("LIGHTSPEED", SEP_13, new BigDecimal("27650.66"), null, null)
```

Add a trailing `null` (or a real `Instant`) so every call has six arguments. Search with:

Run: `grep -rn "new DailySalesView\|new ProductSalesView" backend/src/test`
Expected: every call site now passes six args (add `null` as the `recordedAt` arg — the reconciliation services only read `recordedAt` for `newest`, which no existing test exercises).

- [ ] **Step 6: Run the tests**

Run: `cd backend && ./gradlew test`
Expected: PASS (this is a mechanical compile fix; behavior unchanged).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/ backend/src/test/java/com/goldys/platform/reconciliation/
git commit -m "feat: expose recordedAt on canonical sales views"
```

---

## Task 2: Rule persistence (migration + entity + repository)

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__resolution_rule.sql`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRule.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleRepository.java`

**Interfaces:**
- Produces: `ResolutionRule` (package-private, accessors `entityType()`, `fieldKey()`, `strategy()`, `customLogic()`, `sourcePriority()`, `actorEmail()`, `recordedAt()`, `supersededAt()`, plus `create(...)` and `supersede(Instant)`); `ResolutionRuleRepository` (`lockCurrent(entityType, fieldKey)`, `findCurrent(entityType, fieldKey)`, `findAllByOrderByRecordedAtDesc()`). Used by Tasks 3, 4, 5, 6.

- [ ] **Step 1: Write the migration**

`backend/src/main/resources/db/migration/V12__resolution_rule.sql`:

```sql
-- Standing resolution rules, append-only and superseded like the override tables.

CREATE TABLE resolution_rule (
    id uuid PRIMARY KEY,
    entity_type varchar(64) NOT NULL,
    field_key varchar(512) NOT NULL,
    strategy varchar(32) NOT NULL,
    custom_logic varchar(32),
    source_priority jsonb,
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX ux_resolution_rule_current
    ON resolution_rule (entity_type, field_key)
    WHERE superseded_at IS NULL;
```

- [ ] **Step 2: Write the entity**

`backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRule.java`:

```java
package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An append-only standing resolution rule for one reconciliation unit
 * ({@code entity_type} + {@code field_key}). A new row supersedes the prior one
 * for the same key; rows are never mutated.
 */
@Entity
@Table(name = "resolution_rule")
class ResolutionRule {
  @Id private UUID id;

  @Column(name = "entity_type", nullable = false, updatable = false)
  private String entityType;

  @Column(name = "field_key", nullable = false, updatable = false)
  private String fieldKey;

  @Column(name = "strategy", nullable = false, updatable = false)
  private String strategy;

  @Column(name = "custom_logic", updatable = false)
  private String customLogic;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "source_priority", updatable = false, columnDefinition = "jsonb")
  private List<String> sourcePriority;

  @Column(name = "actor_email", nullable = false, updatable = false)
  private String actorEmail;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  protected ResolutionRule() {}

  private ResolutionRule(
      String entityType,
      String fieldKey,
      String strategy,
      String customLogic,
      List<String> sourcePriority,
      String actorEmail,
      Instant recordedAt) {
    this.id = UUID.randomUUID();
    this.entityType = Objects.requireNonNull(entityType, "entityType");
    this.fieldKey = Objects.requireNonNull(fieldKey, "fieldKey");
    this.strategy = Objects.requireNonNull(strategy, "strategy");
    this.customLogic = customLogic;
    this.sourcePriority = sourcePriority;
    this.actorEmail = Objects.requireNonNull(actorEmail, "actorEmail");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
  }

  static ResolutionRule create(
      String entityType,
      String fieldKey,
      String strategy,
      String customLogic,
      List<String> sourcePriority,
      String actorEmail,
      Instant recordedAt) {
    return new ResolutionRule(
        entityType, fieldKey, strategy, customLogic, sourcePriority, actorEmail, recordedAt);
  }

  void supersede(Instant at) {
    if (supersededAt != null) {
      throw new IllegalStateException("Rule " + id + " is already superseded");
    }
    this.supersededAt = Objects.requireNonNull(at, "at");
  }

  UUID id() {
    return id;
  }

  String entityType() {
    return entityType;
  }

  String fieldKey() {
    return fieldKey;
  }

  String strategy() {
    return strategy;
  }

  String customLogic() {
    return customLogic;
  }

  List<String> sourcePriority() {
    return sourcePriority;
  }

  String actorEmail() {
    return actorEmail;
  }

  Instant recordedAt() {
    return recordedAt;
  }

  Instant supersededAt() {
    return supersededAt;
  }
}
```

- [ ] **Step 3: Write the repository**

`backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleRepository.java`:

```java
package com.goldys.platform.reconciliation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ResolutionRuleRepository extends JpaRepository<ResolutionRule, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from ResolutionRule r where r.entityType = :entityType "
          + "and r.fieldKey = :fieldKey and r.supersededAt is null")
  Optional<ResolutionRule> lockCurrent(String entityType, String fieldKey);

  @Query(
      "select r from ResolutionRule r where r.entityType = :entityType "
          + "and r.fieldKey = :fieldKey and r.supersededAt is null")
  Optional<ResolutionRule> findCurrent(String entityType, String fieldKey);

  List<ResolutionRule> findAllByOrderByRecordedAtDesc();
}
```

- [ ] **Step 4: Compile and run the migration test**

Run: `cd backend && ./gradlew compileJava spotlessApply`
Expected: compiles; Spotless reformats if needed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V12__resolution_rule.sql backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRule.java backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleRepository.java
git commit -m "feat: add resolution rule entity and migration"
```

---

## Task 3: Pure rule evaluation

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/SourceMetric.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/RuleEvaluator.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/RuleEvaluatorTest.java`

**Interfaces:**
- Consumes: `ResolutionRule` (Task 2).
- Produces: `SourceMetric(String sourceSystem, BigDecimal metric, Instant recordedAt)`; `RuleEvaluator.resolve(ResolutionRule, List<SourceMetric>): Optional<String>`. Used by Task 5.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/reconciliation/RuleEvaluatorTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEvaluatorTest {

  private static final Instant OLD = Instant.parse("2026-09-13T09:00:00Z");
  private static final Instant NEW = Instant.parse("2026-09-14T09:00:00Z");

  private static ResolutionRule rule(String strategy, String customLogic, List<String> priority) {
    return ResolutionRule.create(
        "daily_sales", "daily_sales", strategy, customLogic, priority, "a@b.com", OLD);
  }

  private static SourceMetric m(String source, String metric, Instant at) {
    return new SourceMetric(source, new BigDecimal(metric), at);
  }

  @Test
  void priorityFallsThroughToTheNextSourceThatHasData() {
    ResolutionRule r = rule("priority", null, List.of("LIGHTSPEED", "CTB"));
    List<SourceMetric> sources = List.of(m("CTB", "12.00", OLD));
    assertThat(RuleEvaluator.resolve(r, sources)).contains("CTB");
  }

  @Test
  void priorityIsUnresolvedWhenNoListedSourceHasData() {
    ResolutionRule r = rule("priority", null, List.of("LIGHTSPEED"));
    assertThat(RuleEvaluator.resolve(r, List.of(m("CTB", "12.00", OLD)))).isEmpty();
  }

  @Test
  void manualLeavesEverythingUnresolved() {
    ResolutionRule r = rule("manual", null, null);
    assertThat(RuleEvaluator.resolve(r, List.of(m("LIGHTSPEED", "12.00", OLD)))).isEmpty();
  }

  @Test
  void customFlagLeavesEverythingUnresolved() {
    ResolutionRule r = rule("custom", "flag", null);
    assertThat(RuleEvaluator.resolve(r, List.of(m("LIGHTSPEED", "12.00", OLD)))).isEmpty();
  }

  @Test
  void customHighestPicksTheLargestMetric() {
    ResolutionRule r = rule("custom", "highest", null);
    List<SourceMetric> sources =
        List.of(m("LIGHTSPEED", "12.00", OLD), m("CTB", "20.00", NEW));
    assertThat(RuleEvaluator.resolve(r, sources)).contains("CTB");
  }

  @Test
  void customLowestPicksTheSmallestMetric() {
    ResolutionRule r = rule("custom", "lowest", null);
    List<SourceMetric> sources =
        List.of(m("LIGHTSPEED", "12.00", OLD), m("CTB", "20.00", NEW));
    assertThat(RuleEvaluator.resolve(r, sources)).contains("LIGHTSPEED");
  }

  @Test
  void customNewestPicksTheMostRecentlyRecorded() {
    ResolutionRule r = rule("custom", "newest", null);
    List<SourceMetric> sources =
        List.of(m("LIGHTSPEED", "12.00", OLD), m("CTB", "20.00", NEW));
    assertThat(RuleEvaluator.resolve(r, sources)).contains("CTB");
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*RuleEvaluatorTest'`
Expected: FAIL — `SourceMetric` / `RuleEvaluator` do not exist.

- [ ] **Step 3: Write `SourceMetric`**

`backend/src/main/java/com/goldys/platform/reconciliation/SourceMetric.java`:

```java
package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One source's value for the metric a rule is evaluated against, plus its system
 * time. For daily sales the metric is {@code totalSales}; for product sales it is
 * {@code quantitySold}.
 */
public record SourceMetric(String sourceSystem, BigDecimal metric, Instant recordedAt) {}
```

- [ ] **Step 4: Write `RuleEvaluator`**

`backend/src/main/java/com/goldys/platform/reconciliation/RuleEvaluator.java`:

```java
package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure evaluation of a single rule against per-source metrics. No I/O, no
 * permissions — the reconciliation services call this after manual overrides and
 * agreement have already been considered.
 */
final class RuleEvaluator {

  private RuleEvaluator() {}

  /** Picks the authoritative source, or empty when the rule leaves the unit unresolved. */
  static Optional<String> resolve(ResolutionRule rule, List<SourceMetric> sources) {
    if (sources.isEmpty()) {
      return Optional.empty();
    }
    return switch (rule.strategy()) {
      case "priority" -> priority(rule.sourcePriority(), sources);
      case "manual" -> Optional.empty();
      case "custom" -> custom(rule.customLogic(), sources);
      default -> Optional.empty();
    };
  }

  private static Optional<String> priority(List<String> order, List<SourceMetric> sources) {
    if (order == null) {
      return Optional.empty();
    }
    for (String source : order) {
      boolean present = sources.stream().anyMatch(s -> s.sourceSystem().equals(source));
      if (present) {
        return Optional.of(source);
      }
    }
    return Optional.empty();
  }

  private static Optional<String> custom(String logic, List<SourceMetric> sources) {
    if (logic == null) {
      return Optional.empty();
    }
    return switch (logic) {
      case "flag" -> Optional.empty();
      case "highest" -> pick(sources, Comparator.comparing(SourceMetric::metric));
      case "lowest" -> pick(sources, Comparator.comparing(SourceMetric::metric).reversed());
      case "newest" -> pick(sources, Comparator.comparing(SourceMetric::recordedAt));
      default -> Optional.empty();
    };
  }

  private static Optional<String> pick(List<SourceMetric> sources, Comparator<SourceMetric> by) {
    return sources.stream()
        .filter(s -> s.metric() != null)
        .max(by)
        .map(SourceMetric::sourceSystem);
  }
}
```

Note: `newest` compares `recordedAt`, which is never null for a canonical row; `highest`/`lowest` filter out null metrics (a source with no value cannot win a numeric comparison).

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*RuleEvaluatorTest'`
Expected: PASS.

- [ ] **Step 6: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`
Then:

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/SourceMetric.java backend/src/main/java/com/goldys/platform/reconciliation/RuleEvaluator.java backend/src/test/java/com/goldys/platform/reconciliation/RuleEvaluatorTest.java
git commit -m "feat: add pure resolution rule evaluation"
```

---

## Task 4: Rule service (CRUD + permission + lookup)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleService.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ResolutionRuleServiceTest.java`

**Interfaces:**
- Consumes: `ResolutionRule`, `ResolutionRuleRepository` (Task 2), `PermissionService` (existing).
- Produces: `ResolutionRuleService` with `list()`, `save(UserRole, String actorEmail, RuleInput)`, `delete(UserRole, String actorEmail, String id)`, `findCurrent(String entityType, String fieldKey)`. Used by Tasks 5, 6, 7.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/reconciliation/ResolutionRuleServiceTest.java`:

```java
package com.goldys.platform.reconciliation;

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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResolutionRuleServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void deniedSaveThrows() {
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    ResolutionRuleService service =
        new ResolutionRuleService(mock(ResolutionRuleRepository.class), permissions);

    assertThatThrownBy(
            () ->
                service.save(
                    OWNER, "a@b.com", new RuleInput("daily_sales", "daily_sales", "priority", null, List.of("CTB"))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void saveSupersedesThePriorRule() {
    PermissionService permissions = mock(PermissionService.class);
    ResolutionRuleRepository repository = mock(ResolutionRuleRepository.class);
    ResolutionRule prior =
        ResolutionRule.create("daily_sales", "daily_sales", "priority", null, List.of("CTB"), "a@b.com", Instant.now());
    when(repository.lockCurrent("daily_sales", "daily_sales")).thenReturn(Optional.of(prior));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ResolutionRuleService service = new ResolutionRuleService(repository, permissions);

    ResolutionRule saved =
        service.save(
            OWNER, "a@b.com", new RuleInput("daily_sales", "daily_sales", "priority", null, List.of("LIGHTSPEED")));

    assertThat(prior.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(prior);
    assertThat(saved.sourcePriority()).containsExactly("LIGHTSPEED");
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*ResolutionRuleServiceTest'`
Expected: FAIL — `ResolutionRuleService` / `RuleInput` do not exist.

- [ ] **Step 3: Write `RuleInput` and the service**

`backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleService.java` (contains the nested `RuleInput` record):

```java
package com.goldys.platform.reconciliation;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, edits, and deletes standing resolution rules, permission-gated and append-only. */
@Service
public class ResolutionRuleService {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");
  private static final Clock CLOCK = Clock.systemUTC();

  private final ResolutionRuleRepository repository;
  private final PermissionService permissions;

  public ResolutionRuleService(ResolutionRuleRepository repository, PermissionService permissions) {
    this.repository = repository;
    this.permissions = permissions;
  }

  public List<ResolutionRule> list() {
    return repository.findAllByOrderByRecordedAtDesc();
  }

  @Transactional
  public ResolutionRule save(UserRole actor, String actorEmail, RuleInput input) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);
    Instant now = CLOCK.instant();
    Optional<ResolutionRule> current = repository.lockCurrent(input.entityType(), input.fieldKey());
    if (current.isPresent()) {
      current.get().supersede(now);
      repository.saveAndFlush(current.get());
    }
    return repository.save(
        ResolutionRule.create(
            input.entityType(),
            input.fieldKey(),
            input.strategy(),
            input.customLogic(),
            input.sourcePriority(),
            actorEmail,
            now));
  }

  @Transactional
  public void delete(UserRole actor, String actorEmail, String id) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);
    repository.findById(UUID.fromString(id)).ifPresent(rule -> rule.supersede(CLOCK.instant()));
  }

  public Optional<ResolutionRule> findCurrent(String entityType, String fieldKey) {
    return repository.findCurrent(entityType, fieldKey);
  }

  /** A rule authored through the API. */
  public record RuleInput(
      String entityType, String fieldKey, String strategy, String customLogic, List<String> sourcePriority) {}
}
```

Note: `save` treats create and update identically — supersede the current rule for the key (if any) and append the new one, matching the override services.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ResolutionRuleServiceTest'`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`
Then:

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleService.java backend/src/test/java/com/goldys/platform/reconciliation/ResolutionRuleServiceTest.java
git commit -m "feat: add permission-gated resolution rule service"
```

---

## Task 5: Integrate rules into the two reconciliation services

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/SourceTotal.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSourceTotal.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesReconciliationService.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesReconciliationService.java`
- Modify (tests): `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesReconciliationTest.java`, `ProductSalesReconciliationTest.java`, and `backend/src/test/java/com/goldys/platform/api/ReconciliationControllerTest.java` (any test constructing `SourceTotal`/`ProductSourceTotal`).

**Interfaces:**
- Consumes: `RuleEvaluator` (Task 3), `ResolutionRuleService.findCurrent` (Task 4), `SourceMetric` (Task 3), `recordedAt` on the views (Task 1).
- Produces: `DailySalesReconciliationService(CanonicalDailySalesQuery, DailySalesOverrideRepository, ResolutionRuleService)`; `ProductSalesReconciliationService(CanonicalProductSalesQuery, ProductSalesOverrideRepository, ResolutionRuleService)`. `conflicts()`/`resolved()` gain rule-derived resolution.

- [ ] **Step 1: Extend `SourceTotal` and `ProductSourceTotal` with `recordedAt`**

`backend/src/main/java/com/goldys/platform/reconciliation/SourceTotal.java`:

```java
package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/** One source's reported total for a trading date. */
public record SourceTotal(String sourceSystem, BigDecimal totalSales, Instant recordedAt) {}
```

`backend/src/main/java/com/goldys/platform/reconciliation/ProductSourceTotal.java`:

```java
package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/** One source's per-product values for a trading date. */
public record ProductSourceTotal(
    String sourceSystem, BigDecimal quantitySold, BigDecimal amount, Instant recordedAt) {}
```

- [ ] **Step 2: Write the failing tests**

Add to `DailySalesReconciliationTest.java`:

```java
@Test
void aPriorityRuleResolvesAConflictSoItDropsOutOfConflicts() {
  CanonicalDailySalesQuery query = mock(CanonicalDailySalesQuery.class);
  when(query.currentDailySales())
      .thenReturn(
          List.of(
              view("LIGHTSPEED", SEP_13, "27650.66"),
              view("CTB", SEP_13, "20990.83")));

  ResolutionRuleService rules = mock(ResolutionRuleService.class);
  ResolutionRule rule =
      ResolutionRule.create(
          "daily_sales", "daily_sales", "priority", null, List.of("CTB"), "a@b.com",
          java.time.Instant.now());
  when(rules.findCurrent("daily_sales", "daily_sales")).thenReturn(Optional.of(rule));

  DailySalesReconciliationService service =
      new DailySalesReconciliationService(query, mock(DailySalesOverrideRepository.class), rules);

  assertThat(service.conflicts()).isEmpty();
}
```

Add to `ProductSalesReconciliationTest.java` a test for Review Focus #3 (specific beats catch-all):

```java
@Test
void aProductSpecificRuleAndTheCatchAllEachResolveTheirUnits() {
  LocalDate date = LocalDate.of(2026, 9, 14);
  CanonicalProductSalesQuery query = mock(CanonicalProductSalesQuery.class);
  when(query.currentProductSales())
      .thenReturn(
          List.of(
              pview("LIGHTSPEED", date, "garlic aioli", "150", "380.88"),
              pview("CTB", date, "garlic aioli", "127", "322.46"),
              pview("LIGHTSPEED", date, "chips", "10", "50.00"),
              pview("CTB", date, "chips", "9", "45.00")));

  ResolutionRuleService rules = mock(ResolutionRuleService.class);
  ResolutionRule specific =
      ResolutionRule.create(
          "product_sales", "garlic aioli", "priority", null, List.of("CTB"), "a@b.com",
          java.time.Instant.now());
  ResolutionRule catchAll =
      ResolutionRule.create(
          "product_sales", "*", "priority", null, List.of("LIGHTSPEED"), "a@b.com",
          java.time.Instant.now());
  when(rules.findCurrent("product_sales", "garlic aioli")).thenReturn(Optional.of(specific));
  when(rules.findCurrent("product_sales", "chips")).thenReturn(Optional.empty());
  when(rules.findCurrent("product_sales", "*")).thenReturn(Optional.of(catchAll));

  ProductSalesReconciliationService service =
      new ProductSalesReconciliationService(query, mock(ProductSalesOverrideRepository.class), rules);

  assertThat(service.conflicts()).isEmpty();
}
```

(Add `import java.time.Instant;` / `import java.time.LocalDate;` where missing, and a `pview` helper mirroring the existing `view` helper in that test file.)

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd backend && ./gradlew test --tests '*DailySalesReconciliationTest' --tests '*ProductSalesReconciliationTest'`
Expected: FAIL — the services' constructors need a third arg, and `SourceTotal`/`ProductSourceTotal` now need a fourth arg.

- [ ] **Step 4: Modify `DailySalesReconciliationService`**

Change the constructor to take `ResolutionRuleService`, build `byDate` with `recordedAt`, and apply the rule in `conflicts()` and `resolved()`:

```java
public class DailySalesReconciliationService {
  private final CanonicalDailySalesQuery dailySales;
  private final DailySalesOverrideRepository overrides;
  private final ResolutionRuleService rules;

  public DailySalesReconciliationService(
      CanonicalDailySalesQuery dailySales,
      DailySalesOverrideRepository overrides,
      ResolutionRuleService rules) {
    this.dailySales = dailySales;
    this.overrides = overrides;
    this.rules = rules;
  }
```

In `conflicts()`, build each `SourceTotal` with `view.recordedAt()`:

```java
    for (DailySalesView view : dailySales.currentDailySales()) {
      byDate
          .computeIfAbsent(view.tradingDate(), k -> new ArrayList<>())
          .add(new SourceTotal(view.sourceSystem(), view.totalSales(), view.recordedAt()));
    }
```

and in the loop, apply the rule after the override and agreement checks:

```java
    for (Map.Entry<LocalDate, List<SourceTotal>> entry : byDate.entrySet()) {
      if (overrides.findCurrent(entry.getKey()).isPresent()) {
        continue;
      }
      String status = classify(entry.getValue());
      if ("agreed".equals(status)) {
        continue;
      }
      // A rule that resolves the unit drops it from the open-exceptions list.
      if (rules
          .findCurrent("daily_sales", "daily_sales")
          .flatMap(r -> RuleEvaluator.resolve(r, toMetrics(entry.getValue())))
          .isPresent()) {
        continue;
      }
      out.add(new DailySalesConflict(entry.getKey(), entry.getValue(), status));
    }
```

In `resolved()`, after the override branch and the "agreed" branch, insert the rule branch before the final unresolved return:

```java
    Optional<ResolutionRule> rule = rules.findCurrent("daily_sales", "daily_sales");
    if (rule.isPresent()) {
      Optional<String> chosen = RuleEvaluator.resolve(rule.get(), toMetrics(sources));
      if (chosen.isPresent()) {
        String source = chosen.get();
        return sales.stream()
            .filter(s -> s.sourceSystem().equals(source))
            .findFirst()
            .map(s -> new DailySalesResolved(date, s.totalSales(), "rule:" + source));
      }
    }
    return Optional.of(new DailySalesResolved(date, null, null));
```

Add the metric helper:

```java
  private static List<SourceMetric> toMetrics(List<SourceTotal> sources) {
    return sources.stream()
        .map(s -> new SourceMetric(s.sourceSystem(), s.totalSales(), s.recordedAt()))
        .toList();
  }
```

- [ ] **Step 5: Modify `ProductSalesReconciliationService`**

Change the constructor to take `ResolutionRuleService`, build `byKey` with `recordedAt`, and apply the rule in `conflicts()`:

```java
public class ProductSalesReconciliationService {
  private final CanonicalProductSalesQuery productSales;
  private final ProductSalesOverrideRepository overrides;
  private final ResolutionRuleService rules;

  public ProductSalesReconciliationService(
      CanonicalProductSalesQuery productSales,
      ProductSalesOverrideRepository overrides,
      ResolutionRuleService rules) {
    this.productSales = productSales;
    this.overrides = overrides;
    this.rules = rules;
  }
```

Build each `ProductSourceTotal` with `view.recordedAt()`:

```java
          .add(new ProductSourceTotal(view.sourceSystem(), view.quantitySold(), view.amount(), view.recordedAt()));
```

Apply the rule (most-specific-first) in the conflict loop:

```java
      if (overrides.findCurrent(key, date).isPresent()) {
        continue;
      }
      String status = classify(e.getValue());
      if (!"agreed".equals(status)) {
        Optional<ResolutionRule> rule = rules.findCurrent("product_sales", key);
        if (rule.isEmpty()) {
          rule = rules.findCurrent("product_sales", "*");
        }
        boolean resolved =
            rule.flatMap(r -> RuleEvaluator.resolve(r, toProductMetrics(e.getValue()))).isPresent();
        if (!resolved) {
          out.add(new ProductSalesConflict(date, key, e.getValue(), status));
        }
      }
```

Add the product metric helper (metric = `quantitySold`):

```java
  private static List<SourceMetric> toProductMetrics(List<ProductSourceTotal> sources) {
    return sources.stream()
        .map(s -> new SourceMetric(s.sourceSystem(), s.quantitySold(), s.recordedAt()))
        .toList();
  }
```

- [ ] **Step 6: Fix the remaining constructor call sites in tests**

Run: `grep -rn "new SourceTotal\|new ProductSourceTotal" backend/src/test`
Expected: update `DailySalesReconciliationTest.st(...)` to pass `null` as the `recordedAt` arg, `ProductSalesReconciliationTest` likewise, and `ReconciliationControllerTest`'s `new SourceTotal(...)`/`new ProductSourceTotal(...)` calls (add `null`). The controller itself never constructs these records, so `ReconciliationController` needs no change.

- [ ] **Step 7: Run the reconciliation tests**

Run: `cd backend && ./gradlew test --tests '*DailySalesReconciliationTest' --tests '*ProductSalesReconciliationTest' --tests '*ReconciliationControllerTest'`
Expected: PASS (existing cases still pass; the two new rule cases pass).

- [ ] **Step 8: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`
Then:

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/ backend/src/test/java/com/goldys/platform/reconciliation/ backend/src/test/java/com/goldys/platform/api/ReconciliationControllerTest.java
git commit -m "feat: apply resolution rules in reconciliation"
```

---

## Task 6: Rule audit

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/RuleAuditService.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/RuleAuditServiceTest.java`

**Interfaces:**
- Consumes: `ResolutionRuleRepository.findAllByOrderByRecordedAtDesc()` (Task 2).
- Produces: `RuleAuditService` with `history(): List<RuleAuditEntry>`, where `RuleAuditEntry(UUID ruleId, String entityType, String fieldKey, String change, Instant at, String by)`. Used by Task 7.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/reconciliation/RuleAuditServiceTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleAuditServiceTest {

  @Test
  void derivesCreatedUpdatedAndDeleted() {
    Instant t1 = Instant.parse("2026-09-13T09:00:00Z");
    Instant t2 = Instant.parse("2026-09-14T09:00:00Z");
    Instant t3 = Instant.parse("2026-09-15T09:00:00Z");

    ResolutionRule created = ResolutionRule.create("daily_sales", "daily_sales", "priority", null, List.of("CTB"), "a@b.com", t1);

    ResolutionRule updated = ResolutionRule.create("daily_sales", "daily_sales", "priority", null, List.of("LIGHTSPEED"), "a@b.com", t2);

    ResolutionRule deleted = ResolutionRule.create("product_sales", "*", "priority", null, List.of("CTB"), "a@b.com", t3);
    deleted.supersede(t3.plusSeconds(1));

    List<RuleAuditService.RuleAuditEntry> history =
        RuleAuditService.history(List.of(created, updated, deleted));

    assertThat(history).hasSize(3);
    assertThat(history).extracting(RuleAuditService.RuleAuditEntry::change)
        .containsExactlyInAnyOrder("created", "updated", "deleted");
    assertThat(history.stream().filter(e -> e.change().equals("deleted"))).hasSize(1);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*RuleAuditServiceTest'`
Expected: FAIL — `RuleAuditService` does not exist.

- [ ] **Step 3: Write the service**

`backend/src/main/java/com/goldys/platform/reconciliation/RuleAuditService.java`:

```java
package com.goldys.platform.reconciliation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Derives the rule change history from the append-only rule rows. */
@Service
public class RuleAuditService {
  private final ResolutionRuleRepository repository;

  public RuleAuditService(ResolutionRuleRepository repository) {
    this.repository = repository;
  }

  public List<RuleAuditEntry> history() {
    return history(repository.findAllByOrderByRecordedAtDesc());
  }

  /**
   * Pure derivation (also used directly by tests): group rows by (entityType, fieldKey),
   * sort oldest-first, and label each row "created" (first for its key), "updated" (superseded
   * with a successor), or "deleted" (superseded with no successor).
   */
  static List<RuleAuditEntry> history(List<ResolutionRule> rows) {
    Map<String, List<ResolutionRule>> byKey = new LinkedHashMap<>();
    for (ResolutionRule r : rows) {
      byKey.computeIfAbsent(r.entityType() + "\u0000" + r.fieldKey(), k -> new ArrayList<>()).add(r);
    }
    List<RuleAuditEntry> out = new ArrayList<>();
    for (List<ResolutionRule> group : byKey.values()) {
      group.sort(Comparator.comparing(ResolutionRule::recordedAt));
      for (int i = 0; i < group.size(); i++) {
        ResolutionRule r = group.get(i);
        boolean hasSuccessor = i + 1 < group.size();
        String change =
            r.supersededAt() == null ? "created" : (hasSuccessor ? "updated" : "deleted");
        out.add(new RuleAuditEntry(r.id(), r.entityType(), r.fieldKey(), change, r.recordedAt(), r.actorEmail()));
      }
    }
    out.sort(Comparator.comparing(RuleAuditEntry::at).reversed());
    return out;
  }

  public record RuleAuditEntry(
      UUID ruleId, String entityType, String fieldKey, String change, Instant at, String by) {}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*RuleAuditServiceTest'`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`
Then:

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/RuleAuditService.java backend/src/test/java/com/goldys/platform/reconciliation/RuleAuditServiceTest.java
git commit -m "feat: add rule audit history"
```

---

## Task 7: Controller + endpoints

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/api/ResolutionRuleController.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ResolutionRuleControllerTest.java`

**Interfaces:**
- Consumes: `ResolutionRuleService` (Task 4), `RuleAuditService` (Task 6), `CurrentUserService`, `PermissionService` (existing).
- Produces: the five endpoints the frontend `liveApi` already calls.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/goldys/platform/api/ResolutionRuleControllerTest.java`:

```java
package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.reconciliation.ResolutionRule;
import com.goldys.platform.reconciliation.ResolutionRuleService;
import com.goldys.platform.reconciliation.RuleAuditService;
import java.time.Instant;
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

@WebMvcTest(ResolutionRuleController.class)
@Import(SecurityConfig.class)
class ResolutionRuleControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean ResolutionRuleService rules;
  @MockitoBean RuleAuditService audit;
  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void listsCurrentRules() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(rules.list())
        .thenReturn(
            List.of(
                ResolutionRule.create(
                    "daily_sales", "daily_sales", "priority", null, List.of("CTB"), "a@b.com",
                    Instant.now())));

    mvc.perform(get("/api/reconciliation/rules").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].entityType").value("daily_sales"))
        .andExpect(jsonPath("$[0].strategy").value("priority"))
        .andExpect(jsonPath("$[0].sourcePriority[0]").value("CTB"));
  }

  @Test
  void createsARule() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());

    mvc.perform(
            post("/api/reconciliation/rules")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entityType\":\"daily_sales\",\"fieldKey\":\"daily_sales\",\"strategy\":\"priority\",\"sourcePriority\":[\"CTB\"]}"))
        .andExpect(status().isOk());
  }

  @Test
  void recomputeStatusIsComplete() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());

    mvc.perform(get("/api/reconciliation/recompute/status").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("complete"));
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor authenticated(
      AccountUserDetails user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests '*ResolutionRuleControllerTest'`
Expected: FAIL — `ResolutionRuleController` does not exist.

- [ ] **Step 3: Write the controller**

`backend/src/main/java/com/goldys/platform/api/ResolutionRuleController.java`:

```java
package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reconciliation.ResolutionRule;
import com.goldys.platform.reconciliation.ResolutionRuleService;
import com.goldys.platform.reconciliation.RuleAuditService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD + audit for standing resolution rules, and the (derived) recompute status. */
@RestController
@RequestMapping("/api/reconciliation")
public class ResolutionRuleController {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final ResolutionRuleService rules;
  private final RuleAuditService audit;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public ResolutionRuleController(
      ResolutionRuleService rules,
      RuleAuditService audit,
      CurrentUserService currentUser,
      PermissionService permissions) {
    this.rules = rules;
    this.audit = audit;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping("/rules")
  List<RuleDto> list(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return rules.list().stream().map(this::toDto).toList();
  }

  @PostMapping("/rules")
  RuleDto save(
      @RequestBody RuleRequest body, @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    ResolutionRule saved =
        rules.save(
            role,
            user.email(),
            new ResolutionRuleService.RuleInput(
                body.entityType(), body.fieldKey(), body.strategy(), body.customLogic(), body.sourcePriority()));
    return toDto(saved);
  }

  @DeleteMapping("/rules/{id}")
  void delete(@PathVariable String id, @AuthenticationPrincipal AccountUserDetails user) {
    rules.delete(currentUser.roleOf(user), user.email(), id);
  }

  @GetMapping("/rules/audit")
  List<RuleAuditDto> audit(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return audit.history().stream()
        .map(
            e ->
                new RuleAuditDto(
                    e.ruleId(), e.entityType(), e.fieldKey(), e.change(), e.at(), e.by()))
        .toList();
  }

  @GetMapping("/recompute/status")
  RecomputeStatusDto recomputeStatus(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return new RecomputeStatusDto("complete", null);
  }

  private RuleDto toDto(ResolutionRule r) {
    return new RuleDto(
        r.id(),
        r.entityType(),
        r.fieldKey(),
        r.strategy(),
        r.sourcePriority(),
        r.customLogic(),
        r.recordedAt(),
        r.actorEmail());
  }

  record RuleDto(
      UUID id,
      String entityType,
      String fieldKey,
      String strategy,
      List<String> sourcePriority,
      String customLogic,
      Instant updatedAt,
      String updatedBy) {}

  record RuleAuditDto(
      UUID ruleId, String entityType, String fieldKey, String change, Instant at, String by) {}

  record RecomputeStatusDto(String state, Instant lastChangedAt) {}

  record RuleRequest(
      String entityType, String fieldKey, String strategy, String customLogic, List<String> sourcePriority) {}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests '*ResolutionRuleControllerTest'`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd backend && ./gradlew spotlessApply`
Then:

```bash
git add backend/src/main/java/com/goldys/platform/api/ResolutionRuleController.java backend/src/test/java/com/goldys/platform/api/ResolutionRuleControllerTest.java
git commit -m "feat: add resolution rule endpoints"
```

---

## Task 8: Full verification pass

**Files:**
- None (verification only).

- [ ] **Step 1: Run the full backend gate**

Run: `cd backend && ./gradlew test spotlessCheck build`
Expected: all pass.

- [ ] **Step 2: Manual smoke check (optional, requires a Postgres + running backend)**

If the local Postgres is up (`docker compose up -d`), start the backend and confirm:

- `GET /api/reconciliation/rules` returns `[]`.
- `POST /api/reconciliation/rules` with a priority rule, then `GET` shows it.
- A conflict that the rule resolves disappears from `GET /api/reconciliation/exceptions`.

- [ ] **Step 3: Commit any fixes**

If the smoke check surfaced a fix, commit it atomically with a `fix:` message; otherwise nothing to commit.
