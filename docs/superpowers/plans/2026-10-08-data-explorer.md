# Data Explorer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A read-only, owner-gated "data explorer" that browses all three pipeline layers — raw records, canonical entities, and resolved views — from a single `/data` screen, with the canonical/resolved layers enumerated from a registry so future domains surface automatically.

**Architecture:** Follows the existing provenance/trust seam exactly so the ArchUnit `api → canonical/raw` rule stays green: a leaf `semantic.DataExplorerQuery` interface implemented in `reconciliation`, reached by `api` only through an `application` authorization service. Raw reads go through a new `ingestion.RawRecordBrowseQuery` facade; canonical reads through a new `canonical.CanonicalBrowseQuery` facade; resolved reads through the existing `reconciliation` resolved repositories. Rows are generic `{id, columns}` maps so the frontend renders any entity without knowing it.

**Tech Stack:** Java 25 / Spring Boot 3.5 (Spring Data JPA `Pageable`), Gradle 9.5 wrapper, PostgreSQL 16 (Testcontainers), JUnit 5 + Mockito + AssertJ + ArchUnit. Frontend: Next.js App Router, TypeScript, Tailwind, the existing `lib/api` `live`+`demo` split and shadcn/ui components.

**Spec:** `docs/superpowers/specs/2026-10-08-data-explorer-design.md`.

## Global Constraints

- Read-only: no write/edit/override/export from the explorer.
- `connectors` READ-gated (owner/admin); non-owner gets `NOT_PERMITTED`, never a filtered list.
- ArchUnit: the `api` package must not depend on `canonical` or `ingestion.RawLedgerQuery`. `semantic` must stay a leaf (no platform deps). No new ArchUnit rule is added.
- Raw payloads are fetched only by id; the list path returns metadata, never bytes.
- Schema changes only via new Flyway migrations (none needed here — no new tables).
- Integration tests against Testcontainers, never H2.
- `./gradlew test spotlessCheck build` green at each task; `bun run lint` + `bun run build` for frontend tasks.

## Review Focus

1. **The ArchUnit seam** — the whole design hinges on `api` never importing `canonical` or `RawLedgerQuery`. A stray import in the controller silently fails `apiDoesNotReadCanonical`. (Task 5 runs the arch test.)
2. **Raw list must not return payload bytes** — `listRaw` returns metadata only; `rawDetail` returns bytes. A list that loads payloads defeats the pagination. (Task 2.)
3. **Generic-row ordering/column determinism** — the bitemporal columns must come first and in a fixed order; dates ISO; nulls omitted. A non-deterministic map makes the UI columns jump. (Task 3.)
4. **Placeholder entities** — `shift`/`sale_item` (and `stock_count`/`wastage`, which have models but no source) must render an explicit empty/placeholder state, not "no data". (Tasks 4, 7.)
5. **Unknown entity/domain id → 404**, not a 500 or an empty page. (Task 5.)

---

### Task 1: `semantic` read records + `DataExplorerQuery` interface

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/DataExplorerQuery.java`
- Create: one top-level record each in `backend/src/main/java/com/goldys/platform/semantic/` — `RawRecordSummary.java`, `RawRecordDetail.java`, `RawFilter.java`, `EntityDescriptor.java`, `GenericRow.java`, `DataPage.java`

**Interfaces:**
- Produces (consumed by Tasks 2–8):
  - `DataExplorerQuery.listRaw(RawFilter, int page, int size) -> DataPage<RawRecordSummary>`
  - `DataExplorerQuery.rawDetail(UUID id) -> RawRecordDetail`
  - `DataExplorerQuery.canonicalEntities() -> List<EntityDescriptor>`
  - `DataExplorerQuery.canonicalRows(String entityId, int page, int size) -> DataPage<GenericRow>`
  - `DataExplorerQuery.resolvedDomains() -> List<EntityDescriptor>`
  - `DataExplorerQuery.resolvedRows(String domainId, int page, int size) -> DataPage<GenericRow>`
  - `record RawRecordSummary(UUID id, String sourceSystem, String fetcherIdentity, String fetchMethod, String contentType, String characterEncoding, Instant fetchedAt, long byteLength, String sha256)`
  - `record RawRecordDetail(RawRecordSummary summary, String payload, boolean isJson, String sha256)`
  - `record RawFilter(String sourceSystem, String fetcherIdentity, String fetchMethod, Instant from, Instant to)` (all nullable)
  - `record EntityDescriptor(String id, String label, boolean placeholder)`
  - `record GenericRow(String id, Map<String, String> columns)`
  - `record DataPage<T>(List<T> items, long total, int page, int size)`

- [ ] **Step 1: Write the interface and records** (leaf — no imports beyond `java.util`, `java.time`, `java.util.UUID`).

```java
package com.goldys.platform.semantic;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read port for the owner's data explorer: raw, canonical, and resolved layers. */
public interface DataExplorerQuery {
  DataPage<RawRecordSummary> listRaw(RawFilter filter, int page, int size);
  RawRecordDetail rawDetail(UUID id);
  List<EntityDescriptor> canonicalEntities();
  DataPage<GenericRow> canonicalRows(String entityId, int page, int size);
  List<EntityDescriptor> resolvedDomains();
  DataPage<GenericRow> resolvedRows(String domainId, int page, int size);
}
```

Each record is its own top-level `public record` file in `com.goldys.platform.semantic` (matching the existing `TrustSummary`/`Provenance` style), with imports `java.time.Instant`, `java.util.List`, `java.util.Map`, `java.util.UUID`:

```java
public record RawRecordSummary(
    UUID id,
    String sourceSystem,
    String fetcherIdentity,
    String fetchMethod,
    String contentType,
    String characterEncoding,
    Instant fetchedAt,
    long byteLength,
    String sha256) {}

public record RawRecordDetail(RawRecordSummary summary, String payload, boolean isJson, String sha256) {}

public record RawFilter(
    String sourceSystem, String fetcherIdentity, String fetchMethod, Instant from, Instant to) {}

public record EntityDescriptor(String id, String label, boolean placeholder) {}

public record GenericRow(String id, Map<String, String> columns) {}

public record DataPage<T>(List<T> items, long total, int page, int size) {}
```

- [ ] **Step 2: Compile** (`./gradlew compileJava`). Expected: success; no tests yet.
- [ ] **Step 3: Commit.**

---

### Task 2: `ingestion.RawRecordBrowseQuery` + paged repository query

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/ingestion/IngestionRepositories.java` (add a paged query to `RawRecordRepository`)
- Create: `backend/src/main/java/com/goldys/platform/ingestion/RawRecordBrowseQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/ingestion/RawRecordBrowseQueryIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 records.
- Produces: `RawRecordBrowseQuery.list(filter, page, size) -> DataPage<RawRecordSummary>` and `byId(UUID) -> RawRecordDetail`; returns `semantic` records so `reconciliation` never touches the package-private `RawRecord`.

- [ ] **Step 1: Add the paged query.** In `RawRecordRepository` (package-private, in `IngestionRepositories.java`):

```java
@Query(
    """
    select r from RawRecord r
    where (:source is null or r.sourceSystem = :source)
      and (:fetcher is null or r.fetcherIdentity = :fetcher)
      and (:method is null or r.fetchMethod = :method)
      and (:from is null or r.fetchedAt >= :from)
      and (:to is null or r.fetchedAt <= :to)
    order by r.fetchedAt desc
    """)
org.springframework.data.domain.Page<RawRecord> findPage(
    @org.springframework.data.repository.query.Param("source") String source,
    @org.springframework.data.repository.query.Param("fetcher") String fetcher,
    @org.springframework.data.repository.query.Param("method") FetchMethod method,
    @org.springframework.data.repository.query.Param("from") Instant from,
    @org.springframework.data.repository.query.Param("to") Instant to,
    org.springframework.data.domain.Pageable pageable);
```

(Add imports `java.time.Instant` and `org.springframework.data.domain.Page`/`Pageable` to the file.)

- [ ] **Step 2: Write the facade.**

```java
package com.goldys.platform.ingestion;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.RawFilter;
import com.goldys.platform.semantic.RawRecordDetail;
import com.goldys.platform.semantic.RawRecordSummary;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Read-only browse access to the raw ledger for the data explorer (metadata list + payload by id). */
@Service
public class RawRecordBrowseQuery {
  private static final int MAX_PAGE_SIZE = 200;
  private final RawRecordRepository records;

  public RawRecordBrowseQuery(RawRecordRepository records) {
    this.records = records;
  }

  public DataPage<RawRecordSummary> list(RawFilter filter, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    var result =
        records.findPage(
            filter.sourceSystem(),
            filter.fetcherIdentity(),
            fetchMethod(filter),
            filter.from(),
            filter.to(),
            PageRequest.of(Math.max(page, 0), safeSize));
    return new DataPage<>(
        result.getContent().stream().map(RawRecordBrowseQuery::toSummary).toList(),
        result.getTotalElements(),
        page,
        safeSize);
  }

  public RawRecordDetail byId(UUID id) {
    RawRecord r =
        records.findById(id).orElseThrow(() -> new java.util.NoSuchElementException(id.toString()));
    RawRecordSummary summary = toSummary(r);
    String payload = new String(r.payloadBytes(), StandardCharsets.UTF_8);
    boolean isJson = payload.startsWith("{") || payload.startsWith("[");
    return new RawRecordDetail(summary, payload, isJson, r.payloadSha256());
  }

  private static RawRecordSummary toSummary(RawRecord r) {
    return new RawRecordSummary(
        r.id(),
        r.sourceSystem(),
        r.fetcherIdentity(),
        r.fetchMethod().name(),
        r.contentType(),
        r.characterEncoding(),
        r.fetchedAt(),
        r.payloadByteLength(),
        r.payloadSha256());
  }

  private static FetchMethod fetchMethod(RawFilter filter) {
    if (filter.fetchMethod() == null) return null;
    return FetchMethod.valueOf(filter.fetchMethod());
  }
}
```

- [ ] **Step 3: Write the integration test** (`RawRecordBrowseQueryIntegrationTest`, `@SpringBootTest` + Testcontainers, mirroring `CanonicalDailySalesIntegrationTest`): persist 3 raw records via `IngestionService.ingestPush(...)` (sources `CTB`/`LIGHTSPEED`, distinct fetchers), then assert `list` filters by source and by fetcher, paginates (`size=1` returns `total=3`, one item), returns no bytes, and `byId` returns the payload. Add a `@TestConfiguration` or reuse the existing `PostgresContainerConfiguration`.

- [ ] **Step 4: Run** `./gradlew test --tests "*RawRecordBrowseQueryIntegrationTest"`. Expected: PASS.
- [ ] **Step 5: Commit.**

---

### Task 3: `canonical.CanonicalBrowseQuery` (generic row mapping)

**Files:**
- Modify: each canonical repository (add `Page<T> findAllByOrderByRecordedAtDesc(Pageable)`; Spring Data derives it from the inherited `recordedAt` property) — files under `backend/src/main/java/com/goldys/platform/canonical/*Repository.java`.
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalBrowseQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalBrowseQueryIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 records; the package-private canonical entities/repositories.
- Produces: `CanonicalBrowseQuery.listRows(String entityId, int page, int size) -> DataPage<GenericRow>` and `entities() -> List<EntityDescriptor>`; reads any canonical entity from the `canonical` package.

- [ ] **Step 1: Add the ordered-page method** to `CanonicalDailySalesRepository`, `CanonicalProductSalesRepository`, `CanonicalReservationRepository`, `CanonicalInvoiceRepository`, `CanonicalInvoiceLineRepository`, `CanonicalLabourEntryRepository`, `CanonicalStockCountRepository`, `CanonicalWastageRepository`, `CanonicalSaleItemRepository`, `CanonicalShiftRepository`:

```java
org.springframework.data.domain.Page<CanonicalDailySales> findAllByOrderByRecordedAtDesc(org.springframework.data.domain.Pageable pageable);
```

(Substitute the concrete entity type per repository. `shift`/`sale_item`/`stock_count`/`wastage` repos get it too, even though they're empty — it's harmless.)

- [ ] **Step 2: Write the facade.** It dispatches on `entityId` to a per-entity paged query, mapping each row to a `GenericRow` whose columns are, in order: `id, logical_entity_id, source_system, source_record_ref, raw_record_id, valid_from, valid_to, recorded_at, superseded_at` (from `BitemporalEntity`), then the entity's own scalar fields. Worked example for daily sales:

```java
private static GenericRow toRow(CanonicalDailySales s) {
  java.util.Map<String, String> c = new java.util.LinkedHashMap<>();
  putCommon(c, s);
  c.put("trading_date", s.tradingDate().toString());
  c.put("total_sales", s.totalSales().toPlainString());
  c.put("gst_total", s.gstTotal().toPlainString());
  c.put("net_total", s.netTotal().toPlainString());
  return new GenericRow(s.id().toString(), c);
}

private static void putCommon(java.util.Map<String, String> c, BitemporalEntity e) {
  c.put("id", e.id().toString());
  c.put("logical_entity_id", e.logicalEntityId().toString());
  c.put("source_system", e.sourceSystem());
  c.put("source_record_ref", e.sourceRecordRef());
  c.put("raw_record_id", e.rawRecordId().toString());
  c.put("valid_from", e.validFrom().toString());
  if (e.validTo() != null) c.put("valid_to", e.validTo().toString());
  c.put("recorded_at", e.recordedAt().toString());
  if (e.supersededAt() != null) c.put("superseded_at", e.supersededAt().toString());
}
```

Entity-specific columns for the rest (read each entity's accessors to confirm exact names): product sales → `product_name_key, quantity_sold, amount` (plus its trading-date field); reservation → `reservation_id, reservation_date, party_size, status`; invoice → `invoice_number, supplier_name, invoice_date, due_date, total_amount`; invoice line → `invoice_number, line_ref, description, quantity, amount`; labour entry → `source_record_ref`-derived fields (`trading_date, department, hours`); stock count → `counted_date, product_name_key, quantity_on_hand`; wastage → `wastage_date, product_name_key, quantity, reason`. `shift` and `sale_item` have no meaningful fields — map only the common columns.

- [ ] **Step 3: `entities()`** returns the ten descriptors, with `placeholder=true` for `shift` and `sale_item` (and note `stock_count`/`wastage` have models but no source). Keep this list in one place; it is the registry's canonical half.

- [ ] **Step 4: Write the integration test** — record one daily-sales row and one invoice row, assert `listRows("daily_sales", …)` and `listRows("invoice", …)` return generic rows with the common columns first and the entity columns present; assert `entities()` returns 10 with `shift`/`sale_item` flagged `placeholder`.

- [ ] **Step 5: Run** the test. Expected: PASS.
- [ ] **Step 6: Commit.**

---

### Task 4: `reconciliation.DataExplorerQueryImpl` (registry + delegation)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/reconciliation/DataExplorerQueryImpl.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/DataExplorerQueryImplTest.java`

**Interfaces:**
- Consumes: `DataExplorerQuery` (Task 1), `RawRecordBrowseQuery` (Task 2), `CanonicalBrowseQuery` (Task 3), and the package-private resolved repositories in `reconciliation`.
- Produces: the single `DataExplorerQuery` bean Spring injects into the application service.

- [ ] **Step 1: Implement raw + canonical delegation** (thin pass-through to the two facades).

- [ ] **Step 2: Implement the resolved half** using the existing resolved repositories (`ResolvedDailySalesRepository`, `ResolvedProductSalesRepository`, `ResolvedReservationDayRepository`, `ResolvedLabourDayRepository`, `ResolvedInventoryDayRepository`). Each resolved descriptor maps rows to `GenericRow` with a `date`-style key column first (e.g. `trading_date`, `date`) plus the resolved columns. Order recent-first (date `DESC`).

- [ ] **Step 3: `resolvedDomains()`** returns the five descriptors: `resolved_daily_sales`, `resolved_product_sales`, `resolved_reservation_day`, `resolved_labour_day`, `resolved_inventory_day`.

- [ ] **Step 4: Write the unit test** (Mockito) — `canonicalEntities()`/`resolvedDomains()` enumerate 10 + 5; `canonicalRows`/`resolvedRows`/`listRaw`/`rawDetail` delegate to the corresponding facade/repo; an unknown id throws `NoSuchElementException` (mapped to 404 in Task 5).

- [ ] **Step 5: Run** the test. Expected: PASS.
- [ ] **Step 6: Commit.**

---

### Task 5: `application.DataExplorerService` + `api.DataExplorerController`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/application/DataExplorerService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/DataExplorerController.java`
- Test: `backend/src/test/java/com/goldys/platform/api/DataExplorerControllerTest.java`

**Interfaces:**
- Consumes: `DataExplorerQuery`, `PermissionService`, `CurrentUserService`.
- Produces: `GET /api/data/raw`, `/api/data/raw/{id}`, `/api/data/canonical`, `/api/data/canonical/{entity}`, `/api/data/resolved`, `/api/data/resolved/{domain}` — all `connectors` READ-gated.

- [ ] **Step 1: Write `DataExplorerService`** (mirrors `ConnectorApplicationService`): a `private static final ResourceKey RESOURCE = new ResourceKey("connectors");` and each method calls `permissions.require(role, RESOURCE, PermissionAction.READ)` before delegating to `DataExplorerQuery`. Methods: `listRaw(role, filter, page, size)`, `rawDetail(role, id)`, `canonicalEntities(role)`, `canonicalRows(role, entityId, page, size)`, `resolvedDomains(role)`, `resolvedRows(role, domainId, page, size)`.

- [ ] **Step 2: Write `DataExplorerController`** — thin: `@GetMapping` handlers that resolve `AccountUserDetails` → `UserRole` via `CurrentUserService.roleOf(user)` and delegate. Parse ISO instants from `from`/`to` query params (`@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant`), `int page = 0`, `int size = 50` defaults. Catch `NoSuchElementException` → 404. Unknown entity/domain id → 404 (the impl throws `NoSuchElementException`).

- [ ] **Step 3: Write the controller test** (`@WebMvcTest`, `@AutoConfigureMockMvc(addFilters=false)`, `@MockitoBean DataExplorerService`): each endpoint returns 200 with the service's result; a service that throws `AccessDeniedException` maps to the standard `NOT_PERMITTED` error (covered by `ApiExceptionHandler`); unknown id → 404.

- [ ] **Step 4: Run** `./gradlew test --tests "*DataExplorerControllerTest" --tests "*ArchitectureBoundariesTest"`. Expected: both PASS — this is the arch-seam proof.
- [ ] **Step 5: Commit.**

---

### Task 6: Frontend types + `Api` methods (`live` + `demo`)

**Files:**
- Modify: `frontend/lib/api/types.ts`
- Modify: `frontend/lib/api/live.ts`
- Modify: `frontend/lib/api/demo.ts`

**Interfaces:**
- Consumes: Task 5 endpoint shapes.
- Produces: `Api` methods the `/data` page calls.

- [ ] **Step 1: Add types** to `types.ts` and to the `Api` interface:

```ts
export interface RawRecordSummary {
  id: string; sourceSystem: string; fetcherIdentity: string; fetchMethod: string;
  contentType: string; characterEncoding: string | null; fetchedAt: string;
  byteLength: number; sha256: string;
}
export interface RawRecordDetail { summary: RawRecordSummary; payload: string; isJson: boolean; sha256: string; }
export interface EntityDescriptor { id: string; label: string; placeholder: boolean; }
export interface GenericRow { id: string; columns: Record<string, string>; }
export interface DataPage<T> { items: T[]; total: number; page: number; size: number; }
// Api additions:
listRawRecords(filter: { source?: string; fetcher?: string; method?: string; from?: string; to?: string }, page: number, size: number): Promise<DataPage<RawRecordSummary>>;
getRawRecord(id: string): Promise<RawRecordDetail>;
listCanonicalEntities(): Promise<EntityDescriptor[]>;
listCanonicalRows(entity: string, page: number, size: number): Promise<DataPage<GenericRow>>;
listResolvedDomains(): Promise<EntityDescriptor[]>;
listResolvedRows(domain: string, page: number, size: number): Promise<DataPage<GenericRow>>;
```

- [ ] **Step 2: Implement `liveApi`** — build the query string from the filter (skip nulls), e.g. `fetchApi<DataPage<RawRecordSummary>>(\`/api/data/raw?page=${page}&size=${size}\`)` plus optional `&source=…&fetcher=…&method=…&from=…&to=…`.

- [ ] **Step 3: Implement `demoApi`** — return small fixed fixtures (2–3 raw summaries, one detail, the 10 canonical entities + 5 resolved domains, a couple of generic rows) so the page renders without a backend.

- [ ] **Step 4: Run** `bun run lint` and `bun run build`. Expected: PASS.
- [ ] **Step 5: Commit.**

---

### Task 7: `/data` page + nav item

**Files:**
- Create: `frontend/app/(app)/data/page.tsx`
- Create: `frontend/components/data-explorer/` (split: `raw-tab.tsx`, `entity-table.tsx`, `payload-view.tsx` as needed)
- Modify: `frontend/components/app-shell/app-sidebar.tsx`
- Test: `frontend/app/(app)/data/data-page.test.tsx`

**Interfaces:**
- Consumes: the `Api` methods from Task 6; existing `LoadingState`, `ErrorState`, `EmptyState`, `Button`, `Select`, `Input` components; `useApiData` hook.

- [ ] **Step 1: Add the nav item** to the "Data" group in `app-sidebar.tsx` (a `Database` icon from `lucide-react`), `{ title: "Data explorer", href: "/data" }`.

- [ ] **Step 2: Build the page** — a three-section layout switched by a segmented control of `Button`s (Raw / Canonical / Resolved; no `Tabs` primitive exists in `components/ui`). Raw: filter inputs (source, fetcher, method, from/to) + paged table + expand-to-view payload (formatted `<pre>` JSON, or a "non-JSON payload (base64)" note). Canonical: entity `Select` (from `listCanonicalEntities`) + generic table whose columns come from the first row's `columns` keys, with a "model placeholder" `EmptyState` for `placeholder` entities. Resolved: domain `Select` + generic table + a "open in screen" link. Recent-first is already the backend ordering; the UI adds paging controls (page / next / prev).

- [ ] **Step 3: Write the page test** — renders the three tabs, switches sections, shows rows, and shows the placeholder empty state.

- [ ] **Step 4: Run** `bun run lint` + `bun run build`. Expected: PASS.
- [ ] **Step 5: Commit.**

---

### Task 8: Provenance-panel raw-record links

**Files:**
- Modify: `frontend/components/trust/provenance-panel.tsx`

**Interfaces:**
- Consumes: the existing `Provenance.rawRecordIds` (already in `types.ts`).

- [ ] **Step 1: Make each `rawRecordIds` entry a `Link` to `/data?layer=raw&id=<id>`** (replacing the current `join(", ")` text). The `/data` Raw tab reads `layer`/`id` from `useSearchParams` and opens the payload view for that id on load.

- [ ] **Step 2: Update `provenance-panel` tests** (if a test asserts the joined text, change it to assert the links).

- [ ] **Step 3: Run** `bun run lint` + `bun run build`. Expected: PASS.
- [ ] **Step 4: Commit.**

---

### Task 9: Full verification

- [ ] **Step 1:** `./gradlew test spotlessCheck build` (backend) — all tests + ArchUnit green.
- [ ] **Step 2:** `bun run lint` + `bun run build` (frontend).
- [ ] **Step 3:** Manual smoke (optional, if a backend + frontend are running): log in as owner, open `/data`, confirm the three tabs, a raw payload drill-in, and a provenance link route correctly.

## Out of scope (from the spec)

Export/bulk download, full-text search, write/edit actions, staff-facing access, populating `canonical_shift`/`canonical_sale_item`.
