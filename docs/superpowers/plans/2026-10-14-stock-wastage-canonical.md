# Stock & Wastage Canonicalisation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Formalise the CTB stocktake and wastage detail already in the raw ledger into bitemporal canonical entities (a new `canonical_stocktake` header, plus enriched `canonical_stock_count` and `canonical_wastage`) and wire raw → canonical so the data is no longer "unprocessed".

**Architecture:** Single-source (CTB) canonical layer only — no projection, resolved model, semantic query, API or tool this slice. Three bitemporal entities follow the existing `CanonicalInvoice` (header) and `CanonicalRecipeIngredient` (line) patterns; a flag-gated `ApplicationRunner` replays the existing raw ledger through new parsers into the entities, deduping headers to the latest per id and superseding on `(source_system, source_record_ref)`.

**Tech Stack:** Java 25, Spring Boot 3.5.0, Spring Data JPA, PostgreSQL 16 (Flyway migrations, `hibernate.ddl-auto: validate`), JUnit 5 + Mockito + AssertJ, Testcontainers for integration.

**Spec:** `docs/superpowers/specs/2026-10-14-stock-wastage-canonical-design.md`

## Global Constraints

- Java 25 / Spring Boot 3.5.0; committed Gradle wrapper **9.5.0** (never "fix" it down to 8.x). Schema changes only via a **new** Flyway migration, never `hibernate` DDL and never editing an applied migration.
- Every canonical entity extends `BitemporalEntity`; corrections **close + reinsert** (`supersede()`), never `UPDATE` in place; a unique-current index on `(source_system, source_record_ref) WHERE superseded_at IS NULL` per entity.
- Raw ledger is immutable; replay reads bytes via `RawLedgerQuery` (`payload_bytes`, never `parsed_payload`).
- CTB ids are `integer`; money/quantity are `numeric(14,4)` (Java `BigDecimal`). `is_partial`/`is_locked` are nullable `boolean` (Java `Boolean`).
- **No event publishing** this slice — no projector listens, so services mirror `CanonicalInvoiceService` (no `ApplicationEventPublisher`), not `CanonicalRecipeIngredientService`.
- Product identity is `ProductNameKey.normalize(stockDesc)` (public static, `com.goldys.platform.canonical`).
- No new package; everything lands in existing `canonical`, `connectors.ctb`, `ingestion`. `ArchitectureBoundariesTest` is unchanged.
- `hibernate validate` only runs under `./gradlew test` (integration, Testcontainers); `./gradlew fastTest` is the unit loop and does not load the context, so intermediate entity/migration states during Tasks 1–3 are fine.
- Atomic conventional commits; work on branch `feat/stock-wastage-canonical` (never `main`).

## Review Focus

The five failure modes the spec implies that no happy-path test would catch:

1. **A stocktake-detail line whose `stocktakeId` has no header** → the runner must throw (fail loud), never silently drop the line. → Task 5, `throwsWhenCountHasNoHeader`.
2. **Daily header re-pull means several raw header records per stocktake** → the runner must keep the *latest* per id, not the first, so the date/value is current. → Task 5, `dedupesHeadersToLatestByFetchedAt`.
3. **Literal `\u0000` in CTB strings** → parser strips it before any `varchar`/`text` write. → Task 4, `stripsNullEscapes` (both parsers).
4. **Exact-duplicate lines** (recipe 617 precedent) → parser collapses them so counts are not doubled. → Task 4, `dedupesExactDuplicateLines`.
5. **Line `id` is per-stocktake-scoped, not globally unique** → identity must anchor on `(stocktakeId, lineId)`; two stocktakes sharing a line id must be distinct facts. → Task 2, `lineIdentityIsScopedToStocktake`.

A sixth, lower-risk condition — a malformed `stocktakeDate`/`recordDate` — is pinned in Task 4 as `malformedDateThrowsSchemaMismatch`.

---

### Task 1: Migration (V67) + stocktake header entity

**Files:**
- Create: `backend/src/main/resources/db/migration/V67__stock_wastage_canonical.sql`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalStocktake.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/StocktakeInput.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalStocktakeRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalStocktakeService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalStocktakeIngest.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalStocktakeServiceTest.java`

**Interfaces:**
- Produces (consumed by Task 5, Task 6):
  - `StocktakeInput(String sourceSystem, int stocktakeId, LocalDate stocktakeDate, String description, String outletName, BigDecimal totalStockValue, Boolean isPartial, Boolean isLocked, UUID rawRecordId)`
  - `CanonicalStocktakeIngest.record(StocktakeInput input)` (public `@Service` facade)
  - package-private `CanonicalStocktake` with accessors `stocktakeId()`, `stocktakeDate()`, `description()`, `outletName()`, `totalStockValue()`, `isPartial()`, `isLocked()`

- [ ] **Step 1: Write the migration** `V67__stock_wastage_canonical.sql` — the whole schema in one file (all three tables; Tasks 2–3 only map columns):

```sql
CREATE TABLE canonical_stocktake (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    stocktake_id integer NOT NULL,
    stocktake_date date NOT NULL,
    description varchar(512),
    outlet_name varchar(255),
    total_stock_value numeric(14,4),
    is_partial boolean,
    is_locked boolean,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_stocktake_current_source_fact
    ON canonical_stocktake (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX idx_stocktake_id ON canonical_stocktake (stocktake_id);

ALTER TABLE canonical_stock_count
    ADD COLUMN stocktake_id integer NOT NULL,
    ADD COLUMN line_id integer NOT NULL,
    ADD COLUMN stock_id integer,
    ADD COLUMN stock_code varchar(255),
    ADD COLUMN unit_of_measurement_name varchar(64),
    ADD COLUMN unit_cost numeric(14,4),
    ADD COLUMN line_value numeric(14,4),
    ADD COLUMN supplier_name varchar(512),
    ADD COLUMN area_name varchar(255),
    ADD COLUMN recipe_id integer;
CREATE UNIQUE INDEX uq_stock_count_current_source_fact
    ON canonical_stock_count (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX idx_stock_count_stocktake ON canonical_stock_count (stocktake_id);

ALTER TABLE canonical_wastage
    ADD COLUMN wastage_record_id integer NOT NULL,
    ADD COLUMN line_id integer NOT NULL,
    ADD COLUMN cost numeric(14,4),
    ADD COLUMN supplier_name varchar(512),
    ADD COLUMN unit_of_measurement_name varchar(64),
    ADD COLUMN unit_size varchar(64),
    ADD COLUMN recipe_id integer;
CREATE UNIQUE INDEX uq_wastage_current_source_fact
    ON canonical_wastage (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX idx_wastage_record ON canonical_wastage (wastage_record_id);
```

(The two `ALTER`s target empty tables — `canonical_stock_count`/`canonical_wastage` are 0 rows — so adding `NOT NULL` columns is safe.)

- [ ] **Step 2: Write `StocktakeInput`** — the public record with the exact component order and types in the Interfaces block above.

- [ ] **Step 3: Write `CanonicalStocktake`** — `@Entity @Table(name = "canonical_stocktake")`, package-private, `extends BitemporalEntity`. Fields (all `updatable = false`): `stocktakeId` (`@Column(name="stocktake_id", nullable=false)` int), `stocktakeDate` (`date`, nullable=false), `description` (512), `outletName` (255), `totalStockValue` (`numeric(14,4)`), `isPartial`/`isLocked` (nullable `Boolean`). Provide `create(...)` and `sameFact(StocktakeInput)` (compare the seven fact fields, `sameAmount` helper for `BigDecimal`), mirroring `CanonicalInvoice`.

- [ ] **Step 4: Write `CanonicalStocktakeRepository`** — package-private, `extends BitemporalRepository<CanonicalStocktake>`, with `@Lock(PESSIMISTIC_WRITE)` `Optional<CanonicalStocktake> lockCurrentSourceFact(String source, String ref)` (JPQL on `sourceRecordRef`/`sourceSystem`/`supersededAt is null`), mirroring `CanonicalInvoiceRepository`.

- [ ] **Step 5: Write the failing test** `CanonicalStocktakeServiceTest` (Mockito, mirror `CanonicalRecipeIngredientServiceTest` but **no** `ApplicationEventPublisher`):

```java
// recordsNewFactAndSetsFields — lockCurrentSourceFact→empty, save→arg;
//   assert saved.stocktakeId()==1, saved.stocktakeDate()==LocalDate.parse("2026-10-05")
// supersedesOnChangedFact — record v1 (date 2026-10-05), re-record changed (isLocked true→false);
//   assert saved.supersededAt()!=null, verify saveAndFlush, verify save twice
// sameFactIsANoop — record twice identical; assert second isSameAs(first), verify save once
// logicalIdIsDeterministic — assert saved.logicalEntityId()
//   == UUID.nameUUIDFromBytes("stocktake:1".getBytes(UTF_8))
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.CanonicalStocktakeServiceTest'`
Expected: FAIL (compilation error — `CanonicalStocktakeService` does not exist).

- [ ] **Step 7: Implement `CanonicalStocktakeService` + `CanonicalStocktakeIngest`**

`CanonicalStocktakeService` (package-private `@Service`, no publisher): `CanonicalStocktake record(StocktakeInput)` → `recordAt(input, Clock.systemUTC().instant())`; `recordAt` computes `ref = "stocktake:" + stocktakeId`, `logicalId = UUID.nameUUIDFromBytes(ref.getBytes(UTF_8))`, `lockCurrentSourceFact` → `sameFact` no-op / else `supersede` + insert. `CanonicalStocktakeIngest` (public `@Service`) delegates `record(input)`.

- [ ] **Step 8: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.CanonicalStocktakeServiceTest'`
Expected: PASS.

- [ ] **Step 9: Compile + fast lane**

Run: `cd backend && ./gradlew compileJava fastTest`
Expected: green.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/db/migration/V67__stock_wastage_canonical.sql backend/src/main/java/com/goldys/platform/canonical/CanonicalStocktake.java backend/src/main/java/com/goldys/platform/canonical/StocktakeInput.java backend/src/main/java/com/goldys/platform/canonical/CanonicalStocktakeRepository.java backend/src/main/java/com/goldys/platform/canonical/CanonicalStocktakeService.java backend/src/main/java/com/goldys/platform/canonical/CanonicalStocktakeIngest.java backend/src/test/java/com/goldys/platform/canonical/CanonicalStocktakeServiceTest.java
git commit -m "feat: canonical stocktake header entity (V67)"
```

---

### Task 2: Enrich `canonical_stock_count` + ingest

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalStockCount.java` (add fields + `sameFact` + extend `create`)
- Create: `backend/src/main/java/com/goldys/platform/canonical/StockCountInput.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalStockCountRepository.java` (add `lockCurrentSourceFact`)
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalStockCountService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalStockCountIngest.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalStockCountServiceTest.java`

**Interfaces:**
- Consumes: `BitemporalEntity`, `BitemporalRepository` (existing).
- Produces (consumed by Task 4, Task 5, Task 6):
  - `StockCountInput(String sourceSystem, int stocktakeId, int lineId, LocalDate countedDate, String productNameKey, BigDecimal quantityOnHand, Integer stockId, String stockCode, String unitOfMeasurementName, BigDecimal unitCost, BigDecimal lineValue, String supplierName, String areaName, Integer recipeId, UUID rawRecordId)`
  - `CanonicalStockCountIngest.record(StockCountInput)` and `recordAll(List<StockCountInput>)`

- [ ] **Step 1: Write the failing test** `CanonicalStockCountServiceTest` (mirror Task 1's service test, plus `recordAllRecordsAllFacts` — verify `save` called once per input — and `lineIdentityIsScopedToStocktake`: record `(stocktakeId=1, lineId=100)` then `(stocktakeId=2, lineId=100)`; assert two distinct `save` calls, distinct `sourceRecordRef` `"stocktake:1:100"` vs `"stocktake:2:100"`, distinct `logicalEntityId`).

```java
// recordsNewFactAndSetsFields
// supersedesOnChangedFact — change quantityOnHand 5 → 6 on same (stocktake,line)
// sameFactIsANoop
// logicalIdIsDeterministic — "stock-count:1:100" → nameUUIDFromBytes
// lineIdentityIsScopedToStocktake — (1,100) and (2,100) are distinct facts
// recordAllRecordsAllFacts
```

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.CanonicalStockCountServiceTest'`
Expected: FAIL.

- [ ] **Step 3: Write `StockCountInput`** — record with the exact component order/types in Interfaces.

- [ ] **Step 4: Extend `CanonicalStockCount`** — add fields (all `updatable = false`): `stocktakeId` (`integer`, NOT NULL), `lineId` (`integer`, NOT NULL), `stockId` (`Integer`), `stockCode` (255), `unitOfMeasurementName` (64), `unitCost` (`numeric(14,4)`), `lineValue` (`numeric(14,4)`), `supplierName` (512), `areaName` (255), `recipeId` (`Integer`). Keep existing `countedDate`, `productNameKey`, `quantityOnHand`. Extend `create(...)` and add `sameFact(StockCountInput)` + accessors.

- [ ] **Step 5: Extend `CanonicalStockCountRepository`** — add `lockCurrentSourceFact(String source, String ref)`.

- [ ] **Step 6: Implement `CanonicalStockCountService` + `CanonicalStockCountIngest`**

`ref = "stocktake:" + stocktakeId + ":" + lineId`; `logicalId = nameUUIDFromBytes("stock-count:" + stocktakeId + ":" + lineId)`. `recordAll` loops `recordAt` in one `@Transactional`, no event.

- [ ] **Step 7: Run to verify pass**

Run: `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.CanonicalStockCountServiceTest'`
Expected: PASS.

- [ ] **Step 8: Fast lane + commit**

Run: `cd backend && ./gradlew compileJava fastTest` then:

```bash
git add backend/src/main/java/com/goldys/platform/canonical/ backend/src/test/java/com/goldys/platform/canonical/CanonicalStockCountServiceTest.java
git commit -m "feat: canonical stock count entity + ingest"
```

---

### Task 3: Enrich `canonical_wastage` + ingest

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalWastage.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/WastageInput.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalWastageRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalWastageService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalWastageIngest.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalWastageServiceTest.java`

**Interfaces:**
- Produces (consumed by Task 4, Task 5, Task 6):
  - `WastageInput(String sourceSystem, int wastageRecordId, int lineId, LocalDate wastageDate, String productNameKey, BigDecimal quantity, BigDecimal cost, String reason, String supplierName, String unitOfMeasurementName, String unitSize, Integer recipeId, UUID rawRecordId)`
  - `CanonicalWastageIngest.record(WastageInput)` and `recordAll(List<WastageInput>)`

- [ ] **Step 1: Write the failing test** `CanonicalWastageServiceTest` (mirror Task 2, but for wastage: `recordsNewFactAndSetsFields`, `supersedesOnChangedFact` (cost 12.00 → 13.00), `sameFactIsANoop`, `logicalIdIsDeterministic` (`"wastage:7:359"`), `recordAllRecordsAllFacts`).

- [ ] **Step 2: Run to verify failure** — `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.CanonicalWastageServiceTest'` → FAIL.

- [ ] **Step 3: Write `WastageInput`** — record with the exact component order/types in Interfaces.

- [ ] **Step 4: Extend `CanonicalWastage`** — add `wastageRecordId` (`integer`, NOT NULL), `lineId` (`integer`, NOT NULL), `cost` (`numeric(14,4)`), `supplierName` (512), `unitOfMeasurementName` (64), `unitSize` (64), `recipeId` (`Integer`). Keep `wastageDate`, `productNameKey`, `quantity`, `reason`. Extend `create(...)` + `sameFact(WastageInput)` + accessors.

- [ ] **Step 5: Extend `CanonicalWastageRepository`** — add `lockCurrentSourceFact`.

- [ ] **Step 6: Implement `CanonicalWastageService` + `CanonicalWastageIngest`**

`ref = "wastage:" + wastageRecordId + ":" + lineId`; `logicalId = nameUUIDFromBytes("wastage:" + wastageRecordId + ":" + lineId)`.

- [ ] **Step 7: Run to verify pass** — `cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.CanonicalWastageServiceTest'` → PASS.

- [ ] **Step 8: Fast lane + commit**

Run: `cd backend && ./gradlew compileJava fastTest` then:

```bash
git add backend/src/main/java/com/goldys/platform/canonical/ backend/src/test/java/com/goldys/platform/canonical/CanonicalWastageServiceTest.java
git commit -m "feat: canonical wastage entity + ingest"
```

---

### Task 4: CTB parsers (stocktake + wastage)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbStocktakeParser.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbWastageParser.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/CtbStocktakeParserTest.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/CtbWastageParserTest.java`

**Interfaces:**
- Consumes: `StocktakeInput`, `StockCountInput`, `WastageInput`, `ProductNameKey.normalize(String)`.
- Produces (consumed by Task 5):
  - `CtbStocktakeParser.parseStocktakes(byte[] json, UUID rawRecordId) -> List<StocktakeInput>`
  - `CtbStocktakeParser.parseStocktakeLines(byte[] json, UUID rawRecordId, int stocktakeId, LocalDate countedDate) -> List<StockCountInput>`
  - `CtbWastageParser.parse(byte[] json, UUID rawRecordId) -> List<WastageInput>`

Both `@Component`, use `ObjectMapper`, a defensive `integer`/`decimal`/`bool`/`sanitize` (strip `\u0000`) helper set, and collapse exact duplicates via `LinkedHashSet` (mirror `CtbRecipeIngredientsParser`).

- [ ] **Step 1: Write the failing tests**

`CtbStocktakeParserTest`:
```java
// parsesHeaderFields — data[0] stocktakeId=1, stocktakeDate="2026-10-05", stocktakeDesc, outletName,
//   totalStockValue=1234.50, isPartial=false, isLockStocktake=true → assert all mapped
// malformedDateThrowsSchemaMismatch — stocktakeDate="not-a-date" → ConnectorFetchException, failureType CONNECTOR_SCHEMA_MISMATCH
// parsesLineFields — data[0] id=100, stockId=42, stockCode, stockDesc, qtyCount=5, stocktakeDetailCost=2.50,
//   subTotal=12.50, supplierName, stocktakeAreaName, recipeId → assert StockCountInput mapped, countedDate == arg
// normalizesProductName — stockDesc=" Chicken  Breast " → productNameKey == ProductNameKey.normalize(...)
// nullQtyCountIsZero — qtyCount missing → quantityOnHand == 0
// stripsNullEscapes — stockDesc/stockCode with \u0000 → no control char
// dedupesExactDuplicateLines — two identical data rows → size 1
// nonJsonThrowsSchemaMismatch
// missingDataReturnsEmpty
```

`CtbWastageParserTest`:
```java
// parsesWastageFields — wastageRecordId=7, wastageRecordDetailId=359, recordDate="2026-10-05",
//   stockDesc, quantity=3, cost=12.00, reason, supplierName, unitOfMeasurementName, unitSize, recipeId → mapped
// normalizesProductName
// stripsNullEscapes
// malformedDateThrowsSchemaMismatch — recordDate="bad" → throw
// nonJsonThrowsSchemaMismatch
```

- [ ] **Step 2: Run to verify failure** — `cd backend && ./gradlew test --tests 'com.goldys.platform.connectors.ctb.CtbStocktakeParserTest' --tests 'com.goldys.platform.connectors.ctb.CtbWastageParserTest'` → FAIL.

- [ ] **Step 3: Implement `CtbStocktakeParser`**

`parseStocktakes`: read `data` array; per row build `StocktakeInput("CTB", int("stocktakeId"), date("stocktakeDate") [throw on malformed], sanitize("stocktakeDesc"), sanitize("outletName"), decimal("totalStockValue"), bool("isPartial"), bool("isLockStocktake"), rawRecordId)`. `parseStocktakeLines`: per row `StockCountInput("CTB", stocktakeId, int("id"), countedDate, ProductNameKey.normalize(sanitize("stockDesc")), nz(decimal("qtyCount")), integer("stockId"), sanitize("stockCode"), sanitize("stockUnitOfMeasurementName"), decimal("stocktakeDetailCost"), decimal("subTotal"), sanitize("supplierName"), sanitize("stocktakeAreaName"), integer("recipeId"), rawRecordId)`; dedupe via `LinkedHashSet`.

- [ ] **Step 4: Implement `CtbWastageParser`**

Per row: `WastageInput("CTB", int("wastageRecordId"), int("wastageRecordDetailId"), date("recordDate") [throw on malformed], ProductNameKey.normalize(sanitize("stockDesc")), decimal("quantity"), decimal("cost"), sanitize("reason"), sanitize("supplierName"), sanitize("unitOfMeasurementName"), sanitize("unitSize"), integer("recipeId"), rawRecordId)`; dedupe.

- [ ] **Step 5: Run to verify pass** — same command as Step 2 → PASS.

- [ ] **Step 6: Fast lane + commit**

Run: `cd backend && ./gradlew fastTest` then:

```bash
git add backend/src/main/java/com/goldys/platform/connectors/ctb/CtbStocktakeParser.java backend/src/main/java/com/goldys/platform/connectors/ctb/CtbWastageParser.java backend/src/test/java/com/goldys/platform/connectors/ctb/
git commit -m "feat: CTB stocktake & wastage parsers"
```

---

### Task 5: `RawLedgerQuery` ordered finder + canonicalization runner

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/IngestionRepositories.java` (add ordered finder to `RawRecordRepository`)
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/RawLedgerQuery.java` (add `rawPayloadsForFetcherWithFetchedAt` + record)
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/StockWastageCanonicalizationRunner.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/StockWastageCanonicalizationRunnerTest.java`

**Interfaces:**
- Consumes: `RawLedgerQuery`, `CtbStocktakeParser`, `CtbWastageParser`, `CanonicalStocktakeIngest`, `CanonicalStockCountIngest`, `CanonicalWastageIngest`, `ConnectorFetchException`.
- Produces: the `ApplicationRunner` bean (flag `app.stock-wastage-canonicalize=true`), and `RawLedgerQuery.rawPayloadsForFetcherWithFetchedAt(String sourceSystem, String fetcherIdentity) -> List<RawPayloadWithRefAndTime>` where `RawPayloadWithRefAndTime(UUID id, byte[] bytes, String sourceRecordRef, Instant fetchedAt)`.

- [ ] **Step 1: Add the ordered finder**

In `IngestionRepositories.RawRecordRepository`, add:
```java
List<RawRecord> findBySourceSystemAndFetcherIdentityOrderByFetchedAtAsc(String sourceSystem, String fetcherIdentity);
```

- [ ] **Step 2: Add `RawLedgerQuery.rawPayloadsForFetcherWithFetchedAt`** — map the finder to `new RawPayloadWithRefAndTime(r.id(), r.payloadBytes(), r.sourceRecordRef(), r.fetchedAt())`, plus the `RawPayloadWithRefAndTime` record.

- [ ] **Step 3: Write the failing test** `StockWastageCanonicalizationRunnerTest` (mock `RawLedgerQuery` and the three ingests, real parsers, mirror `RecipeIngredientCanonicalizationRunnerTest`):

```java
// replaysStocktakesAndCountsAndWastage — one header record (stocktakeId=1, date 2026-10-05),
//   one detail record (ref "stocktake:1", data[0] id=100, qtyCount=5), one wastage record
//   (ref "wastage:7", data[0] wastageRecordDetailId=359, quantity=3);
//   capture stocktakeIngest.record(...) → assert stocktakeId 1;
//   capture stockCountIngest.recordAll(...) → assert one StockCountInput, countedDate 2026-10-05;
//   capture wastageIngest.recordAll(...) → assert one WastageInput
// dedupesHeadersToLatestByFetchedAt — two header records same stocktakeId=1, fetchedAt t1 (date 2026-10-05)
//   and t2>t1 (date 2026-10-06); assert stocktakeIngest.record called once with stocktakeDate 2026-10-06
// throwsWhenCountHasNoHeader — detail ref "stocktake:999" not in headers → assertThatThrownBy
//   ConnectorFetchException, and verify stockCountIngest.recordAll never called
// skipsUnparseableDetailRef — detail ref null → no throw, no count recorded
```

- [ ] **Step 4: Run to verify failure** — `cd backend && ./gradlew test --tests 'com.goldys.platform.connectors.ctb.StockWastageCanonicalizationRunnerTest'` → FAIL.

- [ ] **Step 5: Implement `StockWastageCanonicalizationRunner`**

`@Component @Order(Ordered.HIGHEST_PRECEDENCE) @ConditionalOnProperty(name="app.stock-wastage-canonicalize", havingValue="true") implements ApplicationRunner`. In `run`:
1. `Map<Integer, StocktakeInput> latest = new HashMap<>()`; for each `rawPayloadsForFetcherWithFetchedAt("CTB","ctb-stocktakes")`, parse headers and `put(stocktakeId, input)` (ascending order → last wins = latest).
2. Record each latest header via `stocktakeIngest.record(...)`; build `Map<Integer, LocalDate> dates` from them.
3. For each `rawPayloadsForFetcherWithRef("CTB","ctb-stocktake-detail")`: derive `stocktakeId` from `source_record_ref` (`"stocktake:<id>"`; skip + `log.warn` if unparseable); if `!dates.containsKey(stocktakeId)` collect into `missing`; else parse lines and accumulate `counts`.
4. For each `rawPayloadsForFetcherWithRef("CTB","ctb-wastage-detail")`: parse and accumulate `wastages`.
5. If `missing` is non-empty → `throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "stocktake detail missing header date for stocktakes: " + missing)` (fail fast, before any count write).
6. Else `stockCountIngest.recordAll(counts)` then `wastageIngest.recordAll(wastages)`.

- [ ] **Step 6: Run to verify pass** — same command as Step 4 → PASS.

- [ ] **Step 7: Fast lane + commit**

Run: `cd backend && ./gradlew fastTest` then:

```bash
git add backend/src/main/java/com/goldys/platform/ingestion/IngestionRepositories.java backend/src/main/java/com/goldys/platform/ingestion/RawLedgerQuery.java backend/src/main/java/com/goldys/platform/connectors/ctb/StockWastageCanonicalizationRunner.java backend/src/test/java/com/goldys/platform/connectors/ctb/StockWastageCanonicalizationRunnerTest.java
git commit -m "feat: stock & wastage canonicalization runner"
```

---

### Task 6: Pin identity in docs + full gate

**Files:**
- Modify: `docs/connectors/matching-and-identity.md` (replace the two "identity deferred" entries with the finalized identity/matching below)

**Interfaces:** none (documentation only).

- [ ] **Step 1: Update `matching-and-identity.md`**

Replace the `Stock count` and `Wastage` sections with finalized text, and add a `Stocktake header` entry:

| entity | source | source_record_ref | logical identity |
|---|---|---|---|
| Stocktake header | CTB (`ctb-stocktakes`) | `stocktake:<id>` | `nameUUIDFromBytes("stocktake:"+id)` |
| Stock count line | CTB (`ctb-stocktake-detail`) | `stocktake:<id>:<lineId>` | `nameUUIDFromBytes("stock-count:"+id+":"+lineId)` |
| Wastage line | CTB (`ctb-wastage-detail`) | `wastage:<recordId>:<detailId>` | `nameUUIDFromBytes("wastage:"+recordId+":"+detailId)` |

Note explicitly: `stocktakeDetailId` is not a line id (4 distinct values); the line id is the per-stocktake `id` field, so the parent `stocktakeId` anchors identity. Wastage has no stock code — product identity is `product_name_key` from `stockDesc`. All single-source `CTB`, deterministic, re-record supersedes on `(source_system, source_record_ref)`.

- [ ] **Step 2: Full gate**

Run: `cd backend && ./gradlew test spotlessCheck`
Expected: green. (This runs `hibernate validate` + Testcontainers integration, proving the migration matches the three entities.)

- [ ] **Step 3: Commit**

```bash
git add docs/connectors/matching-and-identity.md
git commit -m "docs: pin stock/wastage identity and matching"
```

---

## Post-plan notes for the executor

- The flag `app.stock-wastage-canonicalize=true` is **off by default**; the runner replays the existing ledger once per flagged deploy and is idempotent (supersede-as-noop). Unset it after the first successful run — the detail backfill is one-off, so re-running only re-confirms the same facts.
- No permission resource is seeded this slice (no API/tool), so no `ALL × OWNER` grant is required — consistent with `docs/prd.md`'s open questions.
- `resolved_inventory_day.wastage`/`stock_on_hand` remain null by design; the projection slice (a later spec) will populate them from these canonical rows.
