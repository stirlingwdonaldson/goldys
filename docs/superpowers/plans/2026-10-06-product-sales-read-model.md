# Product-Sales Exception Read Model — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Project product-sales reconciliation exceptions into the existing `reconciliation_exception` table (`entity_type='product_sales'`), maintain them with a projector, and migrate the dashboard product-conflict count and the product-exceptions list off on-demand recomputation.

**Architecture:** Same synchronous same-transaction projection pattern as the daily-sales slice: `CanonicalProductSalesService.record()` publishes a `ProductSalesRecorded` event; a listener runs `ProductSalesProjector.recompute(product, date)` in the same transaction; override/rule services trigger the projector directly. Exceptions-only — no `resolved_product_sales` values table.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL 16 (Testcontainers), JUnit 5 + AssertJ + Mockito, Spotless (googleJavaFormat 1.28.0).

**Spec:** `docs/superpowers/specs/2026-10-06-product-sales-read-model-design.md`

## Global Constraints

- Java 25; Spring Boot 3.5; PostgreSQL 16; schema owned by Flyway (`spring.jpa.hibernate.ddl-auto: validate`).
- Spotless must pass (`./gradlew spotlessCheck`); googleJavaFormat 1.28.0.
- Backend tests: `./gradlew test`; Testcontainers integration tests use `@SpringBootTest` + `@Import(PostgresContainerConfiguration.class)`.
- Atomic Conventional Commits (`feat:`, `test:`, `fix:`, `docs:`); never commit directly to `main`; work on branch `feature/product-sales-read-model`.
- Canonical rows, overrides, and rules are append-only and authoritative; the exception projection is disposable.
- Preserve the resolution semantics exactly: override → agreement (**zero tolerance** on `quantity_sold` AND `amount`) → rule (`product_sales/<key>` then `product_sales/*`, evaluated on `quantity_sold`) → unresolved (`conflict` = ≥2 disagreeing, `missing` = <2 sources).
- The projector injects **repositories**, not the trigger services (`ResolutionRuleService`, `ProductSalesOverrideService`), to avoid a Spring bean cycle.

## Review Focus

These are the failure modes the spec implies but no happy-path test catches. Each is pinned by a test in the owning task.

1. **A single-source product/day must surface as `missing` (needs a decision), never auto-resolve.** — pinned by `ProductSalesResolverTest` (Task 2) and `ProductSalesProjectorIntegrationTest` (Task 3).
2. **The same product conflicting on two different dates must produce two distinct exceptions** (the whole point of the V16 PK change). — pinned by `ProductSalesProjectorIntegrationTest` (Task 3).
3. **A rule save/delete must recompute every product pair, not just changed ones.** — pinned by `ProductSalesTriggerIntegrationTest` (Task 4).
4. **`recomputeAll()` must be idempotent** (running it twice reproduces identical rows). — pinned by `ProductSalesProjectorIntegrationTest` (Task 3).
5. **`detected_at` must survive recomputes while an exception stays open, and reset when it clears then reopens.** — pinned by `ProductSalesProjectorIntegrationTest` (Task 3).

---

### Task 1: Exception primary-key migration + `@IdClass` update

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__product_sales_exception_key.sql`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionRow.java` (add `tradingDate` to the `@IdClass`)
- Test: existing `DatabaseMigrationTest`, `ProjectionRepositoryIntegrationTest`, `DailySalesProjectorIntegrationTest` (they exercise the entity and migration)

**Interfaces:**
- Produces: a `reconciliation_exception` primary key of `(entity_type, entity_key, trading_date, field_key)`; `ReconciliationExceptionRow.Id` now carries `tradingDate`.

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V16__product_sales_exception_key.sql`:

```sql
-- A product (entity_key = product name) can conflict on multiple trading dates, so the exception
-- key must include trading_date. Daily-sales rows already satisfy it (entity_key == the date).
ALTER TABLE reconciliation_exception DROP CONSTRAINT reconciliation_exception_pkey;
ALTER TABLE reconciliation_exception ADD PRIMARY KEY (entity_type, entity_key, trading_date, field_key);
```

- [ ] **Step 2: Update the `@IdClass` to match the new key**

In `ReconciliationExceptionRow.java`:

1. Add `@jakarta.persistence.Id` to the `tradingDate` field (so it is part of the composite key):

```java
  @jakarta.persistence.Id
  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;
```

2. Update the nested `Id` class to carry `tradingDate` and include it in `equals`/`hashCode`:

```java
  static class Id implements Serializable {
    private String entityType;
    private String entityKey;
    private LocalDate tradingDate;
    private String fieldKey;

    public Id() {}

    Id(String entityType, String entityKey, LocalDate tradingDate, String fieldKey) {
      this.entityType = entityType;
      this.entityKey = entityKey;
      this.tradingDate = tradingDate;
      this.fieldKey = fieldKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Id other)) return false;
      return Objects.equals(entityType, other.entityType)
          && Objects.equals(entityKey, other.entityKey)
          && Objects.equals(tradingDate, other.tradingDate)
          && Objects.equals(fieldKey, other.fieldKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(entityType, entityKey, tradingDate, fieldKey);
    }
  }
```

- [ ] **Step 3: Run the existing tests to confirm nothing broke**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.DatabaseMigrationTest" --tests "com.goldys.platform.reconciliation.ProjectionRepositoryIntegrationTest" --tests "com.goldys.platform.reconciliation.DailySalesProjectorIntegrationTest" --console=plain`
Expected: PASS (V16 applies; the entity round-trips with the new 4-column key; the daily projector still works because daily rows set `tradingDate`).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V16__product_sales_exception_key.sql \
        backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionRow.java
git commit -m "feat: add trading_date to the reconciliation exception key"
```

---

### Task 2: Pure resolver + canonical event

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesResolver.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductSalesRecorded.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesResolverTest.java`

**Interfaces:**
- Produces: `ProductSalesResolver.resolve(List<ProductSourceTotal>, Optional<String> overrideSource, Optional<ResolutionRule> rule)` → `Optional<String>` (`"conflict"`/`"missing"`, empty = resolved/no-exception); `ProductSalesResolver.classify(List<ProductSourceTotal>)`; record `canonical.ProductSalesRecorded(String productNameKey, LocalDate tradingDate)`.

- [ ] **Step 1: Write the failing unit test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesResolverTest.java`:

```java
package com.goldys.platform.reconciliation;

import static com.goldys.platform.reconciliation.ProductSalesResolver.classify;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductSalesResolverTest {

  @Test
  void classifiesAgreedConflictAndMissingWithZeroTolerance() {
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"), st("CTB", "10", "100.00"))))
        .isEqualTo("agreed");
    // quantity differs by 0.0001 — zero tolerance, not "close enough"
    assertThat(classify(List.of(st("LIGHTSPEED", "10.0000", "100.00"), st("CTB", "10.0001", "100.00"))))
        .isEqualTo("conflict");
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00")))).isEqualTo("missing");
  }

  @Test
  void overrideResolvesThePair() {
    assertThat(
            ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88"), st("CTB", "127", "322.46")),
                Optional.of("CTB"),
                Optional.empty()))
        .isEmpty();
  }

  @Test
  void agreementResolvesThePair() {
    assertThat(
            ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "1232", "17340.98"), st("CTB", "1232", "17340.98")),
                Optional.empty(),
                Optional.empty()))
        .isEmpty();
  }

  @Test
  void conflictWithNoRuleIsAnException() {
    assertThat(
            ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88"), st("CTB", "127", "322.46")),
                Optional.empty(),
                Optional.empty()))
        .contains("conflict");
  }

  @Test
  void singleSourceIsMissing() {
    assertThat(
            ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88")), Optional.empty(), Optional.empty()))
        .contains("missing");
  }

  @Test
  void emptySourcesAreNotAnException() {
    assertThat(ProductSalesResolver.resolve(List.of(), Optional.empty(), Optional.empty())).isEmpty();
  }

  @Test
  void aRuleResolvesThePair() {
    ResolutionRule rule =
        ResolutionRule.create(
            "product_sales", "garlic aioli", "priority", null, List.of("CTB"), "a@b.com",
            Instant.EPOCH);

    assertThat(
            ProductSalesResolver.resolve(
                List.of(st("LIGHTSPEED", "150", "380.88"), st("CTB", "127", "322.46")),
                Optional.empty(),
                Optional.of(rule)))
        .isEmpty();
  }

  private static ProductSourceTotal st(String source, String qty, String amount) {
    return new ProductSourceTotal(source, new BigDecimal(qty), new BigDecimal(amount), null);
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProductSalesResolverTest" --console=plain`
Expected: compilation FAIL — `ProductSalesResolver` does not exist.

- [ ] **Step 3: Write the resolver and the event**

Create `ProductSalesResolver.java`:

```java
package com.goldys.platform.reconciliation;

import java.util.List;
import java.util.Optional;

/** Pure product-sales resolution: override → agreement (zero tolerance on quantity AND amount) →
 * rule (evaluated on quantity_sold) → unresolved. Returns the exception status, or empty when the
 * pair is resolved or has no data. */
final class ProductSalesResolver {
  private ProductSalesResolver() {}

  static Optional<String> resolve(
      List<ProductSourceTotal> sources,
      Optional<String> overrideSource,
      Optional<ResolutionRule> rule) {
    if (overrideSource.isPresent()) {
      return Optional.empty();
    }
    if (sources.isEmpty()) {
      return Optional.empty();
    }
    String status = classify(sources);
    if ("agreed".equals(status)) {
      return Optional.empty();
    }
    if (rule.isPresent() && RuleEvaluator.resolve(rule.get(), toMetrics(sources)).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(status);
  }

  static String classify(List<ProductSourceTotal> sources) {
    if (sources.size() < 2) {
      return "missing";
    }
    ProductSourceTotal first = sources.get(0);
    boolean agree =
        sources.stream()
            .allMatch(
                s ->
                    first.quantitySold().compareTo(s.quantitySold()) == 0
                        && first.amount().compareTo(s.amount()) == 0);
    return agree ? "agreed" : "conflict";
  }

  private static List<SourceMetric> toMetrics(List<ProductSourceTotal> sources) {
    return sources.stream()
        .map(s -> new SourceMetric(s.sourceSystem(), s.quantitySold(), s.recordedAt()))
        .toList();
  }
}
```

Create `ProductSalesRecorded.java`:

```java
package com.goldys.platform.canonical;

import java.time.LocalDate;

/** Published after a canonical product-sales fact is recorded, for the product projection. */
public record ProductSalesRecorded(String productNameKey, LocalDate tradingDate) {}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProductSalesResolverTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesResolver.java \
        backend/src/main/java/com/goldys/platform/canonical/ProductSalesRecorded.java \
        backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesResolverTest.java
git commit -m "feat: extract pure product-sales resolver and canonical change event"
```

---

### Task 3: Projector + bulk repository methods

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesRepository.java` (add `findCurrentByDateAndProduct`, `findCurrentByDates`)
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesQuery.java` (add `currentProductSalesForDateAndProduct`, `currentProductSalesForDates`)
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesOverrideRepository.java` (add `findAllCurrent`)
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleRepository.java` (add `findByEntityTypeAndSupersededAtIsNull`)
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionRowRepository.java` (add `findByEntityTypeAndEntityKeyAndTradingDate`, `deleteByEntityType`)
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesProjector.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesProjectorIntegrationTest.java`

**Interfaces:**
- Consumes: `ReconciliationExceptionRow` (Task 1), `ProductSalesResolver` (Task 2), `CanonicalProductSalesQuery` + `ProductSalesView`, `ProductSalesOverrideRepository`, `ResolutionRuleRepository`.
- Produces: `ProductSalesProjector.recompute(String, LocalDate)` and `.recomputeAll()`.

- [ ] **Step 1: Add the bulk repository methods**

In `CanonicalProductSalesRepository.java`, add (imports: `java.util.Collection`):

```java
  @Query(
      "select s from CanonicalProductSales s where s.tradingDate = :date "
          + "and s.productNameKey = :key and s.supersededAt is null")
  List<CanonicalProductSales> findCurrentByDateAndProduct(LocalDate date, String key);

  @Query("select s from CanonicalProductSales s where s.tradingDate in :dates and s.supersededAt is null")
  List<CanonicalProductSales> findCurrentByDates(Collection<LocalDate> dates);
```

In `CanonicalProductSalesQuery.java`, add (import `java.util.Collection`):

```java
  public List<ProductSalesView> currentProductSalesForDateAndProduct(LocalDate date, String key) {
    return repository.findCurrentByDateAndProduct(date, key).stream().map(this::toView).toList();
  }

  public List<ProductSalesView> currentProductSalesForDates(Collection<LocalDate> dates) {
    return repository.findCurrentByDates(dates).stream().map(this::toView).toList();
  }
```

In `ProductSalesOverrideRepository.java`, add:

```java
  @Query("select o from ProductSalesOverride o where o.supersededAt is null")
  List<ProductSalesOverride> findAllCurrent();
```

(import `java.util.List`.)

In `ResolutionRuleRepository.java`, add:

```java
  @Query("select r from ResolutionRule r where r.entityType = :entityType and r.supersededAt is null")
  List<ResolutionRule> findByEntityTypeAndSupersededAtIsNull(String entityType);
```

In `ReconciliationExceptionRowRepository.java`, add:

```java
  List<ReconciliationExceptionRow> findByEntityTypeAndEntityKeyAndTradingDate(
      String entityType, String entityKey, LocalDate tradingDate);

  void deleteByEntityType(String entityType);
```

- [ ] **Step 2: Write the failing integration test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesProjectorIntegrationTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.canonical.ProductSalesInput;
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

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProductSalesProjectorIntegrationTest {

  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);
  private static final LocalDate SEP_15 = LocalDate.of(2026, 9, 15);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalProductSalesIngest ingest;
  @Autowired ProductSalesProjector projector;
  @Autowired ReconciliationExceptionRowRepository exceptions;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table reconciliation_exception, canonical_product_sales, product_sales_override, resolution_rule");
  }

  @Test
  void conflictingPairProducesAnException() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    projector.recompute("garlic aioli", SEP_14);

    var rows = exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).entityKey()).isEqualTo("garlic aioli");
    assertThat(rows.get(0).status()).isEqualTo("conflict");
  }

  @Test
  void sameProductOnTwoDatesProducesTwoExceptions() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    ingest.record(input("LIGHTSPEED", SEP_15, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_15, "garlic aioli", "127", "322.46"));
    projector.recompute("garlic aioli", SEP_14);
    projector.recompute("garlic aioli", SEP_15);

    assertThat(exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales")).hasSize(2);
  }

  @Test
  void agreeingPairProducesNoException() {
    ingest.record(input("LIGHTSPEED", SEP_14, "chips", "10", "50.00"));
    ingest.record(input("CTB", SEP_14, "chips", "10", "50.00"));
    projector.recompute("chips", SEP_14);

    assertThat(exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales")).isEmpty();
  }

  @Test
  void recomputeAllIsIdempotent() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    projector.recomputeAll();
    var first = exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales");

    projector.recomputeAll();
    var second = exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales");

    assertThat(second).hasSize(first.size());
    assertThat(second).usingRecursiveFieldByFieldElementComparatorIgnoringFields("detectedAt")
        .containsExactlyInAnyOrderElementsOf(first);
  }

  @Test
  void detectedAtSurvivesRecomputeWhileOpen() throws Exception {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    projector.recompute("garlic aioli", SEP_14);
    var first = exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales").get(0).detectedAt();

    Thread.sleep(10);
    projector.recompute("garlic aioli", SEP_14);
    var second = exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales").get(0).detectedAt();

    assertThat(second).isEqualTo(first);
  }

  private ProductSalesInput input(String source, LocalDate date, String key, String qty, String amount) {
    return new ProductSalesInput(source, date, key, bd(qty), bd(amount), rawRecord());
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
            + "values (?, ?, 'CTB', 'API', 'application/json', ?, ?, ?, 'test', now())",
        recordId, runId, new byte[] {1}, "0".repeat(64), 1);
    return recordId;
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
```

- [ ] **Step 3: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProductSalesProjectorIntegrationTest" --console=plain`
Expected: compilation FAIL — `ProductSalesProjector` does not exist.

- [ ] **Step 4: Write the projector**

Create `ProductSalesProjector.java`:

```java
package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintains the product_sales exception projection from canonical rows, overrides, and rules.
 * Injects repositories (not the trigger services) to avoid a Spring bean cycle. */
@Service
public class ProductSalesProjector {
  private static final String ENTITY_TYPE = "product_sales";
  private static final String FIELD_KEY = "product_sales";
  private static final String SEP = "\u0000";
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalProductSalesQuery productSales;
  private final ProductSalesOverrideRepository overrides;
  private final ResolutionRuleRepository rules;
  private final ReconciliationExceptionRowRepository exceptions;

  public ProductSalesProjector(
      CanonicalProductSalesQuery productSales,
      ProductSalesOverrideRepository overrides,
      ResolutionRuleRepository rules,
      ReconciliationExceptionRowRepository exceptions) {
    this.productSales = productSales;
    this.overrides = overrides;
    this.rules = rules;
    this.exceptions = exceptions;
  }

  @Transactional
  public void recompute(String productNameKey, LocalDate date) {
    List<ProductSourceTotal> sources =
        productSales.currentProductSalesForDateAndProduct(date, productNameKey).stream()
            .map(v -> new ProductSourceTotal(v.sourceSystem(), v.quantitySold(), v.amount(), v.recordedAt()))
            .toList();
    Optional<String> overrideSource =
        overrides.findCurrent(productNameKey, date).map(ProductSalesOverride::authoritativeSource);
    Optional<ResolutionRule> rule = rules.findCurrent(ENTITY_TYPE, productNameKey);
    if (rule.isEmpty()) {
      rule = rules.findCurrent(ENTITY_TYPE, "*");
    }
    reconcile(productNameKey, date, ProductSalesResolver.resolve(sources, overrideSource, rule));
  }

  @Transactional
  public void recomputeAll() {
    exceptions.deleteByEntityType(ENTITY_TYPE);

    Map<String, List<ProductSourceTotal>> byPair = new HashMap<>();
    for (ProductSalesView v : productSales.currentProductSales()) {
      byPair
          .computeIfAbsent(v.tradingDate() + SEP + v.productNameKey(), k -> new ArrayList<>())
          .add(new ProductSourceTotal(v.sourceSystem(), v.quantitySold(), v.amount(), v.recordedAt()));
    }
    Map<String, String> overrideByPair = new HashMap<>();
    for (ProductSalesOverride o : overrides.findAllCurrent()) {
      overrideByPair.put(o.tradingDate() + SEP + o.productNameKey(), o.authoritativeSource());
    }
    Map<String, ResolutionRule> ruleByFieldKey = new HashMap<>();
    for (ResolutionRule r : rules.findByEntityTypeAndSupersededAtIsNull(ENTITY_TYPE)) {
      ruleByFieldKey.putIfAbsent(r.fieldKey(), r);
    }
    ResolutionRule catchAll = ruleByFieldKey.get("*");

    for (Map.Entry<String, List<ProductSourceTotal>> e : byPair.entrySet()) {
      String[] parts = e.getKey().split(SEP);
      LocalDate date = LocalDate.parse(parts[0]);
      String product = parts[1];
      Optional<ResolutionRule> rule =
          Optional.ofNullable(ruleByFieldKey.get(product))
              .or(() -> Optional.ofNullable(catchAll));
      ProductSalesResolver.resolve(
              e.getValue(), Optional.ofNullable(overrideByPair.get(e.getKey())), rule)
          .ifPresent(
              status ->
                  exceptions.save(
                      new ReconciliationExceptionRow(
                          ENTITY_TYPE, product, FIELD_KEY, date, status, CLOCK.instant())));
    }
  }

  private void reconcile(String product, LocalDate date, Optional<String> status) {
    List<ReconciliationExceptionRow> existing =
        exceptions.findByEntityTypeAndEntityKeyAndTradingDate(ENTITY_TYPE, product, date);
    if (status.isPresent()) {
      if (existing.isEmpty()) {
        exceptions.save(
            new ReconciliationExceptionRow(
                ENTITY_TYPE, product, FIELD_KEY, date, status.get(), CLOCK.instant()));
      } else if (!status.get().equals(existing.get(0).status())) {
        existing.get(0).changeStatus(status.get());
      }
    } else if (!existing.isEmpty()) {
      exceptions.deleteAll(existing);
    }
  }
}
```

- [ ] **Step 5: Run the test to confirm it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProductSalesProjectorIntegrationTest" --console=plain`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesRepository.java \
        backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesQuery.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesOverrideRepository.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleRepository.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionRowRepository.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesProjector.java \
        backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesProjectorIntegrationTest.java
git commit -m "feat: add product-sales exception projector"
```

---

### Task 4: Trigger wiring

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesService.java` (publish event)
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesProjectionListener.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesOverrideService.java` (call projector)
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleService.java` (call product projector on product_sales changes)
- Modify: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesOverrideServiceTest.java` (mock projector)
- Modify: `backend/src/test/java/com/goldys/platform/reconciliation/ResolutionRuleServiceTest.java` (mock product projector)
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesTriggerIntegrationTest.java`

**Interfaces:**
- Consumes: `ProductSalesProjector` (Task 3), `canonical.ProductSalesRecorded` (Task 2).
- Produces: `ProductSalesProjectionListener`; modified `CanonicalProductSalesService`, `ProductSalesOverrideService`, `ResolutionRuleService`.

- [ ] **Step 1: Publish the event from canonical recording**

In `CanonicalProductSalesService.java`: add `ApplicationEventPublisher publisher` field + constructor param; change `record`:

```java
  @Transactional
  CanonicalProductSales record(ProductSalesInput input) {
    CanonicalProductSales saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new ProductSalesRecorded(input.productNameKey(), input.tradingDate()));
    return saved;
  }
```

(import `org.springframework.context.ApplicationEventPublisher`.)

- [ ] **Step 2: Write the listener**

Create `ProductSalesProjectionListener.java`:

```java
package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.ProductSalesRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Projects a product/day's exception whenever a canonical product-sales fact is recorded. */
@Component
public class ProductSalesProjectionListener {
  private final ProductSalesProjector projector;

  public ProductSalesProjectionListener(ProductSalesProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(ProductSalesRecorded event) {
    projector.recompute(event.productNameKey(), event.tradingDate());
  }
}
```

- [ ] **Step 3: Wire the override and rule services**

In `ProductSalesOverrideService.java`: add a `ProductSalesProjector projector` field + constructor param; at the end of `save(...)`, capture the saved row, call `projector.recompute(productNameKey, date)`, and return it:

```java
    ProductSalesOverride saved =
        repository.save(ProductSalesOverride.create(date, productNameKey, source, reason, actorEmail, now));
    projector.recompute(productNameKey, date);
    return saved;
```

In `ResolutionRuleService.java`: add a `ProductSalesProjector productProjector` field + constructor param (alongside the existing `DailySalesProjector projector`). In `save(...)` and `delete(...)`, alongside the existing `daily_sales` branch, add:

```java
    if ("product_sales".equals(input.entityType())) {
      productProjector.recomputeAll();
    }
```

and in `delete(...)`:

```java
    if ("product_sales".equals(current.entityType())) {
      productProjector.recomputeAll();
    }
```

- [ ] **Step 4: Update the two existing unit tests**

In `ProductSalesOverrideServiceTest.java`: every `new ProductSalesOverrideService(...)` gains a `mock(ProductSalesProjector.class)` argument (4 call sites — add it as the last constructor arg).

In `ResolutionRuleServiceTest.java`: every `new ResolutionRuleService(...)` gains a `mock(DailySalesProjector.class)` and a `mock(ProductSalesProjector.class)` argument. Note the constructor now takes both projectors (order: repository, permissions, dailyProjector, productProjector — keep the exact order you chose in Step 3).

- [ ] **Step 5: Write the failing trigger integration test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesTriggerIntegrationTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.canonical.ProductSalesInput;
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
class ProductSalesTriggerIntegrationTest {

  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalProductSalesIngest ingest;
  @Autowired ProductSalesOverrideService overrideService;
  @Autowired ResolutionRuleService ruleService;
  @Autowired ReconciliationExceptionRowRepository exceptions;
  @MockitoBean PermissionService permissions; // no-op mock so saves succeed without a permission fixture

  @BeforeEach
  void clean() {
    jdbc.update("truncate table reconciliation_exception, canonical_product_sales, product_sales_override, resolution_rule");
  }

  @Test
  void recordingCanonicalProjectsThePairAutomatically() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));

    assertThat(exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales")).hasSize(1);
  }

  @Test
  void overrideSaveProjectsThePair() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));

    overrideService.save(OWNER, "a@b.com", SEP_14, "garlic aioli", "LIGHTSPEED", "typo");

    assertThat(exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales")).isEmpty();
  }

  @Test
  void productRuleChangeRecomputesAllPairs() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));

    ruleService.save(
        OWNER,
        "a@b.com",
        new ResolutionRuleService.RuleInput(
            "product_sales", "garlic aioli", "priority", null, List.of("CTB")));

    assertThat(exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales")).isEmpty();
  }

  private ProductSalesInput input(String source, LocalDate date, String key, String qty, String amount) {
    return new ProductSalesInput(source, date, key, bd(qty), bd(amount), rawRecord());
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
            + "values (?, ?, 'CTB', 'API', 'application/json', ?, ?, ?, 'test', now())",
        recordId, runId, new byte[] {1}, "0".repeat(64), 1);
    return recordId;
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
```

- [ ] **Step 6: Run the test to confirm it fails then passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProductSalesTriggerIntegrationTest" --console=plain`
Expected: FAIL before Steps 1–3 (no listener/triggers); PASS after.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesService.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesProjectionListener.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesOverrideService.java \
        backend/src/main/java/com/goldys/platform/reconciliation/ResolutionRuleService.java \
        backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesOverrideServiceTest.java \
        backend/src/test/java/com/goldys/platform/reconciliation/ResolutionRuleServiceTest.java \
        backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesTriggerIntegrationTest.java
git commit -m "feat: trigger product-sales projection from canonical, override, and rule writes"
```

---

### Task 5: Read query + consumer migration + deletion

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ReconciliationExceptionRowRepository.java` (add `countByEntityType`)
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesExceptionQuery.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/DashboardController.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/ReconciliationController.java`
- Delete: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesReconciliationService.java`
- Delete: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesConflict.java`
- Delete: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesReconciliationTest.java`
- Modify: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesIntegrationTest.java` (migrate to projector/query)
- Modify: `backend/src/test/java/com/goldys/platform/api/DashboardControllerTest.java`
- Modify: `backend/src/test/java/com/goldys/platform/api/ReconciliationControllerTest.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesExceptionQueryIntegrationTest.java`

**Interfaces:**
- Consumes: `ReconciliationExceptionRowRepository` (Task 1/3), `CanonicalProductSalesQuery.currentProductSalesForDates` (Task 3).
- Produces: `ProductSalesExceptionQuery.listAll()` → `List<ProductException>`; `countOpen()` → `long`; `ProductException(LocalDate tradingDate, String productNameKey, String status, List<ProductSourceTotal> sources)`.

- [ ] **Step 1: Write the failing query integration test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesExceptionQueryIntegrationTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
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
class ProductSalesExceptionQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ReconciliationExceptionRowRepository repository;
  @Autowired ProductSalesExceptionQuery query;

  @BeforeEach
  void seed() {
    jdbc.update("truncate table reconciliation_exception");
    repository.save(
        new ReconciliationExceptionRow(
            "product_sales", "garlic aioli", "product_sales", LocalDate.of(2026, 9, 14), "conflict",
            Instant.EPOCH));
    repository.save(
        new ReconciliationExceptionRow(
            "product_sales", "chips", "product_sales", LocalDate.of(2026, 9, 15), "missing",
            Instant.EPOCH));
  }

  @Test
  void listAllReturnsNewestFirst() {
    var rows = query.listAll();
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).tradingDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(rows.get(0).productNameKey()).isEqualTo("chips");
  }

  @Test
  void countOpenCountsOnlyProductExceptions() {
    assertThat(query.countOpen()).isEqualTo(2);
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProductSalesExceptionQueryIntegrationTest" --console=plain`
Expected: compilation FAIL — `ProductSalesExceptionQuery` does not exist.

- [ ] **Step 3: Write the query facade and the count method**

Add to `ReconciliationExceptionRowRepository.java`:

```java
  long countByEntityType(String entityType);
```

Create `ProductSalesExceptionQuery.java`:

```java
package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Read-only facade over the product_sales exception projection, joined back to canonical for the
 * per-source values shown in the exceptions UI. */
@Service
public class ProductSalesExceptionQuery {
  private static final String ENTITY_TYPE = "product_sales";
  private static final String SEP = "\u0000";

  private final ReconciliationExceptionRowRepository exceptions;
  private final CanonicalProductSalesQuery productSales;

  public ProductSalesExceptionQuery(
      ReconciliationExceptionRowRepository exceptions, CanonicalProductSalesQuery productSales) {
    this.exceptions = exceptions;
    this.productSales = productSales;
  }

  public List<ProductException> listAll() {
    List<ReconciliationExceptionRow> rows =
        exceptions.findByEntityTypeOrderByTradingDateDesc(ENTITY_TYPE);
    if (rows.isEmpty()) {
      return List.of();
    }
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (ReconciliationExceptionRow row : rows) {
      dates.add(row.tradingDate());
    }
    Map<String, List<ProductSourceTotal>> byPair = new HashMap<>();
    for (ProductSalesView v : productSales.currentProductSalesForDates(dates)) {
      byPair
          .computeIfAbsent(v.tradingDate() + SEP + v.productNameKey(), k -> new ArrayList<>())
          .add(new ProductSourceTotal(v.sourceSystem(), v.quantitySold(), v.amount(), v.recordedAt()));
    }
    List<ProductException> out = new ArrayList<>();
    for (ReconciliationExceptionRow row : rows) {
      out.add(
          new ProductException(
              row.tradingDate(),
              row.entityKey(),
              row.status(),
              byPair.getOrDefault(row.tradingDate() + SEP + row.entityKey(), List.of())));
    }
    return out;
  }

  public long countOpen() {
    return exceptions.countByEntityType(ENTITY_TYPE);
  }

  public record ProductException(
      LocalDate tradingDate,
      String productNameKey,
      String status,
      List<ProductSourceTotal> sources) {}
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProductSalesExceptionQueryIntegrationTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Migrate the consumers and delete the on-demand service**

In `DashboardController.java`: replace `ProductSalesReconciliationService productSales` with `ProductSalesExceptionQuery productSalesExceptions`, and change the conflict count:

```java
    int openConflicts =
        (int) (resolvedDailySales.countOpenConflicts() + productSalesExceptions.countOpen());
```

In `ReconciliationController.java`: replace `ProductSalesReconciliationService productSales` with `ProductSalesExceptionQuery productSalesExceptions`, and rewrite `productExceptions()`:

```java
  @GetMapping("/products/exceptions")
  List<ProductExceptionDto> productExceptions(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return productSalesExceptions.listAll().stream().map(this::toProductException).toList();
  }

  private ProductExceptionDto toProductException(ProductSalesExceptionQuery.ProductException e) {
    List<SourceValueDto> sources =
        e.sources().stream()
            .map(
                s ->
                    new SourceValueDto(
                        s.sourceSystem(),
                        s.quantitySold().toPlainString() + " × $" + s.amount().toPlainString()))
            .toList();
    return new ProductExceptionDto(
        e.tradingDate() + ":" + e.productNameKey(),
        e.productNameKey(),
        e.productNameKey(),
        e.productNameKey(),
        sources,
        e.status());
  }
```

Delete `ProductSalesReconciliationService.java`, `ProductSalesConflict.java`, and `ProductSalesReconciliationTest.java`.

Verify: `cd backend && grep -rn "ProductSalesReconciliationService\|ProductSalesConflict" src/` → no matches.

- [ ] **Step 6: Migrate `ProductSalesIntegrationTest`**

`ProductSalesIntegrationTest` currently autowires `ProductSalesReconciliationService` and calls `reconciliation.conflicts()`. Replace it with `ProductSalesExceptionQuery` + `ProductSalesProjector`:

- Replace `@Autowired ProductSalesReconciliationService reconciliation;` with `@Autowired ProductSalesExceptionQuery exceptions;`.
- In `perDayScopingAndOverrideResolution`, replace `reconciliation.conflicts()` with `exceptions.listAll()`, asserting on `ProductException.tradingDate()/productNameKey()` instead of `ProductSalesConflict`. Add `truncate table reconciliation_exception` to `clean()`.
- After the override save, assert `exceptions.listAll()` is empty.

- [ ] **Step 7: Update the controller unit tests**

`DashboardControllerTest.java`: replace `@MockitoBean ProductSalesReconciliationService productSales;` with `@MockitoBean ProductSalesExceptionQuery productSalesExceptions;`; replace `when(productSales.conflicts()).thenReturn(List.of())` with `when(productSalesExceptions.countOpen()).thenReturn(0L);`.

`ReconciliationControllerTest.java`: replace `@MockitoBean ProductSalesReconciliationService productSales;` with `@MockitoBean ProductSalesExceptionQuery productSalesExceptions;`; in `productExceptionsReturnsTheConflict`, replace `when(productSales.conflicts())...` with `when(productSalesExceptions.listAll()).thenReturn(List.of(new ProductSalesExceptionQuery.ProductException(LocalDate.of(2026,9,14), "garlic aioli", "conflict", List.of(new ProductSourceTotal("LIGHTSPEED", new BigDecimal("150"), new BigDecimal("380.88"), null), new ProductSourceTotal("CTB", new BigDecimal("127"), new BigDecimal("322.46"), null)))))` and update the jsonPath assertions to match the `"150 × $380.88"` source format.

- [ ] **Step 8: Run the full backend suite**

Run: `cd backend && ./gradlew test --console=plain`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -A backend/src/main/java/com/goldys/platform/reconciliation \
        backend/src/main/java/com/goldys/platform/api \
        backend/src/test/java/com/goldys/platform/reconciliation \
        backend/src/test/java/com/goldys/platform/api
git commit -m "refactor: migrate product-sales reads to the exception projection and remove on-demand reconciliation"
```

---

### Task 6: Startup backfill seeder

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/StartupProjectionSeeder.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesSeederIntegrationTest.java`

**Interfaces:**
- Consumes: `ProductSalesProjector.recomputeAll()` (Task 3), `DailySalesProjector.recomputeAll()`.

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesSeederIntegrationTest.java`:

```java
package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
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
class ProductSalesSeederIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ReconciliationExceptionRowRepository repository;
  @Autowired StartupProjectionSeeder seeder;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table reconciliation_exception, canonical_product_sales, product_sales_override, resolution_rule");
  }

  @Test
  void seedsProductExceptionsFromExistingCanonicalData() {
    seedCanonical("LIGHTSPEED", LocalDate.of(2026, 9, 14), "garlic aioli", "150", "380.88");
    seedCanonical("CTB", LocalDate.of(2026, 9, 14), "garlic aioli", "127", "322.46");

    seeder.run(mockApplicationArguments());

    assertThat(repository.findByEntityTypeOrderByTradingDateDesc("product_sales")).hasSize(1);
  }

  private void seedCanonical(String source, LocalDate date, String key, String qty, String amount) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    UUID entityId = UUID.randomUUID();
    UUID logicalId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, ?, 'test', 'SUCCESS', now(), 1, 1)",
        runId, source);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, ?, 'API', 'application/json', ?, ?, ?, 'test', now())",
        recordId, runId, source, new byte[] {1}, "0".repeat(64), 1);
    jdbc.update(
        "insert into canonical_product_sales (id, logical_entity_id, trading_date, product_name_key, source_system, source_record_ref, raw_record_id, quantity_sold, amount, valid_from, recorded_at) "
            + "values (?, ?, ?, ?, ?, 'ref', ?, ?, ?, now(), now())",
        entityId, logicalId, date, key, source, recordId, new BigDecimal(qty), new BigDecimal(amount));
  }

  private static ApplicationArguments mockApplicationArguments() {
    return org.mockito.Mockito.mock(ApplicationArguments.class);
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProductSalesSeederIntegrationTest" --console=plain`
Expected: FAIL (the seeder does not yet recompute product exceptions).

- [ ] **Step 3: Extend the seeder**

In `StartupProjectionSeeder.java`, add a `ProductSalesProjector productProjector` dependency and call it:

```java
  private final ResolvedDailySalesRepository resolved;
  private final DailySalesProjector projector;
  private final ProductSalesProjector productProjector;

  public StartupProjectionSeeder(
      ResolvedDailySalesRepository resolved,
      DailySalesProjector projector,
      ProductSalesProjector productProjector) {
    this.resolved = resolved;
    this.projector = projector;
    this.productProjector = productProjector;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (resolved.count() == 0) {
      projector.recomputeAll();
    }
    // Product exceptions are backfilled on every boot: "no conflicts" and "never seeded" are
    // indistinguishable without a checkpoint table, and the rebuild is cheap at pub scale.
    productProjector.recomputeAll();
  }
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd backend && ./gradlew test --tests "com.goldys.platform.reconciliation.ProductSalesSeederIntegrationTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Run the full suite + Spotless, then commit**

Run: `cd backend && ./gradlew test spotlessCheck --console=plain`
Expected: PASS.

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation/StartupProjectionSeeder.java \
        backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesSeederIntegrationTest.java
git commit -m "feat: backfill product-sales exceptions on startup"
```

---

## Self-review notes (already applied)

- Spec §5 (schema) → Task 1 (migration + `@IdClass`); §6 (resolution) → Task 2 + Review Focus 1; §7 (projector) → Task 3 + Review Focus 2/4/5; §8 (trigger) → Task 4 + Review Focus 3; §9 (query) → Task 5; §10 (consumers) → Task 5; §11 (removal) → Task 5; §13 (follow-on) → Task 6 (deploy backfill).
- Bean-cycle risk avoided by injecting repositories into the projector (Global Constraints + Task 3).
- The spec's "add trading_date to the PK" implies the `@IdClass` change; made explicit in Task 1 so `ddl-auto: validate` and merge-by-id stay correct.
- Review Focus items 1–5 are each pinned by a test named in the corresponding task.
