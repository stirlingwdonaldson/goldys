# Sales Detail UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface the three already-ingested Lightspeed domains (payments, deleted orders, sale items) to the owner through one "Sales detail" screen with three tabs, each showing resolved aggregate charts over typed, paginated, filterable row tables, plus four React Flow diagrams.

**Architecture:** Backend adds typed, paginated list endpoints that read canonical facts through the existing `application → canonical` path (authorized on the domain metric resources), and wires the three new domains into the Data explorer / pipeline-map registries. Frontend adds one screen that consumes six new `Api` methods (live + demo) and reuses the existing `DataTable`, `WidgetRenderer` charts, and `FlowCanvas` diagram primitives.

**Decision taken during planning (resolves spec §8):** the per-sale relationship diagram is driven by an optional `saleNumber` filter on the three list endpoints (Tasks 3–5), not a dedicated `GET /api/sales/{saleNumber}/detail` endpoint — fewer new types, and the filter is reusable for table filtering.

**Tech Stack:** Java 25 / Spring Boot 3.5 (JPA repositories, ArchUnit boundaries), Flyway migrations; Next.js 15 / React 19 / TypeScript, TanStack Table, Recharts (via `WidgetRenderer`), React Flow (`@xyflow/react`), Vitest.

**Spec:** `docs/superpowers/specs/2026-10-10-sales-detail-ui-design.md`

## Global Constraints

- Resolved-only read direction for **metrics**: charts read the resolved `/mix` and `/totals` endpoints; row tables read **canonical** facts (the Data explorer's canonical-browse surface, not a metric).
- Table-driven permissions: list endpoints authorize on `payments.metrics` / `deleted-sales.metrics` / `sale-items.metrics` (seeded `ALL × OWNER`).
- Schema changes are Flyway-only (`hibernate.ddl-auto: validate`); never edit an applied migration; the next migration is `V40`.
- `ArchitectureBoundariesTest` forbids `api → canonical` direct dependencies; controllers return `semantic.*` records and reach canonical only via `application` services.
- Every `Api` interface method needs a **live** (`live.ts`) and a **demo** (`demo.ts`) implementation; the `Api` interface, `liveApi`, and `demoApi` must all compile together.
- Row tables use server-side pagination (`DataTable` with `pageSize={false}` and the endpoint's `DataPage`), never load the 1.8M-line sale-items table client-side.
- Copy uses the venue's words and Australian spelling; money via `formatCurrency`, dates via `formatDay`.

## Review Focus

1. **Empty range / no data** — a domain with zero rows renders `EmptyState`, and a chart builder given `[]` returns a widget that renders without crashing (not a fabricated zero series).
2. **Nullable fact fields** — `customerName`, `note`, `tableNumber`, `orderType`, `categoryName`, `sku`, `productNumber`, `staffName`, `clearingAccount` etc. render as an em-dash, never the literal string `null` or a render crash.
3. **`hasConflict` flag present** — all three mix/totals records carry `hasConflict` (single-source ⇒ always `false` today); the chart/table must not assume its absence.
4. **`saleNumber` filter matches nothing** — empty table and an empty/absent relationship graph, not an error or infinite spinner.
5. **Permission denial (`NOT_PERMITTED`)** — a list endpoint returning 403 renders `PermissionDenied` (mirroring the Kitchen screen), not a raw error toast.

---

### Task 1: Wire the three resolved domains into the Data explorer

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/DataExplorerQueryImpl.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/DataExplorerRegistryTest.java`

**Interfaces:**
- Consumes: `ResolvedPaymentDay`, `ResolvedDeletedSaleDay`, `ResolvedSaleItemDay` (package-private entities) and their `JpaRepository` interfaces (already have `findAll(PageRequest)`).
- Produces: `resolvedDomains()` returning 8 descriptors; `resolvedRows(domainId, page, size)` handling `resolved_payment_day`, `resolved_deleted_sale_day`, `resolved_sale_item_day` via new package-private static mappers `paymentRow`, `deletedSaleRow`, `saleItemRow`.

- [ ] **Step 1: Write the failing test** — `DataExplorerRegistryTest.resolvedDomainsIncludesTheThreeNewDomains` asserts `new DataExplorerQueryImpl(null, null, null, null, null, null, null, null, null, null).resolvedDomains()` returns a list whose ids include `resolved_payment_day` ("Payments"), `resolved_deleted_sale_day` ("Deleted orders"), `resolved_sale_item_day` ("Sale items"), all `placeholder == false`. Also `paymentRowMapsColumns` constructs a `ResolvedPaymentDay` and asserts the returned `GenericRow` columns contain `trading_date`, `payment_type_name`, `amount`, `tip`, `payment_count`, `resolution_type`, `authoritative_source`, `has_conflict`, `resolved_at` with the exact values.

- [ ] **Step 2: Run it to see it fail** — `./gradlew test --tests '*DataExplorerRegistryTest'` → compile error: constructor arg count and missing methods.

- [ ] **Step 3: Implement** — add three repository fields + constructor params (`ResolvedPaymentDayRepository payments`, `ResolvedDeletedSaleDayRepository deletedSales`, `ResolvedSaleItemDayRepository saleItems`); add the three descriptors to `resolvedDomains()`; add three `case` arms in `resolvedRows(...)` calling `page(repo.findAll(pr), ...)`; add package-private static mappers. Map `ResolvedPaymentDay`: `payment_type_name`, `amount`, `tip`, `payment_count`; `ResolvedDeletedSaleDay`: `deleted_count`, `total_inc_tax`, `total_tax`; `ResolvedSaleItemDay`: `category_name`, `quantity`, `amount`. Each reuses `resolvedCommon(...)` for `resolution_type` / `authoritative_source` / `has_conflict` / `resolved_at`, and derives the row id from `tradingDate` (+ dimension for the two composite-key domains).

- [ ] **Step 4: Run it to see it pass** — `./gradlew test --tests '*DataExplorerRegistryTest'`

- [ ] **Step 5: Commit** — `feat: register payments, deleted-sales and sale-items resolved domains`

---

### Task 2: Wire the two new canonical entities into the Data explorer

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalBrowseQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalBrowseRegistryTest.java`

**Interfaces:**
- Consumes: `CanonicalPaymentRepository`, `CanonicalDeletedSaleRepository` (add as constructor deps).
- Produces: `entities()` returning `payment` ("Payments"), `deleted_sale` ("Deleted orders"), and `sale_item` with `placeholder == false`; `listRows(...)` handling `payment` and `deleted_sale`; `saleItemColumns` expanded with the line-detail fields.

- [ ] **Step 1: Write the failing test** — `entitiesListsPaymentsAndDeletedSalesAndUnplacesSaleItem` asserts the three descriptors; `paymentColumnsMapsFacts` and `deletedSaleColumnsMapsFacts` assert the new package-private `paymentColumns` / `deletedSaleColumns` mappers produce the expected keys (`sale_number`, `payment_type_name`, `amount`, `tip`, `tendered`, `surcharge` … / `sale_number`, `order_type`, `note`, `total_inc_tax`, `deleted_by_staff_name` …).

- [ ] **Step 2: Run it to see it fail** — `./gradlew test --tests '*CanonicalBrowseRegistryTest'` → missing descriptors/cases.

- [ ] **Step 3: Implement** — add the two repository fields + constructor params; add `new EntityDescriptor("payment", "Payments", false)` and `new EntityDescriptor("deleted_sale", "Deleted orders", false)` and change `sale_item`'s third arg to `false`; add `case "payment"` and `case "deleted_sale"` in `listRows`; add package-private `paymentColumns` / `deletedSaleColumns`; expand `saleItemColumns` with `sale_number`, `category_name`, `product_number`, `sku`, `sold_price_inc_tax`, `total_tax`, `cost_inc_tax`, `order_type`, `sale_type`, `staff_name`, `register_name`, `table_number`. Use `entity.sourceRecordRef()` for the payment/deleted-sale `sale_number` where the entity exposes it, and expose the fact accessors needed (Task 3 adds them for payment; add the analogous deleted-sale accessors here).

- [ ] **Step 4: Run it to see it pass** — `./gradlew test --tests '*CanonicalBrowseRegistryTest'`

- [ ] **Step 5: Commit** — `feat: register payments and deleted-sales canonical entities`

---

### Task 3: Payments typed list endpoint

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/PaymentRow.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalPayment.java` (add package-private accessors)
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalPaymentRepository.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalPaymentQuery.java`
- Modify: `backend/src/main/java/com/goldys/platform/application/PaymentReportingService.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/PaymentController.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/PaymentListIntegrationTest.java`

**Interfaces:**
- Produces: `record PaymentRow(LocalDate tradingDate, String saleNumber, String paymentTypeName, String paymentTypeCode, String paymentSourceType, String lspayPaymentMode, String clearingAccount, BigDecimal amount, BigDecimal tip, BigDecimal tendered, BigDecimal surcharge, int paymentCount, int tipCount, String reconciled, String registerCode, String registerName, String staffName, String staffCode, String siteId, String customerName)` in `semantic`.
- Produces: `CanonicalPaymentQuery.page(String paymentType, String saleNumber, LocalDate from, LocalDate to, int page, int size): DataPage<PaymentRow>`.
- Produces: `PaymentReportingService.list(UserRole role, String paymentType, String saleNumber, LocalDate from, LocalDate to, int page, int size): DataPage<PaymentRow>`.
- Produces: `GET /api/payments?from=&to=&paymentType=&saleNumber=&page=&size=` → `DataPage<PaymentRow>`.

- [ ] **Step 1: Write the failing test** — `PaymentListIntegrationTest` (Postgres via Testcontainers) seeds two current payments across two dates/types and one superseded payment, then calls `canonicalPaymentQuery.page(null, null, from, to, 0, 50)` and asserts: only current rows returned, ordered date desc; then `page("Tyro", null, from, to, 0, 50)` returns only the Tyro row; then `page(null, "SALE-1", ...)` returns only sale SALE-1's tenders; and the `PaymentRow` carries the full field set (`saleNumber`, `paymentTypeCode`, `tendered`, `surcharge`, `registerName`, …).

- [ ] **Step 2: Run it to see it fail** — `./gradlew test --tests '*PaymentListIntegrationTest'` → no `page` method.

- [ ] **Step 3: Implement** — add the `PaymentRow` record; add package-private accessors to `CanonicalPayment` for every field not yet exposed; add one `@Query` to `CanonicalPaymentRepository`:

  ```java
  @Query("""
      select p from CanonicalPayment p
      where p.supersededAt is null
        and p.tradingDate >= :from and p.tradingDate <= :to
        and (:paymentType is null or p.paymentTypeName = :paymentType)
        and (:saleNumber is null or p.saleNumber = :saleNumber)
      order by p.tradingDate desc, p.paymentTypeName asc, p.saleNumber asc
      """)
  Page<CanonicalPayment> findCurrent(LocalDate from, LocalDate to, String paymentType, String saleNumber, Pageable pageable);
  ```

  Add `page(...)` to `CanonicalPaymentQuery` (build `PageRequest.of(max(0,page), min(max(1,size),200))`, map each `CanonicalPayment` to `PaymentRow`); add `list(...)` to `PaymentReportingService` (`permissions.require(role, RESOURCE, PermissionAction.READ)` then `payments.page(...)`, injecting `CanonicalPaymentQuery`); add the `@GetMapping` list handler to `PaymentController`.

- [ ] **Step 4: Run it to see it pass** — `./gradlew test --tests '*PaymentListIntegrationTest'`

- [ ] **Step 5: Commit** — `feat: add payments typed list endpoint`

---

### Task 4: Deleted-sales typed list endpoint

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/DeletedSaleRow.java`
- Modify: `CanonicalDeletedSale.java`, `CanonicalDeletedSaleRepository.java`, `CanonicalDeletedSaleQuery.java`, `application/DeletedSaleReportingService.java`, `api/DeletedSaleController.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/DeletedSaleListIntegrationTest.java`

**Interfaces:**
- Produces: `record DeletedSaleRow(LocalDate tradingDate, String saleNumber, String orderType, String note, BigDecimal totalIncTax, BigDecimal totalExTax, BigDecimal totalTax, BigDecimal totalCost, String openedRegisterName, String deletedRegisterName, String staffName, String deletedByStaffName, String tableNumber, String siteId, String customerName)` in `semantic`.
- Produces: `CanonicalDeletedSaleQuery.page(String saleNumber, LocalDate from, LocalDate to, int page, int size): DataPage<DeletedSaleRow>`; `DeletedSaleReportingService.list(...)`; `GET /api/deleted-sales?from=&to=&saleNumber=&page=&size=`.

- [ ] **Step 1: Write the failing test** — `DeletedSaleListIntegrationTest` mirrors Task 3's, asserting current-only rows, date ordering, the `saleNumber` filter, and the full `DeletedSaleRow` field set.

- [ ] **Step 2: Run it to see it fail** — `./gradlew test --tests '*DeletedSaleListIntegrationTest'`

- [ ] **Step 3: Implement** — mirror Task 3 exactly for `DeletedSaleRow` (note `saleNumber` is the identity, so the `@Query` filters `p.saleNumber = :saleNumber` only when non-null). The deleted-sales list has no dimension filter.

- [ ] **Step 4: Run it to see it pass** — `./gradlew test --tests '*DeletedSaleListIntegrationTest'`

- [ ] **Step 5: Commit** — `feat: add deleted-sales typed list endpoint`

---

### Task 5: Sale-items typed list endpoint + `sale_number` index

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/SaleItemRow.java`
- Create: `backend/src/main/resources/db/migration/V40__sale_item_sale_number_index.sql`
- Modify: `CanonicalSaleItem.java`, `CanonicalSaleItemRepository.java`, `CanonicalSaleItemQuery.java`, `application/SaleItemReportingService.java`, `api/SaleItemController.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/SaleItemListIntegrationTest.java`

**Interfaces:**
- Produces: `record SaleItemRow(LocalDate tradingDate, String saleNumber, String receiptLineId, String itemName, String productNumber, String sku, String categoryName, Integer quantitySold, BigDecimal amount, BigDecimal soldPriceIncTax, BigDecimal totalTax, BigDecimal costIncTax, String orderType, String saleType, String staffName, String registerName, String tableNumber)` in `semantic`, where `receiptLineId` is mapped from `sourceRecordRef()`.
- Produces: `CanonicalSaleItemQuery.page(String category, String saleNumber, LocalDate from, LocalDate to, int page, int size): DataPage<SaleItemRow>`; `SaleItemReportingService.list(...)`; `GET /api/sale-items?from=&to=&category=&saleNumber=&page=&size=`.

- [ ] **Step 1: Write the failing test** — `SaleItemListIntegrationTest` mirrors Task 3's, asserting current-only rows, date ordering, the `category` filter, the `saleNumber` filter, and that `receiptLineId` equals the seed's `source_record_ref`.

- [ ] **Step 2: Run it to see it fail** — `./gradlew test --tests '*SaleItemListIntegrationTest'`

- [ ] **Step 3: Implement** — mirror Task 3 for `SaleItemRow` with the `category` dimension filter (nullable `category_name`); add `V40__sale_item_sale_number_index.sql` with `CREATE INDEX ... ON canonical_sale_item (sale_number) WHERE superseded_at IS NULL;` (and the equivalent on `canonical_payment`), because the `saleNumber` drill-down filters a 1.8M-row table.

- [ ] **Step 4: Run it to see it pass** — `./gradlew test --tests '*SaleItemListIntegrationTest'`

- [ ] **Step 5: Commit** — `feat: add sale-items typed list endpoint and sale_number index`

---

### Task 6: Frontend `Api` contract — types, live, demo

**Files:**
- Modify: `frontend/lib/api/types.ts` (add row/mix/filter types + 6 `Api` methods)
- Modify: `frontend/lib/api/live.ts`
- Modify: `frontend/lib/api/demo.ts`
- Modify: `frontend/lib/api/index.ts` (re-export new types)
- Test: `frontend/lib/api/types.test.ts` (or extend existing) and `frontend/lib/api/demo.test.ts`

**Interfaces:**
- Produces (types): `PaymentRow`, `DeletedSaleRow`, `SaleItemRow` (mirror the backend records; money fields `number | string`, dates `string`), `PaymentMix`, `DeletedSaleDay`, `SaleItemMix`, and filters `PaymentFilter { from?: string; to?: string; paymentType?: string; saleNumber?: string }`, `DeletedSaleFilter { from?: string; to?: string; saleNumber?: string }`, `SaleItemFilter { from?: string; to?: string; category?: string; saleNumber?: string }`.
- Produces (`Api` additions): `listPayments(filter, page, size): Promise<DataPage<PaymentRow>>`, `listDeletedSales(filter, page, size)`, `listSaleItems(filter, page, size)`, `getPaymentMix(from, to): Promise<PaymentMix[]>`, `getDeletedSaleTotals(from, to): Promise<DeletedSaleDay[]>`, `getSaleItemMix(from, to): Promise<SaleItemMix[]>`.

- [ ] **Step 1: Write the failing test** — a type-level + demo test: `demoApi.listPayments({ paymentType: "Tyro" }, 0, 20)` resolves to a `DataPage<PaymentRow>` whose `items[0]` has `paymentTypeName === "Tyro"`; `demoApi.getPaymentMix("2026-10-01", "2026-10-05")` returns at least two distinct `paymentTypeName`s. Compilation of the `Api` interface is the primary gate: `liveApi` and `demoApi` must both satisfy it.

- [ ] **Step 2: Run it to see it fail** — `bun run typecheck` → `liveApi`/`demoApi` missing the six methods.

- [ ] **Step 3: Implement** — add the types and `Api` methods; in `live.ts` add `fetchApi` calls (build `URLSearchParams` for filters + `page`/`size`, mirroring `listRawRecords`; `getPaymentMix`/`getDeletedSaleTotals`/`getSaleItemMix` hit the existing `/api/payments/mix`, `/api/deleted-sales/totals`, `/api/sale-items/mix`); in `demo.ts` add small fixtures (a few payments across two types/dates, two deleted orders, a few sale items across two categories) and return them filtered by the request params. Re-export the new types from `index.ts`.

- [ ] **Step 4: Run it to see it pass** — `bun run typecheck && bun run test -- tests/lib/api` (or the project's Vitest invocation)

- [ ] **Step 5: Commit** — `feat: add sales-detail Api methods with live and demo implementations`

---

### Task 7: Navigation, resolved-screen mapping, and the screen shell

**Files:**
- Modify: `frontend/lib/nav.ts`
- Modify: `frontend/lib/data-layers.ts`
- Create: `frontend/app/(app)/sales-detail/page.tsx`
- Create: `frontend/components/sales-detail/date-range-filter.tsx`
- Test: `frontend/app/(app)/sales-detail/sales-detail-page.test.tsx`, `frontend/lib/nav.test.ts`

**Interfaces:**
- Produces: nav item `{ title: "Sales detail", href: "/sales-detail", icon: ReceiptText, keywords: [...] }` in the Business group after Sales; `RESOLVED_SCREENS` entries for `resolved_payment_day`, `resolved_deleted_sale_day`, `resolved_sale_item_day` → `{ href: "/sales-detail", label: "Sales detail" }`.
- Produces: the page renders `PageHeader`, a shared date-range filter (trailing 30-day default, like Kitchen's `defaultRange()`), and a `Tabs` control with three tabs that render placeholder tab components (Task 8–10 fill them).

- [ ] **Step 1: Write the failing test** — `sales-detail-page.test.tsx` renders the page and asserts the header "Sales detail", three tabs labelled Payments / Deleted orders / Sale items, and that switching tabs shows the right stub; `nav.test.ts` asserts `findNav("/sales-detail")` resolves to the Business group item.

- [ ] **Step 2: Run it to see it fail** — `bun run test` → route/tab not found.

- [ ] **Step 3: Implement** — add the nav item and `RESOLVED_SCREENS` entries; create the page (client component) with the date-range state hoisted so the three tabs share it; add three tab components as minimal stubs (each renders an `EmptyState` for now). Use `useSearchParams` to allow a `tab` deep-link (`/sales-detail?tab=payments|deleted-orders|sale-items`) from the pipeline map.

- [ ] **Step 4: Run it to see it pass** — `bun run typecheck && bun run test`

- [ ] **Step 5: Commit** — `feat: add sales-detail nav entry and screen shell`

---

### Task 8: Payments tab — chart + table

**Files:**
- Create: `frontend/components/sales-detail/payment-mix-chart.ts` (pure builder)
- Create: `frontend/components/sales-detail/payments-tab.tsx`
- Test: `frontend/components/sales-detail/payments-tab.test.tsx`, `frontend/components/sales-detail/payment-mix-chart.test.ts`

**Interfaces:**
- Consumes: `Api.listPayments`, `Api.getPaymentMix`.
- Produces: `buildPaymentMixWidget(mix: PaymentMix[]): BarChartWidget` (stacked series per `paymentTypeName`, x = `tradingDate`, y = `amount`; returns a widget with an empty `series` when `mix` is empty).
- Produces: `PaymentsTab({ from, to })` rendering the `WidgetRenderer` chart then a `DataTable` with `pageSize={false}` + server paging (Previous/Next using the `DataPage`), a payment-type dropdown, columns: date, sale number, payment type, amount, tip, surcharge, tendered, count, reconciled, register, staff.

- [ ] **Step 1: Write the failing test** — `payment-mix-chart.test.ts` asserts `buildPaymentMixWidget([...])` produces one series per distinct type with the expected points, and `buildPaymentMixWidget([])` yields an empty `series` array; `payments-tab.test.tsx` renders with an injected `apiOverride` and asserts rows appear and the currency columns are formatted.

- [ ] **Step 2: Run it to see it fail** — `bun run test` → builder/component missing.

- [ ] **Step 3: Implement** — the builder and the tab. Currency columns via `currencyColumn`; date via `dateColumn`; nullable text via a cell rendering `value ?? "—"`; on `error.code === "NOT_PERMITTED"` render `PermissionDenied` (mirroring `kitchen/page.tsx`). The chart renders through `WidgetRenderer` with `widget={buildPaymentMixWidget(mix)}`.

- [ ] **Step 4: Run it to see it pass** — `bun run typecheck && bun run test`

- [ ] **Step 5: Commit** — `feat: add payments tab with mix chart and table`

---

### Task 9: Deleted orders tab — chart + table

**Files:**
- Create: `frontend/components/sales-detail/deleted-sales-trend.ts` (pure builder)
- Create: `frontend/components/sales-detail/deleted-orders-tab.tsx`
- Test: `frontend/components/sales-detail/deleted-orders-tab.test.tsx`

**Interfaces:**
- Consumes: `Api.listDeletedSales`, `Api.getDeletedSaleTotals`.
- Produces: `buildDeletedSalesTrend(totals: DeletedSaleDay[]): TimeSeriesWidget` (series `count` and `totalIncTax`; empty `series` when empty), plus a `StatCard` row for period count and total value.
- Produces: `DeletedOrdersTab({ from, to })` — trend chart, then the table (date, sale number, order type, total inc/ex/tax, cost, note, staff, deleted by, register, table).

- [ ] **Step 1: Write the failing test** — mirror Task 8's builder/component tests with deleted-sales fixtures.

- [ ] **Step 2: Run it to see it fail** — `bun run test`

- [ ] **Step 3: Implement** — mirror Task 8.

- [ ] **Step 4: Run it to see it pass** — `bun run typecheck && bun run test`

- [ ] **Step 5: Commit** — `feat: add deleted-orders tab with trend and table`

---

### Task 10: Sale items tab — chart + table

**Files:**
- Create: `frontend/components/sales-detail/sale-item-mix-chart.ts` (pure builder)
- Create: `frontend/components/sales-detail/sale-items-tab.tsx`
- Test: `frontend/components/sales-detail/sale-items-tab.test.tsx`

**Interfaces:**
- Consumes: `Api.listSaleItems`, `Api.getSaleItemMix`.
- Produces: `buildSaleItemMixWidget(mix: SaleItemMix[]): BarChartWidget` (stacked by `categoryName`, y = `amount`); `SaleItemsTab({ from, to })` — chart, then the table (date, sale number, receipt line, item, product number, SKU, category, quantity, sold price, cost, total).

- [ ] **Step 1: Write the failing test** — mirror Task 8's tests with sale-item fixtures.

- [ ] **Step 2: Run it to see it fail** — `bun run test`

- [ ] **Step 3: Implement** — mirror Task 8.

- [ ] **Step 4: Run it to see it pass** — `bun run typecheck && bun run test`

- [ ] **Step 5: Commit** — `feat: add sale-items tab with mix chart and table`

---

### Task 11: Flow diagrams — lineage, relationship, and drill-down

**Files:**
- Create: `frontend/components/sales-detail/lineage-graph.ts` (pure builder + a small `LineageGraphView`)
- Create: `frontend/components/sales-detail/relationship-graph.ts` (pure builder + `RelationshipGraphView`)
- Test: `frontend/components/sales-detail/lineage-graph.test.ts`, `frontend/components/sales-detail/relationship-graph.test.ts`

**Interfaces:**
- Consumes: `Api.listCanonicalRows` (for live counts) and the existing `FlowCanvas` / `ColumnGraph` types.
- Produces: `buildLineageGraph(domain: "payment" | "deleted_sale" | "sale_item", counts: { raw: number | null; canonical: number | null; resolved: number | null }): ColumnGraph` — the fixed chain `Lightspeed {webhook}` → `raw ledger` → `canonical {…}` → `resolved {…}` → `metric`, with `href`/`drill` targets. The `metric` node carries `drill: "payments" | "deleted-orders" | "sale-items"`.
- Produces: `buildRelationshipGraph(saleNumber, payments, items, deletedOrder): ColumnGraph` — a hub node for the sale with tenders and line items as child nodes and (when `deletedOrder` is non-null) a deleted-order node. Empty when `payments` and `items` are both empty.
- Produces: `RelationshipGraphView` wired to a `Trace` action on payment/sale-item rows (`onDrill` decodes the sale number), fetching via `listPayments({ saleNumber })` + `listSaleItems({ saleNumber })` + `listDeletedSales({ saleNumber })`.

- [ ] **Step 1: Write the failing test** — `lineage-graph.test.ts` asserts the five columns and the `drill` id on the metric node; `relationship-graph.test.ts` asserts a sale with two tenders + three items + a deleted order produces the expected node count and a `deletedOrder` node, and an empty sale produces an empty graph.

- [ ] **Step 2: Run it to see it fail** — `bun run test`

- [ ] **Step 3: Implement** — the builders as pure functions (no React), then the two view components rendering `FlowCanvas` (lineage with `onDrill` switching tabs via a callback; relationship reusing `InvoiceGraphView`'s drill pattern). Wire the `Trace` column on the payments and sale-items tables (Tasks 8/10) to open the relationship graph.

- [ ] **Step 4: Run it to see it pass** — `bun run typecheck && bun run test`

- [ ] **Step 5: Commit** — `feat: add sales-detail lineage and relationship flow diagrams`

---

### Task 12: Full verification

- [ ] **Step 1:** `cd backend && ./gradlew test spotlessCheck`
- [ ] **Step 2:** `cd frontend && bun run typecheck && bun run lint && bun run test && bun run build`
- [ ] **Step 3:** Confirm the pipeline map (Data health screen) now lists the three domains, and the Data explorer's Canonical/Resolved tabs show them.
- [ ] **Step 4:** Commit any remaining tidy-ups with a `chore:` or `test:` message.
