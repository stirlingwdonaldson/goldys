# Sales Detail UI — Design

Date: 2026-10-10 · Status: approved (brainstormed in-session; this doc is the written spec)

## 1. Context

PR #114 (`feature/lightspeed-payments-deleted-sales`, merged to `origin/main`) landed three new
single-source Lightspeed domains end to end on the **backend** — canonical → resolved → semantic →
API — but did not touch the frontend. The three domains:

| Domain | Source webhook | Scale | Read endpoint today |
|---|---|---|---|
| Payments | `all-payments` | ~973k tenders | `GET /api/payments/mix` (day × payment type) |
| Deleted orders | `all-deleted-orders` | ~3.2k orders | `GET /api/deleted-sales/totals` (day) |
| Sale items | `sales-details` | ~1.8M receipt lines | `GET /api/sale-items/mix` (day × category) |

The backend also registered metric-catalogue entries (`payments.amount`, `payments.tip`,
`deleted_sales.amount`, `sale_items.amount`, `sale_items.quantity`), migrations V33–V39, and
permission seeds (`ALL × OWNER`). See `specs/2026-10-10-lightspeed-payments-deleted-sales-design.md`
for the field mappings and identity rules.

The goal of this spec is to surface that data to the owner: **row-level data tables plus the resolved
daily aggregates as charts**, delivered through a single "Sales detail" screen with three tabs, and
four kinds of React Flow diagrams.

## 2. Goals and non-goals

### Goals

1. One "Sales detail" nav item with three tabs — Payments, Deleted orders, Sale items.
2. Each tab: resolved daily aggregate chart(s) on top, a typed, paginated, filterable data table below.
3. Typed, paginated, filterable list endpoints for the three domains (they do not exist yet).
4. The three domains appear in the generic Data explorer and the React Flow pipeline map.
5. Four flow diagrams: pipeline-map extension, per-domain lineage, entity-relationship drill-down,
   and interactive drill-down navigation.

### Non-goals (explicitly out of scope, carried from the merged plan)

- Manual overrides / override services for these domains (deferred in the backend plan).
- New Ask Goldy's reporting tools (the existing `GET_METRIC` / `RANK_DIMENSION` /
  `COMPARE_METRIC_PERIODS` tools cover the registered metrics).
- Any change to ingestion, reconciliation of a second source, or the identity rules already decided
  in the backend spec.

## 3. Architecture invariants honoured

- **Resolved-only read direction for metrics**: charts read the resolved `/mix` and `/totals`
  endpoints (`resolved_payment_day`, `resolved_deleted_sale_day`, `resolved_sale_item_day`). The
  row-level tables read the **canonical** repositories — the same browsing surface the Data
  explorer's Canonical tab already provides, not a metric.
- **Table-driven permissions**: list endpoints authorize on the existing
  `payments.metrics` / `deleted-sales.metrics` / `sale-items.metrics` resources (seeded
  `ALL × OWNER`).
- **Schema via Flyway only**: no schema change is needed here — the canonical tables and resolved
  read models already exist (V33–V38).

## 4. Backend surface (additive)

### 4.1 Typed list endpoints

Three paginated, filterable endpoints read canonical repositories:

| Endpoint | Query params | Returns |
|---|---|---|
| `GET /api/payments` | `from`, `to`, `paymentType`, `page`, `size` | `DataPage<PaymentRow>` |
| `GET /api/deleted-sales` | `from`, `to`, `page`, `size` | `DataPage<DeletedSaleRow>` |
| `GET /api/sale-items` | `from`, `to`, `category`, `page`, `size` | `DataPage<SaleItemRow>` |

`from`/`to` are inclusive ISO dates; `paymentType` and `category` filter on the canonical
`payment_type_name` and `category_name` dimensions respectively. Sorting is server-side (date
descending, then the dimension) to match the table's initial sort; page size is capped (existing
explorer cap is 200).

DTOs mirror the canonical fact fields (typed, not `GenericRow` strings):

- **`PaymentRow`** — `tradingDate`, `saleNumber`, `paymentTypeName`, `paymentTypeCode`,
  `paymentSourceType`, `lspayPaymentMode`, `clearingAccount`, `amount`, `tip`, `tendered`,
  `surcharge`, `paymentCount`, `tipCount`, `reconciled`, `registerName`, `registerCode`,
  `staffName`, `staffCode`, `siteId`, `customerName`.
- **`DeletedSaleRow`** — `tradingDate`, `saleNumber`, `orderType`, `note`, `totalIncTax`,
  `totalExTax`, `totalTax`, `totalCost`, `openedRegisterName`, `deletedRegisterName`, `staffName`,
  `deletedByStaffName`, `tableNumber`, `siteId`, `customerName`.
- **`SaleItemRow`** — `tradingDate`, `saleNumber`, `receiptLineId`, `itemName`, `productNumber`,
  `sku`, `categoryName`, `quantitySold`, `amount`, `soldPriceIncTax`, `totalTax`, `costIncTax`,
  `orderType`, `saleType`, `staffName`, `registerName`, `tableNumber`.

Placement: a reporting service per domain in `application/` (mirroring the existing
`PaymentReportingService` etc.), a controller per domain in `api/`, a query method on the canonical
service/repository. No new packages, so `ArchitectureBoundariesTest` needs no new allowances;
add boundary/integration tests for the new read paths anyway.

### 4.2 Registry wiring (unblocks Data explorer + pipeline map)

- `canonical/CanonicalBrowseQuery`:
  - `entities()`: add `payment` ("Payments") and `deleted_sale` ("Deleted orders"); flip
    `sale_item` from `placeholder: true` → `false` now that it is populated.
  - `listRows()`: add `payment` and `deleted_sale` cases; complete the `sale_item` case against the
    populated `canonical_sale_item`.
- `reconciliation/DataExplorerQueryImpl`:
  - `resolvedDomains()`: add `resolved_payment_day` ("Payments"), `resolved_deleted_sale_day`
    ("Deleted orders"), `resolved_sale_item_day` ("Sale items").
  - `resolvedRows()`: add cases + `GenericRow` mappers for the three new resolved read models.

This is the single change that makes both the generic Data explorer and the pipeline map include the
new domains, because both read `listCanonicalEntities()` / `listResolvedDomains()`.

## 5. Frontend

### 5.1 Navigation

Add to the Business group in `lib/nav.ts`, directly after Sales:

```ts
{ title: "Sales detail", href: "/sales-detail", icon: ReceiptText,
  keywords: ["payments", "voids", "deleted orders", "sale items", "line items", "tenders"] }
```

### 5.2 Screen

`app/(app)/sales-detail/page.tsx` — a client page with:

- `PageHeader` "Sales detail" + a shared date-range filter (from/to ISO inputs, like the Data
  explorer raw section).
- A `Tabs` control with three tabs: **Payments**, **Deleted orders**, **Sale items**.

Each tab renders: resolved aggregate chart(s) on top (Recharts), then a typed `DataTable`
(reusing `components/data-table` and the shared `currencyColumn` / `dateColumn` column builders):

- **Payments tab** — `getPaymentMix` chart (amount by payment type over time) → payments table.
  Columns: date, sale number, payment type, amount, tip, surcharge, tendered, count, reconciled,
  register, staff. Filter: payment type (dropdown from the mix dimension), date range.
- **Deleted orders tab** — `getDeletedSaleTotals` chart (daily count + total inc tax) → deleted
  orders table. Columns: date, sale number, order type, total inc/ex/tax, cost, note, staff,
  deleted by, register, table. Filter: date range.
- **Sale items tab** — `getSaleItemMix` chart (quantity/amount by category) → line-items table.
  Columns: date, sale number, receipt line, item, product number, SKU, category, quantity, sold
  price, cost, total. Filter: category (dropdown), date range.

Loading / error / empty states reuse `useApiData` and the existing `LoadingState` / `ErrorState` /
`EmptyState` components.

### 5.3 API contract

Six new methods on the `Api` interface (`lib/api/types.ts`), each with a **live** and **demo**
implementation (`live.ts` / `demo.ts`) and matching types:

```ts
listPayments(filter: PaymentFilter): Promise<DataPage<PaymentRow>>;
listDeletedSales(filter: DeletedSaleFilter): Promise<DataPage<DeletedSaleRow>>;
listSaleItems(filter: SaleItemFilter): Promise<DataPage<SaleItemRow>>;
getPaymentMix(from: string, to: string): Promise<PaymentMix[]>;
getDeletedSaleTotals(from: string, to: string): Promise<DeletedSaleDay[]>;
getSaleItemMix(from: string, to: string): Promise<SaleItemMix[]>;
```

`PaymentMix` / `DeletedSaleDay` / `SaleItemMix` mirror the backend records
(`tradingDate`, `paymentTypeName`/`categoryName`, `amount`/`tip`/`count`/`quantity`,
`totalIncTax`/`totalTax`, `hasConflict`).

List filter shapes (page/size are separate arguments, matching the existing `listRawRecords`
signature):

```ts
interface PaymentFilter    { from?: string; to?: string; paymentType?: string }
interface DeletedSaleFilter{ from?: string; to?: string }
interface SaleItemFilter   { from?: string; to?: string; category?: string }
```

### 5.4 Resolved screen mapping

`lib/data-layers.ts`: map the three new resolved domains to the new screen:

```ts
resolved_payment_day:      { href: "/sales-detail", label: "Sales detail" },
resolved_deleted_sale_day: { href: "/sales-detail", label: "Sales detail" },
resolved_sale_item_day:    { href: "/sales-detail", label: "Sales detail" },
```

(The pipeline map uses this for its "Shown on …" detail line and its node `href`.)

## 6. Flow diagrams

All four reuse `components/flow/FlowCanvas` and the `ColumnGraph` / `StepNodeData` types.

1. **Pipeline-map extension** — free once §4.2 lands: `buildPipelineGraph` reads the entity/domain
   registries, so the three domains appear as canonical + resolved nodes with live counts.
2. **Per-domain lineage** — a `lineage-graph` builder renders the fixed chain for the active tab:
   `Lightspeed {all-payments|all-deleted-orders|sales-details}` → `raw ledger` → `canonical
   {payment|deleted_sale|sale_item}` → `resolved {day}` → `metric`. Static structure with live row
   counts where cheap to read.
3. **Entity-relationship** — a per-sale drill-down diagram centred on `sale_number`: its payment
   tenders and line items, plus a deleted-order node when that sale was voided. Mirrors the existing
   Kitchen invoice node-graph (`getInvoiceGraphSuppliers/Invoices/Lines`).
4. **Interactive drill-down** — `FlowCanvas` `onDrill` + `drill` targets: clicking a node applies a
   filter (e.g. click a payment-type node → filter the payments table to that type; click the
   "Payments" resolved node → switch to the Payments tab).

The per-domain lineage (§6.2) and entity-relationship (§6.3) diagrams need a small amount of graph
metadata (node/edge definition), which lives in the frontend as pure functions so they are
unit-testable without a browser.

## 7. Data flow, error handling, testing

- **Data flow** follows existing screens: `useApiData` wraps the `Api` method; tabs fetch
  independently on mount and refetch when the date-range/dimension filter changes.
- **Error handling**: backend list endpoints return the standard `ApiErrorResponse`; the screen
  renders `ErrorState` with retry. Permission denials surface as the standard error envelope
  (no special handling).
- **Testing**:
  - Backend: integration tests for the three list endpoints (pagination, date-range filter,
    dimension filter, permission boundary); registry wiring covered by the existing
    `CanonicalSchemaTest` / explorer tests extended with the new entities/domains.
  - Frontend: Vitest for the tab components (loading/error/empty/rows), the table column
    definitions, and the two graph builders (`lineage-graph`, `relationship-graph`); demo-fixture
    parity with the live types.
  - CI checks: `./gradlew test spotlessCheck` and `bun run typecheck && bun run lint && bun run test`.

## 8. Decisions and open questions

- **Table columns (§4.1) are the owner-facing contract** — they encode what the owner sees per row;
  confirmed in brainstorming.
- **Single "Sales detail" screen with tabs** over three separate nav items — confirmed in
  brainstorming.
- **Row-level tables read canonical, charts read resolved** — consistent with the existing Data
  explorer read split.
- Open: the entity-relationship diagram (§6.3) requires a "find payments + items for a sale number"
  query. Leaning toward a dedicated `GET /api/sales/{saleNumber}/detail` endpoint returning that
  sale's tenders, line items, and (when voided) its deleted-order node, to avoid over-fetching from
  the 1.8M-line table. To be confirmed during planning.
