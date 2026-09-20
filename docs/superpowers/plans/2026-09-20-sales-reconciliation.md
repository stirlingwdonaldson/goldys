# Sales Reconciliation & Connectors (Phase 1, Slice 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pull daily sales from Lightspeed and CTB into the byte-faithful raw log, canonicalize them to per-source daily totals, reconcile them keyed by trading date, and expose the first real conflicts (e.g. the 13 Sep gap) through the reconciliation screen with a permission-gated manual override.

**Architecture:** Lightspeed's scheduled CSV is received by a **webhook endpoint** (push, one-time setup in Lightspeed Insights), and CTB is pulled by a `SourceConnector` adapter over authenticated HTTP — both stream source bytes through the existing ingestion ledger. A new bitemporal `CanonicalDailySales` entity stores one row per (trading date, source). A reconciliation service compares the per-date source totals, derives resolved views, and records append-only overrides. REST endpoints light up the already-built frontend reconciliation screen via `liveApi`.

**Tech Stack:** Java 25, Spring Boot 3.5, Gradle 9.5.0, PostgreSQL 16, Flyway, JUnit 5, Testcontainers, Apache Commons CSV (new).

**Spec:** `docs/superpowers/specs/2026-09-20-sales-reconciliation-design.md`
**Discovery:** `docs/connectors/source-access.md`

## Global Constraints

- Keep Gradle 9.5.0, Java 25, Spring Boot 3.5.0, PostgreSQL 16, Hibernate `ddl-auto: validate`, Flyway-owned schema (V1–V4 immutable; add V5+).
- Persist source bytes before parsing; raw log is append-only; canonical rows close (never overwrite); resolved views are derived.
- Every connector is one-way and implements the existing `SourceConnector` port; vendor types stay inside the adapter.
- Route override actions through the sole `PermissionService`; denied access is explicit, never silently filtered.
- Never guess matching tolerances or permission-matrix rows. This slice only does date-keyed matching (deterministic); line-item matching is explicitly out of scope.
- Never commit credentials, tokens, or unsanitized source data. Credentials are env vars (`LIGHTSPEED_*`, `CTB_*`).
- Integration tests use Testcontainers/PostgreSQL 16, never H2. Webhook pushes and HTTP pulls are NOT exercised in tests (fixtures only).

## Review Focus

1. The two sources' daily totals disagree (13 Sep: Lightspeed ~$6.7k higher) → surfaced as a day-level conflict with both values, never averaged or silently won. Pinned in Task 5.
2. CTB `revenueDate` is a .NET ticks integer → converted to a real date, not misread. Pinned in Task 2.
3. The Lightspeed CSV has quoted, multi-line `Notes` fields → the parser yields one row per transaction, not one per line. Pinned in Task 1.
4. An override is append-only and permission-gated; a denied user gets `NOT_PERMITTED`, never a partial write. Pinned in Task 6.
5. Retried identical source facts do not create duplicate canonical versions (idempotency). Pinned in Task 4.

---

## File Structure

```text
backend/src/main/java/com/goldys/platform/
  ingestion/port/            (existing: SourceConnector, IngestionSink, FetchedPayload)
  ingestion/webhook/         WebhookIngestService (persist pushed bytes -> raw log)
  api/                       LightspeedIngestController (POST /api/ingest/lightspeed)
  connectors/
    lightspeed/              LightspeedSalesFeedParser
    ctb/                     CtbConnector, CtbRevenueParser, CtbClient
  canonical/                 (existing: BitemporalEntity, CanonicalSaleItem, ...)
    CanonicalDailySales.java
    CanonicalDailySalesRepository.java
    CanonicalDailySalesService.java
  reconciliation/
    DailySalesReconciliationService.java
    DailySalesOverride.java
    DailySalesOverrideRepository.java
  api/
    ReconciliationController.java
    DashboardController.java
    ConnectorStatusController.java
    (DTOs)
backend/src/main/resources/db/migration/
  V5__daily_sales_reconciliation.sql
backend/src/test/java/com/goldys/platform/
  connectors/lightspeed/     LightspeedSalesFeedParserTest (fixture CSV)
  connectors/ctb/            CtbRevenueParserTest (fixture JSON)
  canonical/                 CanonicalDailySalesIntegrationTest
  reconciliation/            DailySalesReconciliationTest
  api/                       ReconciliationControllerTest
frontend/
  lib/api/live.ts            (point liveApi at the real endpoints)
```

---

### Task 1: Lightspeed webhook receiver and Sales Feed parser

**Files:**
- Modify: `backend/build.gradle` (add Apache Commons CSV)
- Create: `backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedSalesFeedParser.java`
- Create: `backend/src/main/java/com/goldys/platform/ingestion/webhook/WebhookIngestService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/LightspeedIngestController.java`
- Create: `backend/src/test/java/com/goldys/platform/connectors/lightspeed/LightspeedSalesFeedParserTest.java`

**Interfaces:**
- Consumes: `RawPayloadService.persist(...)`, `IngestionRunService` (existing foundation services).
- Produces: `POST /api/ingest/lightspeed` accepting the scheduled CSV (raw body or multipart — capture the exact shape from a test send), persisting bytes byte-faithfully (`FetchMethod.FILE_EXPORT`), then `LightspeedSalesFeedParser.parse(byte[] csv) -> List<LightspeedSale>` where `record LightspeedSale(String saleId, String saleNo, Instant saleDate, String siteName, String terminalName, String customerName, String operator, String notes, String linkedSaleId, BigDecimal netAmount, BigDecimal taxAmount, BigDecimal tip, BigDecimal total)`.

> **One-time setup (outside the platform):** in Lightspeed Insights
> (`https://insights.kounta.com/insights`), build a custom daily-sales report, then
> **Save and schedule** → destination **Webhook** → the platform's `/api/ingest/lightspeed`
> URL (reachable via the cloudflared tunnel). Configured once; no recurring scraper.
> This is a manual/bot one-off, not a scheduled job in the platform.

- [ ] **Step 1: Add dependency to `backend/build.gradle`**

```groovy
implementation 'org.apache.commons:commons-csv:1.11.0'
```

- [ ] **Step 2: Write the failing parser test (fixture CSV)**

Save the sanitized Sales Feed sample (the file the owner provided) as
`backend/src/test/resources/fixtures/lightspeed/sales_feed_sample.csv`, trimmed to a
few rows including one with an embedded newline in `Notes`. Then:

```java
class LightspeedSalesFeedParserTest {
  @Test
  void parsesRowsAndHandlesQuotedMultilineNotes() throws Exception {
    byte[] csv = Files.readAllBytes(Path.of(
        "src/test/resources/fixtures/lightspeed/sales_feed_sample.csv"));
    List<LightspeedSale> sales = new LightspeedSalesFeedParser().parse(csv);
    assertThat(sales).hasSize(3);          // 3 data rows, regardless of embedded newlines
    assertThat(sales.get(0).saleNo()).isEqualTo("SP-249 0920090517");
    assertThat(sales.get(0).netAmount()).isEqualByComparingTo("13.64");
    assertThat(sales.get(0).total()).isEqualByComparingTo("15");
    assertThat(sales.get(1).notes()).contains("me & u");   // the multi-line note
  }
}
```

- [ ] **Step 3: Run RED** — `./gradlew test --tests '*LightspeedSalesFeedParserTest'` (fails: class absent).

- [ ] **Step 4: Implement the parser**

```java
public class LightspeedSalesFeedParser {
  private static final CSVFormat FMT = CSVFormat.DEFAULT.builder()
      .setHeader().setSkipHeaderRecord(true).get();

  public List<LightspeedSale> parse(byte[] csv) {
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      List<LightspeedSale> out = new ArrayList<>();
      for (CSVRecord r : FMT.parse(in)) {
        out.add(new LightspeedSale(
            r.get("SaleID"), r.get("SaleNo"),
            Instant.parse(r.get("SaleDate").replace(" ", "T") + "Z"),
            r.get("SiteName"), r.get("TerminalName"), r.get("CustomerName"),
            r.get("Operator"), r.get("Notes"), r.get("LinkedSaleID"),
            new BigDecimal(r.get("Net Amount")), new BigDecimal(r.get("Tax Amount")),
            new BigDecimal(r.get("Tip")), new BigDecimal(r.get("Total"))));
      }
      return out;
    } catch (IOException e) { throw new UncheckedIOException(e); }
  }
}
```

- [ ] **Step 5: Implement the webhook receiver** — `LightspeedIngestController` +
  `WebhookIngestService`: accept the POST body (raw CSV or multipart; handle both),
  open/complete an `IngestionRun` (`FetchMethod.FILE_EXPORT`, `fetcherIdentity`
  `lightspeed-insights`), persist the bytes via `RawPayloadService`, then parse into
  canonical daily totals (delegating to Task 4's service).

- [ ] **Step 6: Run GREEN + commit**

```bash
cd backend && ./gradlew test --tests '*LightspeedSalesFeedParserTest' spotlessCheck
git add backend/build.gradle backend/src/main/java/com/goldys/platform/connectors/lightspeed backend/src/test/java/com/goldys/platform/connectors/lightspeed backend/src/test/resources/fixtures/lightspeed
git commit -m "feat: add lightspeed sales feed connector and parser"
```

---

### Task 2: CTB connector (authenticated HTTP) and revenue parser

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbClient.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbConnector.java`
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbRevenueParser.java`
- Create: `backend/src/test/java/com/goldys/platform/connectors/ctb/CtbRevenueParserTest.java`

**Interfaces:**
- Consumes: `SourceConnector`, `IngestionSink`; the CTB envelope `{"data":…,"message":{"IsSuccess":bool}}`.
- Produces: `CtbRevenueParser.parse(byte[] json) -> List<CtbRevenue>` where `record CtbRevenue(long revenueId, LocalDate revenueDate, String departmentName, BigDecimal kitchenRevenueTotal, BigDecimal totalSales, BigDecimal gstTotal, String outletName)`.

- [ ] **Step 1: Write the failing parser test (fixture JSON)**

Copy a sanitized slice of `revenues.json` to
`backend/src/test/resources/fixtures/ctb/revenues_sample.json`. Include a `.NET ticks`
date value (e.g. `639230904000000000` = 2026-08-24) and assert the conversion:

```java
class CtbRevenueParserTest {
  @Test
  void parsesEnvelopeAndConvertsTicksToLocalDate() throws Exception {
    byte[] json = Files.readAllBytes(Path.of("src/test/resources/fixtures/ctb/revenues_sample.json"));
    List<CtbRevenue> rows = new CtbRevenueParser().parse(json);
    assertThat(rows).isNotEmpty();
    assertThat(rows.get(0).revenueDate()).isEqualTo(LocalDate.of(2026, 8, 24));
    assertThat(rows.get(0).totalSales()).isEqualByComparingTo("4087.478");
    assertThat(rows.get(0).departmentName()).isEqualTo("Beverage");
  }
}
```

- [ ] **Step 2: Run RED** — `./gradlew test --tests '*CtbRevenueParserTest'` (fails).

- [ ] **Step 3: Implement the parser + ticks conversion**

```java
public class CtbRevenueParser {
  private static final long TICKS_PER_SECOND = 10_000_000L;
  private static final long EPOCH_TICKS = 621_355_968_000_000_000L; // 0001-01-01 -> 1970-01-01

  public List<CtbRevenue> parse(byte[] json) {
    JsonNode root = readTree(json);
    JsonNode data = root.get("data");
    if (data == null || !data.isArray()) return List.of();
    List<CtbRevenue> out = new ArrayList<>();
    for (JsonNode n : data) {
      long ticks = n.get("revenueDate").asLong();
      Instant instant = Instant.ofEpochSecond((ticks - EPOCH_TICKS) / TICKS_PER_SECOND);
      out.add(new CtbRevenue(
          n.get("revenueId").asLong(),
          instant.atZone(ZoneId.of("Australia/Melbourne")).toLocalDate(),
          n.get("businessDepartmentName").asText(),
          dec(n.get("kitchenRevenueTotal")), dec(n.get("totalSales")),
          dec(n.get("GSTTotal")), n.get("outletName").asText()));
    }
    return out;
  }
  private static BigDecimal dec(JsonNode n) {
    return n == null || n.isNull() ? null : n.decimalValue();
  }
}
```

- [ ] **Step 4: Implement `CtbClient` + `CtbConnector`** — `POST /Account/Login`
  (`userEmail`/`userPassword`) to get the session cookie, then paginate
  `Revenue/SearchRevenues` (`start`/`limit`) with `X-Requested-With: XMLHttpRequest`,
  streaming each page's raw JSON bytes to the sink (`FetchMethod.API`).

- [ ] **Step 5: Run GREEN + commit**

```bash
cd backend && ./gradlew test --tests '*CtbRevenueParserTest' spotlessCheck
git add backend/src/main/java/com/goldys/platform/connectors/ctb backend/src/test/java/com/goldys/platform/connectors/ctb backend/src/test/resources/fixtures/ctb
git commit -m "feat: add ctb revenue connector and parser"
```

---

### Task 3: Migration V5 — daily sales and override tables

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__daily_sales_reconciliation.sql`
- Create: `backend/src/test/java/com/goldys/platform/canonical/DailySalesSchemaTest.java`

**Interfaces:**
- Consumes: V4 canonical constraints + V2 raw records.
- Produces: `canonical_daily_sales` (bitemporal, per-source daily totals) and `daily_sales_override` (append-only).

- [ ] **Step 1: Write the failing schema test** — insert two source rows for the same
  date and assert the partial-unique index (`(trading_date, source_system) WHERE superseded_at IS NULL`)
  rejects a second current row for the same `(date, source)`.

- [ ] **Step 2: Run RED** — `./gradlew test --tests '*DailySalesSchemaTest'` (table absent).

- [ ] **Step 3: Create V5**

```sql
CREATE TABLE canonical_daily_sales (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    trading_date date NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    total_sales numeric(14,4) NOT NULL,
    gst_total numeric(14,4) NOT NULL,
    net_total numeric(14,4) NOT NULL,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX ux_daily_sales_current
    ON canonical_daily_sales (trading_date, source_system)
    WHERE superseded_at IS NULL;
CREATE INDEX ix_daily_sales_logical
    ON canonical_daily_sales (logical_entity_id) WHERE superseded_at IS NULL;

CREATE TABLE daily_sales_override (
    id uuid PRIMARY KEY,
    trading_date date NOT NULL,
    authoritative_source varchar(255) NOT NULL,
    reason text,
    actor_oidc_issuer varchar(512) NOT NULL,
    actor_oidc_subject varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX ux_daily_sales_override_current
    ON daily_sales_override (trading_date) WHERE superseded_at IS NULL;
```

- [ ] **Step 4: Run GREEN + commit**

```bash
cd backend && ./gradlew test --tests '*DailySalesSchemaTest' --tests '*DatabaseMigrationTest' spotlessCheck
git add backend/src/main/resources/db/migration/V5__daily_sales_reconciliation.sql backend/src/test/java/com/goldys/platform/canonical/DailySalesSchemaTest.java
git commit -m "feat: add daily sales and override schema"
```

---

### Task 4: CanonicalDailySales entity, repository, service

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalDailySales.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalDailySalesRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalDailySalesService.java`
- Create: `backend/src/test/java/com/goldys/platform/canonical/CanonicalDailySalesIntegrationTest.java`

**Interfaces:**
- Consumes: the V4 bitemporal base pattern (`BitemporalEntity`).
- Produces: `CanonicalDailySalesService.record(input)` — idempotent, corrected facts close the prior version and insert a successor with the same `logicalEntityId`.

- [ ] **Step 1: Write idempotency + as-of tests** (mirroring the foundation's
  `CanonicalVersionIntegrationTest`): recording an unchanged `(date, source)` returns the
  existing row; a corrected total closes the prior row and inserts a successor; `findKnownAt`
  reflects the past value.

- [ ] **Step 2: Run RED** — `./gradlew test --tests '*CanonicalDailySalesIntegrationTest'`.

- [ ] **Step 3: Implement** — entity maps V5; `record` locks the current
  `(date, source)` fact and either returns it unchanged or supersedes + inserts.

- [ ] **Step 4: Run GREEN + commit**

```bash
cd backend && ./gradlew test --tests 'com.goldys.platform.canonical.*' spotlessCheck
git add backend/src/main/java/com/goldys/platform/canonical backend/src/test/java/com/goldys/platform/canonical/CanonicalDailySalesIntegrationTest.java
git commit -m "feat: persist canonical daily sales versions"
```

---

### Task 5: Daily-total reconciliation (conflict detection + resolved views)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesReconciliationService.java`
- Create: `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesReconciliationTest.java`

**Interfaces:**
- Consumes: `CanonicalDailySalesRepository`.
- Produces:
  - `List<DailySalesConflict> conflicts()` — per date where source totals differ by more than one cent.
  - `Optional<DailySalesResolved> resolved(LocalDate date)` — the resolved total (override, or agreed value, or `unresolved`).

- [ ] **Step 1: Write the failing tests** — given Lightspeed `27650.66` vs CTB `20990.83`
  for 13 Sep, `conflicts()` returns one conflict with both values; a date where sources agree
  returns none; an override flips the resolved value.

- [ ] **Step 2: Run RED** — `./gradlew test --tests '*DailySalesReconciliationTest'`.

- [ ] **Step 3: Implement** — group current `CanonicalDailySales` rows by `trading_date`;
  compare `total_sales` with a one-cent rounding rule; derive resolved value from override
  else agreement else `unresolved`.

- [ ] **Step 4: Run GREEN + commit**

```bash
cd backend && ./gradlew test --tests '*DailySalesReconciliationTest' spotlessCheck
git add backend/src/main/java/com/goldys/platform/reconciliation/DailySalesReconciliationService.java backend/src/test/java/com/goldys/platform/reconciliation
git commit -m "feat: reconcile daily sales totals by trading date"
```

---

### Task 6: Manual override (append-only, permission-gated)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesOverride.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesOverrideRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesOverrideService.java`
- Create: `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesOverrideTest.java`

**Interfaces:**
- Consumes: `PermissionService`, `StaffProfileService`.
- Produces: `DailySalesOverrideService.save(actor, date, source, reason)` — requires
  `WRITE` on `reconciliation.sales`; supersedes any prior override for that date; returns the new override.

- [ ] **Step 1: Write the failing tests** — saving an override succeeds; a user without the
  permission gets `AccessDeniedException`; saving again supersedes the prior (append-only).

- [ ] **Step 2: Run RED** — `./gradlew test --tests '*DailySalesOverrideTest'`.

- [ ] **Step 3: Implement** — entity maps V5; service routes through
  `PermissionService.require(role, new ResourceKey("reconciliation.sales"), WRITE)`.

- [ ] **Step 4: Run GREEN + commit**

```bash
cd backend && ./gradlew test --tests '*DailySalesOverrideTest' spotlessCheck
git add backend/src/main/java/com/goldys/platform/reconciliation backend/src/test/java/com/goldys/platform/reconciliation/DailySalesOverrideTest.java
git commit -m "feat: add permission-gated daily sales override"
```

---

### Task 7: REST endpoints + DTOs

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/api/ReconciliationController.java`
- Create: `backend/src/main/java/com/goldys/platform/api/DashboardController.java`
- Create: `backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java`
- Create: `backend/src/test/java/com/goldys/platform/api/ReconciliationControllerTest.java`

**Interfaces:**
- Consumes: reconciliation + override + connector-run services; the `ApiErrorResponse` envelope.
- Produces (aligns with the frontend `liveApi` contract):
  - `GET /api/reconciliation/exceptions` → `ReconciliationException[]`
  - `GET /api/reconciliation/records/{date}` → `ReconciliationRecord`
  - `POST /api/reconciliation/records/{date}/override` → `OverrideResult`
  - `GET /api/dashboard/summary` → `DashboardSummary`
  - `GET /api/connectors` → `ConnectorStatus[]`
- Permission checks: reads require `READ` on `reconciliation.sales`; override requires `WRITE`.

- [ ] **Step 1: Write the failing controller test** (mock OIDC identity) — exceptions
  return the 13 Sep conflict; override without permission returns `NOT_PERMITTED`; override
  with permission returns `ok: true`.

- [ ] **Step 2: Run RED** — `./gradlew test --tests '*ReconciliationControllerTest'`.

- [ ] **Step 3: Implement** — map services to the DTOs; wire the error envelope.

- [ ] **Step 4: Run GREEN + commit**

```bash
cd backend && ./gradlew test --tests '*ReconciliationControllerTest' spotlessCheck build
git add backend/src/main/java/com/goldys/platform/api backend/src/test/java/com/goldys/platform/api
git commit -m "feat: expose reconciliation, dashboard, and connector endpoints"
```

---

### Task 8: Point the frontend `liveApi` at the real endpoints

**Files:**
- Modify: `frontend/lib/api/live.ts`

**Interfaces:**
- Consumes: the endpoints from Task 7.
- Produces: `liveApi` calling the real routes (the demo fixtures stay behind the `demo` flag, unchanged).

- [ ] **Step 1: Update `live.ts`** — set each method to the endpoint paths above (already
  largely correct from the demo build; adjust to the exact DTO field names if they drift).

- [ ] **Step 2: Verify** — `cd frontend && bun run typecheck && bun run lint && bun run test && bun run build`.

- [ ] **Step 3: Commit** — `git add frontend/lib/api/live.ts && git commit -m "feat: wire live api to reconciliation endpoints"`.

---

### Task 9: End-to-end verification (fixture trading week)

**Files:**
- Create: `backend/src/test/java/com/goldys/platform/reconciliation/TradingWeekAcceptanceTest.java`

**Interfaces:**
- Consumes: everything above, driven by the fixture dataset (sanitized).

- [ ] **Step 1: Write the acceptance test** — seed raw records for a sanitized week
  (Lightspeed transactions + CTB revenues) via the parsers; run ingestion → canonical →
  reconciliation; assert the 13 Sep conflict surfaces, an override resolves it, and a
  denied user cannot override.

- [ ] **Step 2: Run the full gate**

```bash
cd backend && ./gradlew test spotlessCheck build
cd frontend && bun run typecheck && bun run lint && bun run test && bun run build
```

- [ ] **Step 3: Commit** — `git add backend/src/test/java/com/goldys/platform/reconciliation/TradingWeekAcceptanceTest.java && git commit -m "test: acceptance coverage for a trading week"`.

---

## Checkpoint: Slice 1 Complete

- [ ] The Lightspeed webhook receiver and the CTB connector both persist byte-faithful source data (fixture-tested parsers).
- [ ] `CanonicalDailySales` is idempotent and bitemporal; V5 migrates on an empty DB.
- [ ] Date-keyed conflict detection surfaces the 13 Sep gap; overrides are append-only + permission-gated.
- [ ] REST endpoints return the contract the frontend already expects; `liveApi` points at them.
- [ ] Backend tests/build/Spotless and frontend typecheck/lint/test/build all pass.

## Follow-On (not in this plan)

- CTB `Sale` per-transaction `searchType` and Lightspeed line-item data (a second scheduled Insights report, or the REST API when unlocked) → the line-item matching slice (gated on paired samples + tolerance design).
- Outlet mapping (Lightspeed "Goldy's Tavern" ↔ CTB's two outlets).
- Scheduled runs (cron) for both connectors, and the connector-status dashboard wired to run history.
