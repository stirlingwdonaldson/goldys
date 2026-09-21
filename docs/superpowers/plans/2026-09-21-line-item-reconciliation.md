# Line-Item Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconcile Lightspeed and CTB at the per-product line-item level — match products by normalized name, flag quantity/amount conflicts, surface source-only products, and let staff override per product.

**Architecture:** Mirror the daily-total slice. A new bitemporal `CanonicalProductSales` entity (keyed by normalized product name + trading date + source) is fed by an extended CTB connector (`Sale/SearchSaleItemsByDateRange`) and a new Lightspeed per-product webhook. A `ProductSalesReconciliationService` matches by name (zero tolerance) and reuses the append-only override pattern; the existing reconciliation screen drills into per-product conflicts.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Security 6, JPA, PostgreSQL 16, Flyway, Testcontainers, Commons CSV, Jackson; Next.js frontend.

**Spec:** `docs/superpowers/specs/2026-09-21-line-item-reconciliation-design.md`

## Global Constraints

- Name matching is **zero-tolerance** (exact equality after normalization); never guess a tolerance.
- Normalize product names: lowercase, strip non-alphanumerics, collapse whitespace, then strip a leading `New `.
- Persist source bytes before parsing; raw log append-only; canonical rows close (never overwrite).
- Route overrides through the sole `PermissionService`; explicit denial.
- Surface "no data from source X" explicitly; never silently drop a source-only product.
- Schema owned by Flyway (`ddl-auto: validate`); every table change is a versioned migration.
- Credentials in env vars only.

## Review Focus

1. **Name normalization** — `New Kids Fish & Chippies` and `Kids Fish & Chippies` must collide; stray whitespace/case must not split one product into two.
2. **Both-fields conflict** — a product whose quantity AND amount both differ is one conflict carrying both values, not two.
3. **Source-only product** — a product present in only one source is surfaced (status `missing`), never silently dropped (this is where the 17 modifiers land).
4. **Per-day scoping** — the same product on two different trading days is two independent reconciliations, never conflated.
5. **Override resolves** — after a per-product override, the conflict drops out of the open-exceptions list.

Each line is pinned by a test in the owning task (noted there).

---

### Task 1: Product-name key + CTB sale-item parser

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductNameKey.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbSaleItem.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbSaleItemParser.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/ProductNameKeyTest.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/CtbSaleItemParserTest.java`
- Test resource: `backend/src/test/resources/fixtures/ctb/sale_items_sample.json`

**Interfaces:**
- Consumes: Jackson (already a dependency).
- Produces: `ProductNameKey.normalize(String) -> String` (public static), `CtbSaleItem(String stockCode, String stockDescription, BigDecimal quantitySold, BigDecimal amount)`, `CtbSaleItemParser.parse(byte[]) -> List<CtbSaleItem>` for Tasks 2, 3, 6.

- [ ] **Step 1: Write `ProductNameKey`**

```java
package com.goldys.platform.canonical;

import java.util.Locale;

/** The key a product is matched on: a normalized name with the "New " alias folded in. */
public final class ProductNameKey {
  private ProductNameKey() {}

  public static String normalize(String name) {
    if (name == null) return "";
    String lower = name.toLowerCase(Locale.ROOT);
    String alnum = lower.replaceAll("[^a-z0-9]+", " ").trim();
    String collapsed = alnum.replaceAll("\\s+", " ");
    return collapsed.startsWith("new ") ? collapsed.substring(4) : collapsed;
  }
}
```

- [ ] **Step 2: Write the `ProductNameKeyTest`**

```java
package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductNameKeyTest {

  @Test
  void normalizesCasePunctuationAndWhitespace() {
    assertThat(ProductNameKey.normalize("  Pint  -  Carlton   Draught "))
        .isEqualTo("pint carlton draught");
  }

  @Test
  void foldsTheNewPrefixAlias() {
    assertThat(ProductNameKey.normalize("New Kids Fish & Chippies"))
        .isEqualTo("kids fish chippies");
    assertThat(ProductNameKey.normalize("Kids Fish & Chippies"))
        .isEqualTo("kids fish chippies");
  }

  @Test
  void blankNameIsEmpty() {
    assertThat(ProductNameKey.normalize(null)).isEmpty();
    assertThat(ProductNameKey.normalize("   ")).isEmpty();
  }
}
```

- [ ] **Step 3: Write `CtbSaleItem`**

```java
package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/** One per-product sale-item row from CTB's {@code Sale/SearchSaleItemsByDateRange}. */
public record CtbSaleItem(
    String stockCode, String stockDescription, BigDecimal quantitySold, BigDecimal amount) {}
```

- [ ] **Step 4: Write the fixture `sale_items_sample.json`** (a trimmed slice of the discovery pull)

```json
{
  "data": [
    { "stockCode": "17176896", "stockDescription": "(0.5L) Goldy's Pinot Grigio", "quantitySold": 3, "amount": 120.66 },
    { "stockCode": "22101660", "stockDescription": "$28 Steak Special", "quantitySold": 99, "amount": 2732.07 },
    { "stockCode": "987654", "stockDescription": "New Kids Parma", "quantitySold": 7, "amount": 84.00 }
  ],
  "totalCount": 3,
  "message": { "IsSuccess": true, "Info": "" }
}
```

- [ ] **Step 5: Write `CtbSaleItemParser`**

```java
package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Parses CTB's {@code Sale/SearchSaleItemsByDateRange} envelope into per-product rows. */
public class CtbSaleItemParser {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public List<CtbSaleItem> parse(byte[] json) {
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (Exception e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Not JSON: " + e.getMessage(), e);
    }
    JsonNode data = root.get("data");
    if (data == null || !data.isArray()) {
      return List.of();
    }
    List<CtbSaleItem> out = new ArrayList<>();
    for (JsonNode n : data) {
      out.add(
          new CtbSaleItem(
              n.path("stockCode").asText(),
              n.path("stockDescription").asText(),
              n.path("quantitySold").decimalValue(),
              n.path("amount").decimalValue()));
    }
    return out;
  }
}
```

- [ ] **Step 6: Write `CtbSaleItemParserTest`**

```java
package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CtbSaleItemParserTest {

  @Test
  void parsesRows() throws Exception {
    byte[] json = Files.readAllBytes(Path.of("src/test/resources/fixtures/ctb/sale_items_sample.json"));

    var rows = new CtbSaleItemParser().parse(json);

    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).stockDescription()).isEqualTo("(0.5L) Goldy's Pinot Grigio");
    assertThat(rows.get(0).quantitySold()).isEqualByComparingTo("3");
    assertThat(rows.get(1).amount()).isEqualByComparingTo("2732.07");
  }
}
```

- [ ] **Step 7: Run RED then GREEN**

Run: `cd backend && ./gradlew test --tests '*ProductNameKeyTest' --tests '*CtbSaleItemParserTest' spotlessApply spotlessCheck`
Expected: BUILD SUCCESSFUL (the classes are written above, so this is GREEN; RED is the compile-fail when run before Steps 1–6).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/ProductNameKey.java \
        backend/src/main/java/com/goldys/platform/connectors/ctb/CtbSaleItem.java \
        backend/src/main/java/com/goldys/platform/connectors/ctb/CtbSaleItemParser.java \
        backend/src/test/java/com/goldys/platform/canonical/ProductNameKeyTest.java \
        backend/src/test/java/com/goldys/platform/connectors/ctb/CtbSaleItemParserTest.java \
        backend/src/test/resources/fixtures/ctb/sale_items_sample.json
git commit -m "feat: add product-name key and ctb sale-item parser"
```

---

### Task 2: Canonical per-product entity (migration + entity + repository + service)

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__product_sales.sql`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSales.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductSalesInput.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ProductSalesView.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalProductSalesIntegrationTest.java` (Testcontainers, CI-gated)

**Interfaces:**
- Consumes: `BitemporalEntity`, `BitemporalRepository`, `ProductNameKey` (Task 1), `RawPayloadService` pattern.
- Produces: `ProductSalesInput(String sourceSystem, LocalDate tradingDate, String productNameKey, BigDecimal quantitySold, BigDecimal amount, UUID rawRecordId)`, `CanonicalProductSalesService.record(ProductSalesInput)`, `CanonicalProductSalesQuery.currentProductSales()`, `ProductSalesView(String sourceSystem, LocalDate tradingDate, String productNameKey, BigDecimal quantitySold, BigDecimal amount)` for Tasks 3, 4, 5.

- [ ] **Step 1: Write the V9 migration**

```sql
CREATE TABLE canonical_product_sales (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    trading_date date NOT NULL,
    product_name_key varchar(512) NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    quantity_sold numeric(14,4) NOT NULL,
    amount numeric(14,4) NOT NULL,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX ux_product_sales_current
    ON canonical_product_sales (product_name_key, trading_date, source_system)
    WHERE superseded_at IS NULL;
CREATE INDEX ix_product_sales_logical
    ON canonical_product_sales (logical_entity_id) WHERE superseded_at IS NULL;
```

- [ ] **Step 2: Write `ProductSalesInput`**

```java
package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A normalized per-product sales total from one source, to be recorded canonically. */
public record ProductSalesInput(
    String sourceSystem,
    LocalDate tradingDate,
    String productNameKey,
    BigDecimal quantitySold,
    BigDecimal amount,
    UUID rawRecordId) {}
```

- [ ] **Step 3: Write `CanonicalProductSales`** (extends `BitemporalEntity`, fact fields `tradingDate`, `productNameKey`, `quantitySold`, `amount`, plus `create(...)` and `sameFact(ProductSalesInput)`)

```java
package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One version of a source's per-product daily sales total. */
@Entity
@Table(name = "canonical_product_sales")
class CanonicalProductSales extends BitemporalEntity {
  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "product_name_key", nullable = false, updatable = false, length = 512)
  private String productNameKey;

  @Column(name = "quantity_sold", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal quantitySold;

  @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal amount;

  protected CanonicalProductSales() {}

  private CanonicalProductSales(
      UUID logicalEntityId, String sourceSystem, String sourceRecordRef, UUID rawRecordId,
      Instant validFrom, Instant recordedAt, LocalDate tradingDate, String productNameKey,
      BigDecimal quantitySold, BigDecimal amount) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.tradingDate = tradingDate;
    this.productNameKey = productNameKey;
    this.quantitySold = quantitySold;
    this.amount = amount;
  }

  static CanonicalProductSales create(
      UUID logicalEntityId, String sourceSystem, String sourceRecordRef, UUID rawRecordId,
      Instant validFrom, Instant recordedAt, LocalDate tradingDate, String productNameKey,
      BigDecimal quantitySold, BigDecimal amount) {
    return new CanonicalProductSales(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId,
        validFrom, recordedAt, tradingDate, productNameKey, quantitySold, amount);
  }

  boolean sameFact(ProductSalesInput input) {
    return amount.compareTo(input.amount()) == 0
        && quantitySold.compareTo(input.quantitySold()) == 0;
  }

  LocalDate tradingDate() { return tradingDate; }
  String productNameKey() { return productNameKey; }
  BigDecimal quantitySold() { return quantitySold; }
  BigDecimal amount() { return amount; }
}
```

- [ ] **Step 4: Write `CanonicalProductSalesRepository`**

```java
package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalProductSalesRepository extends BitemporalRepository<CanonicalProductSales> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from CanonicalProductSales s where s.productNameKey = :key "
      + "and s.tradingDate = :date and s.sourceSystem = :source and s.supersededAt is null")
  Optional<CanonicalProductSales> lockCurrent(String key, LocalDate date, String source);

  @Query("select s from CanonicalProductSales s where s.tradingDate = :date and s.supersededAt is null")
  List<CanonicalProductSales> findCurrentByDate(LocalDate date);

  @Query("select s from CanonicalProductSales s where s.supersededAt is null")
  List<CanonicalProductSales> findAllCurrent();
}
```

- [ ] **Step 5: Write `CanonicalProductSalesService`** (deterministic logical id from `productNameKey` + date)

```java
package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records canonical per-product daily totals; idempotent and name-keyed. */
@Service
class CanonicalProductSalesService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalProductSalesRepository repository;

  CanonicalProductSalesService(CanonicalProductSalesRepository repository) {
    this.repository = repository;
  }

  @Transactional
  CanonicalProductSales record(ProductSalesInput input) {
    return recordAt(input, CLOCK.instant());
  }

  @Transactional
  CanonicalProductSales recordAt(ProductSalesInput input, Instant recordedAt) {
    UUID logicalId = logicalIdFor(input.productNameKey(), input.tradingDate());
    String ref = input.productNameKey() + "@" + input.tradingDate();
    Optional<CanonicalProductSales> current =
        repository.lockCurrent(input.productNameKey(), input.tradingDate(), input.sourceSystem());
    if (current.isPresent()) {
      CanonicalProductSales existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }
    return repository.save(
        CanonicalProductSales.create(
            logicalId, input.sourceSystem(), ref, input.rawRecordId(), recordedAt, recordedAt,
            input.tradingDate(), input.productNameKey(), input.quantitySold(), input.amount()));
  }

  private static UUID logicalIdFor(String key, java.time.LocalDate date) {
    return UUID.nameUUIDFromBytes(("product-sales:" + key + ":" + date).getBytes(StandardCharsets.UTF_8));
  }
}
```

- [ ] **Step 6: Write `ProductSalesView` + `CanonicalProductSalesQuery`**

```java
package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A read-only view of one current per-product sales total. */
public record ProductSalesView(
    String sourceSystem, LocalDate tradingDate, String productNameKey,
    BigDecimal quantitySold, BigDecimal amount) {}
```

```java
package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read-only query facade for canonical per-product sales. */
@Service
public class CanonicalProductSalesQuery {
  private final CanonicalProductSalesRepository repository;

  public CanonicalProductSalesQuery(CanonicalProductSalesRepository repository) {
    this.repository = repository;
  }

  public List<ProductSalesView> currentProductSales() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  public List<ProductSalesView> currentProductSalesForDate(LocalDate date) {
    return repository.findCurrentByDate(date).stream().map(this::toView).toList();
  }

  private ProductSalesView toView(CanonicalProductSales s) {
    return new ProductSalesView(
        s.sourceSystem(), s.tradingDate(), s.productNameKey(), s.quantitySold(), s.amount());
  }
}
```

- [ ] **Step 7: Write `CanonicalProductSalesIntegrationTest`** (Testcontainers; idempotent retry + two sources sharing the logical id)

```java
package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

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
class CanonicalProductSalesIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalProductSalesService service;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_product_sales");
  }

  @Test
  void twoSourcesForTheSameProductAndDateShareTheLogicalId() {
    LocalDate date = LocalDate.of(2026, 9, 14);
    var ls = service.record(new ProductSalesInput("LIGHTSPEED", date, "pint carlton draught", bd("1232"), bd("17340.98"), rawRecord()));
    var ctb = service.record(new ProductSalesInput("CTB", date, "pint carlton draught", bd("1232"), bd("17340.98"), rawRecord()));

    assertThat(ls.logicalEntityId()).isEqualTo(ctb.logicalEntityId());
  }

  @Test
  void unchangedRetryDoesNotAppendAVersion() {
    LocalDate date = LocalDate.of(2026, 9, 14);
    ProductSalesInput input = new ProductSalesInput("CTB", date, "chicken parma", bd("391"), bd("11418.06"), rawRecord());

    var first = service.record(input);
    var retry = service.record(input);

    assertThat(retry.id()).isEqualTo(first.id());
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'CTB', 'test', 'SUCCESS', now(), 1, 1)", runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'CTB', 'API', 'application/json', ?, ?, ?, 'test', now())",
        recordId, runId, new byte[] {1}, "0".repeat(64), 1);
    return recordId;
  }

  private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
```

- [ ] **Step 8: Compile + commit**

Run: `cd backend && ./gradlew compileJava compileTestJava spotlessApply spotlessCheck`
Expected: BUILD SUCCESSFUL (the integration test is Testcontainers-gated).

```bash
git add backend/src/main/resources/db/migration/V9__product_sales.sql backend/src/main/java/com/goldys/platform/canonical backend/src/test/java/com/goldys/platform/canonical
git commit -m "feat: add canonical per-product sales entity"
```

---

### Task 3: Reconciliation service (matching + conflict + unmatched)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesReconciliationService.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSourceTotal.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesConflict.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesReconciliationTest.java`

**Interfaces:**
- Consumes: `CanonicalProductSalesQuery`, `ProductSalesView` (Task 2), `ProductNameKey` (Task 1).
- Produces: `ProductSourceTotal(String sourceSystem, BigDecimal quantitySold, BigDecimal amount)`, `ProductSalesConflict(LocalDate tradingDate, String productNameKey, List<ProductSourceTotal> sources, String status)`, `ProductSalesReconciliationService.conflicts() -> List<ProductSalesConflict>` for Task 4, 5.

- [ ] **Step 1: Write `ProductSourceTotal` + `ProductSalesConflict`**

```java
package com.goldys.platform.reconciliation;

import java.math.BigDecimal;

/** One source's per-product values for a trading date. */
public record ProductSourceTotal(String sourceSystem, BigDecimal quantitySold, BigDecimal amount) {}
```

```java
package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.List;

/** A product/day whose sources disagree ("conflict") or where a source is absent ("missing"). */
public record ProductSalesConflict(
    LocalDate tradingDate, String productNameKey, List<ProductSourceTotal> sources, String status) {}
```

- [ ] **Step 2: Write `ProductSalesReconciliationService`**

```java
package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Reconciles per-product daily sales by normalized name + date, with zero tolerance. */
@Service
public class ProductSalesReconciliationService {
  private final CanonicalProductSalesQuery productSales;

  public ProductSalesReconciliationService(CanonicalProductSalesQuery productSales) {
    this.productSales = productSales;
  }

  public List<ProductSalesConflict> conflicts() {
    Map<String, List<ProductSourceTotal>> byKey = new LinkedHashMap<>();
    for (ProductSalesView view : productSales.currentProductSales()) {
      byKey
          .computeIfAbsent(view.tradingDate() + "\u0000" + view.productNameKey(), k -> new ArrayList<>())
          .add(new ProductSourceTotal(view.sourceSystem(), view.quantitySold(), view.amount()));
    }
    List<ProductSalesConflict> out = new ArrayList<>();
    for (Map.Entry<String, List<ProductSourceTotal>> e : byKey.entrySet()) {
      String status = classify(e.getValue());
      if (!"agreed".equals(status)) {
        String[] parts = e.getKey().split("\u0000");
        out.add(new ProductSalesConflict(LocalDate.parse(parts[0]), parts[1], e.getValue(), status));
      }
    }
    out.sort(Comparator.comparing(ProductSalesConflict::tradingDate)
        .thenComparing(ProductSalesConflict::productNameKey));
    return out;
  }

  static String classify(List<ProductSourceTotal> sources) {
    if (sources.size() < 2) {
      return "missing";
    }
    ProductSourceTotal first = sources.get(0);
    boolean agree = sources.stream()
        .allMatch(s -> first.quantitySold().compareTo(s.quantitySold()) == 0
            && first.amount().compareTo(s.amount()) == 0);
    return agree ? "agreed" : "conflict";
  }
}
```

- [ ] **Step 3: Write `ProductSalesReconciliationTest`** (pins Review Focus lines 1, 2, 3)

```java
package com.goldys.platform.reconciliation;

import static com.goldys.platform.reconciliation.ProductSalesReconciliationService.classify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductSalesReconciliationTest {

  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Test
  void classifiesAgreedConflictAndMissing() {
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"), st("CTB", "10", "100.00"))))
        .isEqualTo("agreed");
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"), st("CTB", "9", "90.00"))))
        .isEqualTo("conflict"); // both fields differ — one conflict, not two
    assertThat(classify(List.of(st("LIGHTSPEED", "10", "100.00"))))
        .isEqualTo("missing"); // source-only product is surfaced, not dropped
  }

  @Test
  void conflictsGroupByProductAndDate() {
    CanonicalProductSalesQuery query = mock(CanonicalProductSalesQuery.class);
    when(query.currentProductSales())
        .thenReturn(
            List.of(
                view("LIGHTSPEED", SEP_14, "pint carlton draught", "1232", "17340.98"),
                view("CTB", SEP_14, "pint carlton draught", "1232", "17340.98"), // agree
                view("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"),
                view("CTB", SEP_14, "garlic aioli", "127", "322.46"))); // conflict

    ProductSalesReconciliationService service = new ProductSalesReconciliationService(query);

    List<ProductSalesConflict> conflicts = service.conflicts();
    assertThat(conflicts).hasSize(1);
    assertThat(conflicts.get(0).productNameKey()).isEqualTo("garlic aioli");
    assertThat(conflicts.get(0).status()).isEqualTo("conflict");
  }

  private static ProductSourceTotal st(String source, String qty, String amount) {
    return new ProductSourceTotal(source, new BigDecimal(qty), new BigDecimal(amount));
  }

  private static ProductSalesView view(String source, LocalDate date, String key, String qty, String amount) {
    return new ProductSalesView(source, date, key, new BigDecimal(qty), new BigDecimal(amount));
  }
}
```

- [ ] **Step 4: Run RED → GREEN + commit**

Run: `cd backend && ./gradlew test --tests '*ProductSalesReconciliationTest' spotlessApply spotlessCheck`
Expected: PASS.

```bash
git add backend/src/main/java/com/goldys/platform/reconciliation backend/src/test/java/com/goldys/platform/reconciliation
git commit -m "feat: reconcile per-product sales totals"
```

---

### Task 4: Per-product override (entity + repository + service)

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__product_sales_override.sql`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesOverride.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesOverrideRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesOverrideService.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesOverrideServiceTest.java`

**Interfaces:**
- Consumes: `PermissionService`, `ResourceKey`, `UserRole`, `CanonicalProductSalesQuery` (Task 2).
- Produces: `ProductSalesOverrideService.save(UserRole, String actorEmail, LocalDate date, String productNameKey, String source, String reason)`, `currentAuthoritativeSource(LocalDate, String) -> Optional<String>` for Task 5.

- [ ] **Step 1: Write the V10 migration**

```sql
CREATE TABLE product_sales_override (
    id uuid PRIMARY KEY,
    trading_date date NOT NULL,
    product_name_key varchar(512) NOT NULL,
    authoritative_source varchar(255) NOT NULL,
    reason text,
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX ux_product_sales_override_current
    ON product_sales_override (product_name_key, trading_date) WHERE superseded_at IS NULL;
```

- [ ] **Step 2: Write `ProductSalesOverride`** (append-only, mirror `DailySalesOverride` but keyed by `(product_name_key, trading_date)`)

```java
package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** An append-only manual override: which source is authoritative for a product/day. */
@Entity
@Table(name = "product_sales_override")
class ProductSalesOverride {
  @Id private UUID id;

  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "product_name_key", nullable = false, updatable = false, length = 512)
  private String productNameKey;

  @Column(name = "authoritative_source", nullable = false, updatable = false)
  private String authoritativeSource;

  @Column(name = "reason")
  private String reason;

  @Column(name = "actor_email", nullable = false, updatable = false)
  private String actorEmail;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  protected ProductSalesOverride() {}

  private ProductSalesOverride(
      LocalDate tradingDate, String productNameKey, String authoritativeSource, String reason,
      String actorEmail, Instant recordedAt) {
    this.id = UUID.randomUUID();
    this.tradingDate = Objects.requireNonNull(tradingDate);
    this.productNameKey = Objects.requireNonNull(productNameKey);
    this.authoritativeSource = Objects.requireNonNull(authoritativeSource);
    this.reason = reason;
    this.actorEmail = Objects.requireNonNull(actorEmail);
    this.recordedAt = Objects.requireNonNull(recordedAt);
  }

  static ProductSalesOverride create(
      LocalDate tradingDate, String productNameKey, String authoritativeSource, String reason,
      String actorEmail, Instant recordedAt) {
    return new ProductSalesOverride(
        tradingDate, productNameKey, authoritativeSource, reason, actorEmail, recordedAt);
  }

  void supersede(Instant at) {
    if (supersededAt != null) {
      throw new IllegalStateException("Override " + id + " is already superseded");
    }
    this.supersededAt = Objects.requireNonNull(at);
  }

  UUID id() { return id; }
  LocalDate tradingDate() { return tradingDate; }
  String productNameKey() { return productNameKey; }
  String authoritativeSource() { return authoritativeSource; }
  String reason() { return reason; }
  String actorEmail() { return actorEmail; }
  Instant recordedAt() { return recordedAt; }
  Instant supersededAt() { return supersededAt; }
}
```

- [ ] **Step 3: Write `ProductSalesOverrideRepository`**

```java
package com.goldys.platform.reconciliation;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ProductSalesOverrideRepository extends JpaRepository<ProductSalesOverride, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from ProductSalesOverride o where o.productNameKey = :key "
      + "and o.tradingDate = :date and o.supersededAt is null")
  Optional<ProductSalesOverride> lockCurrent(String key, LocalDate date);

  @Query("select o from ProductSalesOverride o where o.productNameKey = :key "
      + "and o.tradingDate = :date and o.supersededAt is null")
  Optional<ProductSalesOverride> findCurrent(String key, LocalDate date);
}
```

- [ ] **Step 4: Write `ProductSalesOverrideService`**

```java
package com.goldys.platform.reconciliation;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records permission-gated, append-only per-product overrides. */
@Service
public class ProductSalesOverrideService {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");
  private static final Clock CLOCK = Clock.systemUTC();

  private final ProductSalesOverrideRepository repository;
  private final PermissionService permissions;
  private final CanonicalProductSalesQuery productSales;

  public ProductSalesOverrideService(
      ProductSalesOverrideRepository repository, PermissionService permissions,
      CanonicalProductSalesQuery productSales) {
    this.repository = repository;
    this.permissions = permissions;
    this.productSales = productSales;
  }

  @Transactional
  public ProductSalesOverride save(
      UserRole actor, String actorEmail, LocalDate date, String productNameKey,
      String source, String reason) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);

    boolean sourceKnown =
        productSales.currentProductSalesForDate(date).stream()
            .anyMatch(s -> s.sourceSystem().equals(source) && s.productNameKey().equals(productNameKey));
    if (!sourceKnown) {
      throw new IllegalArgumentException(
          "No data from source '" + source + "' for product '" + productNameKey + "' on " + date);
    }

    Instant now = CLOCK.instant();
    Optional<ProductSalesOverride> current = repository.lockCurrent(productNameKey, date);
    if (current.isPresent()) {
      current.get().supersede(now);
      repository.saveAndFlush(current.get());
    }
    return repository.save(
        ProductSalesOverride.create(date, productNameKey, source, reason, actorEmail, now));
  }

  public Optional<String> currentAuthoritativeSource(LocalDate date, String productNameKey) {
    return repository.findCurrent(productNameKey, date).map(ProductSalesOverride::authoritativeSource);
  }
}
```

- [ ] **Step 5: Write `ProductSalesOverrideServiceTest`**

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
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductSalesOverrideServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  private static CanonicalProductSalesQuery queryWithCtb() {
    CanonicalProductSalesQuery query = mock(CanonicalProductSalesQuery.class);
    when(query.currentProductSalesForDate(SEP_14))
        .thenReturn(List.of(new ProductSalesView("CTB", SEP_14, "garlic aioli", bd("127"), bd("322.46"))));
    return query;
  }

  @Test
  void deniedUserGetsAccessDenied() {
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions).require(any(), any(), any());
    ProductSalesOverrideService service =
        new ProductSalesOverrideService(mock(ProductSalesOverrideRepository.class), permissions, queryWithCtb());

    assertThatThrownBy(() -> service.save(OWNER, "a@b.com", SEP_14, "garlic aioli", "CTB", null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void unknownSourceIsRejected() {
    ProductSalesOverrideService service =
        new ProductSalesOverrideService(mock(ProductSalesOverrideRepository.class),
            mock(PermissionService.class), queryWithCtb());

    assertThatThrownBy(() -> service.save(OWNER, "a@b.com", SEP_14, "garlic aioli", "LIGHTSPEED", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void permittedSaveRecordsTheOverride() {
    ProductSalesOverrideRepository repository = mock(ProductSalesOverrideRepository.class);
    when(repository.lockCurrent("garlic aioli", SEP_14)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    ProductSalesOverrideService service =
        new ProductSalesOverrideService(repository, mock(PermissionService.class), queryWithCtb());

    ProductSalesOverride saved = service.save(OWNER, "a@b.com", SEP_14, "garlic aioli", "CTB", "typo");

    assertThat(saved.authoritativeSource()).isEqualTo("CTB");
    assertThat(saved.productNameKey()).isEqualTo("garlic aioli");
  }

  private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
```

- [ ] **Step 6: Run + commit**

Run: `cd backend && ./gradlew test --tests '*ProductSalesOverrideServiceTest' spotlessApply spotlessCheck`
Expected: PASS.

```bash
git add backend/src/main/resources/db/migration/V10__product_sales_override.sql backend/src/main/java/com/goldys/platform/reconciliation backend/src/test/java/com/goldys/platform/reconciliation
git commit -m "feat: add per-product override"
```

---

### Task 5: Controllers + DTOs

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/api/ReconciliationController.java` (add product endpoints + an override-drop for product conflicts)
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/ProductSalesReconciliationService.java` (skip overridden product/days in `conflicts()`)
- Test: `backend/src/test/java/com/goldys/platform/api/ProductReconciliationControllerTest.java` (new `@WebMvcTest` if a separate controller is used, else extend `ReconciliationControllerTest`)

**Interfaces:**
- Consumes: `ProductSalesReconciliationService` (Task 3), `ProductSalesOverrideService` (Task 4), `CurrentUserService`, `PermissionService`.
- Produces: `GET /api/reconciliation/products/exceptions`, `GET /api/reconciliation/products/{date}/{product}`, `POST /api/reconciliation/products/{date}/{product}/override` (JSON shapes mirror the daily `ExceptionDto`/`RecordDto`, keyed by product).

- [ ] **Step 1: Make `ProductSalesReconciliationService.conflicts()` skip overridden products** (inject `ProductSalesOverrideRepository`, add a `findCurrent` guard)

```java
// constructor gains: private final ProductSalesOverrideRepository overrides;
// in conflicts(), before adding a conflict:
  if (overrides.findCurrent(parts[1], LocalDate.parse(parts[0])).isPresent()) {
    continue;
  }
```

- [ ] **Step 2: Add the product endpoints to `ReconciliationController`** (same `RESOURCE`/`currentUser`/`permissions` fields already present)

```java
  @GetMapping("/products/exceptions")
  List<ProductExceptionDto> productExceptions(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return productSales.conflicts().stream().map(this::toProductException).toList();
  }

  @GetMapping("/products/{date}/{product}")
  ProductRecordDto productRecord(@PathVariable LocalDate date, @PathVariable String product,
      @AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    List<SourceValueDto> sources =
        productSales.currentProductSalesForDate(date).stream()
            .filter(v -> v.productNameKey().equals(product))
            .map(v -> new SourceValueDto(v.sourceSystem(), v.quantitySold().toPlainString() + " × $" + v.amount().toPlainString()))
            .toList();
    Optional<String> authoritative = productOverrides.currentAuthoritativeSource(date, product);
    FieldDto field =
        new FieldDto(product, product, sources, authoritative.isPresent(), authoritative.orElse(null));
    return new ProductRecordDto(product, product, "product", List.of(field));
  }

  @PostMapping("/products/{date}/{product}/override")
  OverrideResultDto productOverride(@PathVariable LocalDate date, @PathVariable String product,
      @RequestBody OverrideRequestDto body, @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    productOverrides.save(role, user.email(), date, product, body.source(), body.reason());
    return new OverrideResultDto(true, product, "product");
  }

  record ProductExceptionDto(String id, String recordId, String entity, String field,
      List<SourceValueDto> sources, String status) {}

  record ProductRecordDto(String id, String entity, String entityType, List<FieldDto> fields) {}

  private ProductExceptionDto toProductException(ProductSalesConflict c) {
    List<SourceValueDto> sources =
        c.sources().stream()
            .map(s -> new SourceValueDto(s.sourceSystem(),
                s.quantitySold().toPlainString() + " × $" + s.amount().toPlainString()))
            .toList();
    return new ProductExceptionDto(
        c.tradingDate() + ":" + c.productNameKey(), c.productNameKey(),
        c.productNameKey(), "product", sources, c.status());
  }
```

- [ ] **Step 3: Write the controller test** (extend the `@WebMvcTest`; denied → 403, happy path → the conflict JSON)

```java
// ProductReconciliationControllerTest: @WebMvcTest(ReconciliationController.class) + @Import(SecurityConfig.class)
// @MockitoBean ProductSalesReconciliationService productSales; @MockitoBean ProductSalesOverrideService productOverrides; …
// test: when(productSales.conflicts()).thenReturn(List.of(new ProductSalesConflict(SEP_14, "garlic aioli", List.of(...), "conflict")));
//       get("/api/reconciliation/products/exceptions").with(authenticated(owner()))
//       -> status 200 + jsonPath("$[0].field").value("garlic aioli")
// test: doThrow(AccessDeniedException).when(permissions).require(...) -> get(...) -> 403 NOT_PERMITTED
```

- [ ] **Step 4: Run + commit**

Run: `cd backend && ./gradlew test --tests '*ProductSalesReconciliationTest' --tests '*ProductSalesOverrideServiceTest' --tests '*ReconciliationControllerTest' spotlessApply spotlessCheck`
Expected: PASS.

```bash
git add backend/src/main/java/com/goldys/platform/api backend/src/main/java/com/goldys/platform/reconciliation backend/src/test/java/com/goldys/platform/api
git commit -m "feat: expose per-product reconciliation endpoints"
```

---

### Task 6: CTB sale-items ingestion (client + connector extension)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbClient.java` (add `searchSaleItems`)
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbConnector.java` (pull + parse + aggregate sale items → `CanonicalProductSales`)
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbConfig.java` (inject `CanonicalProductSalesService` facade)

**Interfaces:**
- Consumes: `CtbSaleItemParser` (Task 1), `ProductNameKey` (Task 1), `CanonicalProductSalesIngest` (Task 2 — add a public `record` facade), `FetchedPayload`/`IngestionSink`.
- Produces: CTB runs now also canonicalize per-product daily totals.

- [ ] **Step 1: Add `CanonicalProductSalesIngest` facade** (Task 2 addendum, one file)

```java
package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical per-product sales. */
@Service
public class CanonicalProductSalesIngest {
  private final CanonicalProductSalesService service;
  public CanonicalProductSalesIngest(CanonicalProductSalesService service) { this.service = service; }
  public void record(ProductSalesInput input) { service.record(input); }
}
```

- [ ] **Step 2: Add `searchSaleItems` to `CtbClient`**

```java
  /** One page of sale items plus the total count, as raw JSON for the sink. */
  public CtbPage searchSaleItems(String fromDate, String toDate, int start, int limit) {
    String body =
        post("/Sale/SearchSaleItemsByDateRange",
            form("fromDate", fromDate, "toDate", toDate, "searchType", "-1",
                 "start", String.valueOf(start), "limit", String.valueOf(limit)));
    JsonNode json = parse(body);
    if (!json.path("message").path("IsSuccess").asBoolean(false)) {
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "CTB sale-item search failed");
    }
    int total = json.path("totalCount").asInt(json.path("data").size());
    return new CtbPage(body, total);
  }
```

- [ ] **Step 3: Extend `CtbConnector.fetch`** to also aggregate sale items per (date, product) and record them (after the existing revenue loop)

```java
// new fields: private final CtbSaleItemParser saleItemParser; private final CanonicalProductSalesIngest productSales;
// in fetch(...), after the revenue loop:
    int start2 = 0;
    int pages2 = 0;
    int total2 = -1;
    Map<String, ProductSalesInput> byProduct = new LinkedHashMap<>(); // key date+"\0"+ProductNameKey.normalize(name)
    Map<String, UUID> rawByProduct = new LinkedHashMap<>();
    while (pages2 < MAX_PAGES) {
      CtbClient.CtbPage page = client.searchSaleItems(watermarkDate(watermark), LocalDate.now().toString(), start2, PAGE_SIZE);
      byte[] bytes = page.json().getBytes(StandardCharsets.UTF_8);
      UUID rawId = sink.accept(new FetchedPayload(FetchMethod.API, "application/json", bytes,
          StandardCharsets.UTF_8.name(), "ctb-revenue"));
      for (CtbSaleItem item : saleItemParser.parse(bytes)) {
        String key = item.saleDateKey(); // see note
        byProduct.merge(key, accumulate(item), (a, b) -> a.add(b));
        rawByProduct.putIfAbsent(key, rawId);
      }
      total2 = page.totalCount();
      pages2++;
      if (start2 + PAGE_SIZE >= total2) break;
      start2 += PAGE_SIZE;
    }
    for (Map.Entry<String, ProductSalesInput> e : byProduct.entrySet()) {
      productSales.record(e.getValue());
    }
```

> **Note for the implementer:** CTB's `SearchSaleItemsByDateRange` returns per-product rows with **no per-row date** (`saleDate` is null even for a single-day range — see the discovery). The date is the *range* supplied to the call. So aggregate to a single `tradingDate` = the range's `toDate`, and key by `ProductNameKey.normalize(stockDescription)`. The `watermarkDate(String)` helper returns the watermark if it parses as an ISO date, else a fixed look-back (e.g. `LocalDate.now().minusDays(90)`), matching the connector's existing watermark handling.

- [ ] **Step 4: Run RED → GREEN (compile + existing CTB tests) + commit**

Run: `cd backend && ./gradlew compileJava spotlessApply spotlessCheck test --tests '*CtbRevenueParserTest'`
Expected: BUILD SUCCESSFUL.

```bash
git add backend/src/main/java/com/goldys/platform/connectors/ctb backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesIngest.java
git commit -m "feat: pull and canonicalize ctb per-product sale items"
```

---

### Task 7: Lightspeed per-product webhook + parser

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedProductCsvParser.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedProductSale.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/LightspeedIngestController.java` (add `POST /api/ingest/lightspeed-products`)
- Test: `backend/src/test/java/com/goldys/platform/connectors/lightspeed/LightspeedProductCsvParserTest.java`
- Test resource: `backend/src/test/resources/fixtures/lightspeed/product_sales_sample.csv`

**Interfaces:**
- Consumes: Commons CSV, `ProductNameKey` (Task 1), `IngestionService`, `CanonicalProductSalesIngest` (Task 2/6), `LightspeedIngestService` pattern.
- Produces: `LightspeedProductSale(LocalDate tradingDate, String productName, BigDecimal quantitySold, BigDecimal amount)`, `LightspeedProductCsvParser.parse(byte[]) -> List<LightspeedProductSale>`.

- [ ] **Step 1: Write `LightspeedProductSale` + `LightspeedProductCsvParser`** (parse the "Sales By" CSV shape: `Product` name, `Quantity`, `Sale Amount`; strip `$`/`,` from amounts; use explicit headers like the daily parser)

```java
package com.goldys.platform.connectors.lightspeed;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One per-product row from the Lightspeed "sales by product" report. */
public record LightspeedProductSale(LocalDate tradingDate, String productName, BigDecimal quantitySold, BigDecimal amount) {}
```

```java
package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/** Parses the per-product CSV the scheduled Lightspeed report delivers (shape: the "Sales By" export). */
@Component
public class LightspeedProductCsvParser {
  private static final String[] HEADERS = {
    "Position", "Product Number", "Product", "Quantity", "Percent of Quantity",
    "Sale Amount", "Percent of Sale Amount", "Cost", "Percent of Gross Profit"
  };

  public List<LightspeedProductSale> parse(byte[] csv, LocalDate tradingDate) {
    List<LightspeedProductSale> out = new ArrayList<>();
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      for (CSVRecord r : CSVFormat.DEFAULT.builder().setHeader(HEADERS).setSkipHeaderRecord(true).build().parse(in)) {
        String name = r.get("Product");
        if (name == null || name.isBlank()) continue;
        out.add(new LightspeedProductSale(
            tradingDate, name, money(r.get("Quantity")), money(r.get("Sale Amount"))));
      }
      return out;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static BigDecimal money(String value) {
    if (value == null || value.isBlank()) return BigDecimal.ZERO;
    try {
      return new BigDecimal(value.replace("$", "").replace(",", "").trim());
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Bad amount '" + value + "'", e);
    }
  }
}
```

- [ ] **Step 2: Write the fixture + parser test**

Fixture `product_sales_sample.csv`:

```csv
"Position","Product Number","Product","Quantity","Percent of Quantity","Sale Amount","Percent of Sale Amount","Cost","Percent of Gross Profit"
1,"","Pint - Carlton Draught",1232,11,"$17,340.98",10,5327.29,69
2,"","Chicken Parma",391,3,"$11,418.06",7,0,100
3,"","New Kids Parma",7,0,"$84.00",0,0,100
```

```java
package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LightspeedProductCsvParserTest {

  @Test
  void parsesProductRowsAndStripsCurrency() throws Exception {
    byte[] csv = Files.readAllBytes(Path.of("src/test/resources/fixtures/lightspeed/product_sales_sample.csv"));

    var rows = new LightspeedProductCsvParser().parse(csv, LocalDate.of(2026, 9, 14));

    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).productName()).isEqualTo("Pint - Carlton Draught");
    assertThat(rows.get(0).quantitySold()).isEqualByComparingTo("1232");
    assertThat(rows.get(0).amount()).isEqualByComparingTo("17340.98");
  }
}
```

- [ ] **Step 3: Add the `POST /api/ingest/lightspeed-products` endpoint** (a new `@PostMapping` on `LightspeedIngestController`, reusing the same token check, delegating to a `LightspeedProductIngestService` that persists the envelope, extracts `attachment.data`, parses, aggregates by `ProductNameKey.normalize`, and records via `CanonicalProductSalesIngest`)

- [ ] **Step 4: Run + commit**

Run: `cd backend && ./gradlew test --tests '*LightspeedProductCsvParserTest' spotlessApply spotlessCheck`
Expected: PASS.

```bash
git add backend/src/main/java/com/goldys/platform/connectors/lightspeed backend/src/main/java/com/goldys/platform/api/LightspeedIngestController.java backend/src/test/java/com/goldys/platform/connectors/lightspeed backend/src/test/resources/fixtures/lightspeed
git commit -m "feat: add lightspeed per-product webhook and parser"
```

---

### Task 8: Frontend (per-product reconciliation screen) + integration test

**Files:**
- Modify: `frontend/lib/api/types.ts` (add `ProductReconciliationException` if not reusing `ReconciliationException`)
- Modify: `frontend/lib/api/live.ts` + `demo.ts` (add `listProductExceptions`, `getProductRecord`, `saveProductOverride`)
- Modify: `frontend/app/(app)/reconciliation/page.tsx` (a product-level list, or a tab)
- Modify: `frontend/lib/reconciliation-logic.ts` (reuse `deriveExceptions` — the product rows reuse the same `ReconciliationException` shape)
- Test: `frontend/lib/reconciliation-logic.test.ts` (extend for a product conflict + a source-only product)
- Create: `backend/src/test/java/com/goldys/platform/reconciliation/ProductSalesIntegrationTest.java` (Testcontainers: seed Lightspeed+CTB product rows → conflict surfaces → override resolves)

**Interfaces:**
- Consumes: the product endpoints (Task 5), `ReconciliationException`/`ReconciliationRecord` shapes.
- Produces: the frontend shows per-product conflicts; the integration test pins Review Focus lines 4 and 5.

- [ ] **Step 1: Extend `reconciliation-logic.test.ts`** for a source-only product (Review Focus line 3) — `needsDecision` already returns true when any source value is null, so add a fixture row with a single source and assert a `missing` exception.

- [ ] **Step 2: Add `listProductExceptions`/`getProductRecord`/`saveProductOverride` to `types.ts`, `live.ts`, `demo.ts`, and render them in the reconciliation screen (a per-product list keyed by `product`).

- [ ] **Step 3: Write `ProductSalesIntegrationTest`** (Testcontainers; pins Review Focus lines 4 and 5):

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class ProductSalesIntegrationTest {
  // @Autowired CanonicalProductSalesService productSales; ProductSalesReconciliationService reconciliation; ProductSalesOverrideService overrides; MockMvc mvc; JdbcTemplate jdbc;

  @BeforeEach void clean() { jdbc.update("truncate table canonical_product_sales, product_sales_override"); }

  @Test
  void perDayScopingAndOverrideResolution() {
    // seed: 14 Sep "garlic aioli" LIGHTSPEED 150/380.88 vs CTB 127/322.46 (conflict)
    // seed: 15 Sep "garlic aioli" LIGHTSPEED 150/380.88 vs CTB 150/380.88 (agreed, separate day)
    // assert conflicts() has exactly one entry for 14 Sep (not conflated with 15 Sep)
    // override 14 Sep "garlic aioli" -> CTB; assert conflicts() is now empty
  }
}
```

- [ ] **Step 4: Run + commit**

Run: `cd frontend && bun run typecheck && bun run lint && bun run test && bun run build` and `cd backend && ./gradlew compileTestJava spotlessCheck`
Expected: both pass.

```bash
git add frontend backend/src/test/java/com/goldys/platform/reconciliation
git commit -m "feat: per-product reconciliation frontend and integration test"
```

---

## Self-Review

- **Spec coverage:** §3 (matching) → Tasks 1, 3; §5 (strategy) → Task 1 (normalization), Task 3 (conflict/unmatched); §6.1 (Lightspeed) → Task 7; §6.2 (CTB) → Task 6; §7 (reconciliation + override) → Tasks 3, 4; §8 (API) → Task 5; §9 (testing) → Tasks 1–8; §10 (boundaries) → Global Constraints.
- **Placeholder scan:** Task 6 Step 3 and Task 7 Step 3 contain a brief "note for the implementer" on the CTB date handling and the Lightspeed ingest service — those are guidance, not placeholder code; every other code step is concrete. The Lightspeed per-product CSV shape is the "Sales By" export (known from the discovery); the final Looker webhook column names are a deployment-time swap, flagged in the spec §6.1.
- **Type consistency:** `ProductNameKey.normalize` used in Tasks 1, 6, 7; `ProductSalesInput`/`ProductSalesView`/`ProductSalesConflict`/`ProductSalesOverrideService.save` signatures are consistent across Tasks 2–5.
- **Review Focus:** line 1 → `ProductNameKeyTest`; line 2 → `classifiesAgreedConflictAndMissing`; line 3 → `classifiesAgreedConflictAndMissing` + `reconciliation-logic.test.ts`; line 4 → `ProductSalesIntegrationTest.perDayScopingAndOverrideResolution`; line 5 → same integration test.
