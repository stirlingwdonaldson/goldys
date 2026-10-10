# Lightspeed Payments & Deleted-Sales — Design & Field Mapping

Date: 2026-10-10 · Status: draft (capture complete, connector build pending)

## 1. Context

Three new Lightspeed Insights scheduled reports were captured and inspected
(see capture dir `/srv/ai/lightspeed-capture/`):

| Webhook | View | Rows | Columns | Date range | Verdict |
|---|---|---|---|---|---|
| `/webhook/1` sales-details | `salelines` | 139,030 | 117 | 2020-11-23 → 2026-10-09 | **dropped — incomplete** |
| `/webhook/2` all-payments | `payments` | 972,820 | 27 | 2020-11-23 → 2026-10-10 | **ingest** |
| `/webhook/4` all-deleted-orders | `deleted_sales` | 3,197 | 41 | 2020-11-23 → 2026-10-09 | **ingest** |

`sales-details` (`salelines`) is missing ~90% of sales: its sale numbers
(98,916) are a strict subset of `payments`' sale numbers (970,947), and the
gap is register-specific (current main-bar registers "Goldy's Main Bar",
"BAR.2", "Bar 3" are 1–4% populated in `salelines`). This is a Lightspeed-side
data gap, not a filter/limit; the `sale_date` filter was set to `7 years` and
the payload came back byte-identical. Line-item detail is deferred until a
complete source exists.

## 2. Payload envelope (both reports)

Each webhook is a JSON body (content-type `application/json`) in Looker's
scheduled-plan format. The CSV is the `attachment.data` string:

```
{
  "type": "query",
  "scheduled_plan": {
    "scheduled_plan_id": "...",
    "title": "all-payments" | "all-deleted-orders",
    "query": { "view": "payments" | "deleted_sales", "fields": [...],
               "filters": {...}, "sorts": [...], "limit": "-1" }
  },
  "attachment": { "mimetype": "text/csv", "extension": "csv", "data": "<CSV>" },
  "data": null,
  "form_params": {}
}
```

**Critical gotcha:** `query.fields` order **does not match** the CSV column
order. The connector MUST key off the **CSV header (display names)**, never the
array index of `query.fields`. The mappings below are the display-name → field
mappings, resolved by hand from the captured data.

## 3. `all-payments` — view `payments`

One row per payment tender. A single sale can be split across several tenders
(e.g. Tyro + Cash + Manual Tyro), so `sale_number` is **not** unique.

### 3.1 Column mapping (27 columns, display name → meaning → canonical field)

| # | Display name | Technical field | Keep? |
|---|---|---|---|
| 1 | Staff Sale Closed Staff Name | `payments.staff_name` | staffName |
| 2 | Staff Sale Closed Staff Code | `payments.staff_code` | staffCode |
| 3 | Customer Email | `customer.email` | — (3.9% populated; skip) |
| 4 | Customer Has Customer (Yes / No) | `payments.has_customer` | — |
| 5 | Customer Name | `customer.name` | customerName (sparse) |
| 6 | Payment Data Clearing Account | `payment_type.clearing_acct` | clearingAccount |
| 7 | Payment Data Created Date | `payments.created_date` | **tradingDate** |
| 8 | Payment Data LS Pay Payment Mode | `payments.lspay_payment_mode` | lspayPaymentMode |
| 9 | Payment Data Payment Source Type | `payment_type.sourcetype` | paymentSourceType |
| 10 | Payment Data Payment Type Code | `payment_type.paymenttypecode` | paymentTypeCode |
| 11 | Payment Data Payment Type Name | `payment_type.paymenttypename` | paymentTypeName |
| 12 | Payment Data Reconciled | `payments.reconciled` | reconciled |
| 13 | Payments Sale Number | `payments.sale_number` | saleNumber |
| 14 | Reconciliation Reconciliation Date | `cashups.reconciliation_date` | — (cashup; skip v1) |
| 15 | Reconciliation Reconciliation End Date | `cashups.reconciliation_end_date` | — |
| 16 | Reconciliation Reconciliation Start Date | `cashups.reconciliation_start_date` | — |
| 17 | Register Closed Register Code | `register_sale_closed.closed_register_code` | registerCode |
| 18 | Register Closed Register Name | `register_sale_closed.closed_register_name` | registerName |
| 19 | Site Numeric ID | `site.numeric_id` | siteId |
| 20 | Payment Data Amount | `payments.amount` | **amount** |
| 21 | Payment Data Average Amount | `payments.average_amount` | — (derived) |
| 22 | Payment Data Number Of Payments | `payments.count` | paymentCount |
| 23 | Payment Data Payment Surcharge | `payments.payment_surcharge` | surcharge |
| 24 | Payment Data Number of Tips | `payments.count_tip` | tipCount |
| 25 | Payment Data Tendered | `payments.tendered` | tendered |
| 26 | Payment Data Tip | `payments.tip` | **tip** |
| 27 | Payment Data Total Payments With Tip | `payments.payments_with_tip` | — (= tendered + tip) |

Derived (skip, recomputable): average amount, total-with-tip.

### 3.2 Identity (open decision)

No payment id is present. `sale_number` repeats (split tender), and even
`(sale_number, payment_type, created_date)` is not unique in 40 rows (two same
-type tenders on different registers). Provisional `source_record_ref`:

```
sale_number + ":" + payment_type_code + ":" + register_code + ":" + created_date
```

This is unique in the captured data and stable across re-deliveries. If
Lightspeed can expose a payment id, prefer it. Flagged for review.

## 4. `all-deleted-orders` — view `deleted_sales`

One row per deleted order. `sale_number` is unique (3,197 / 3,197) → clean
natural key.

### 4.1 Column mapping (41 columns, display name → technical → keep?)

| # | Display name | Technical field | Keep? |
|---|---|---|---|
| 1 | Customer Email | `customer.email` | — (sparse) |
| 2 | Customer Has Customer (Yes / No) | `deleted_sales.has_customer` | — |
| 3 | Customer Name | `customer.name` | customerName (sparse) |
| 4 | Deleted Orders Order Opened Date | `deleted_sales.sale_opened_date` | **tradingDate** |
| 5 | Deleted Orders Order Type | `deleted_sales.order_type_addon_type` | orderType |
| 6 | Order Source Addon Name | `oauth2_clients.addon_name` | — |
| 7–10 | Reconciliation … Date/End/Number/Start | `cashups.*` | — (skip v1) |
| 11 | Register Opened Register Code | `register_order_opened.opened_register_code` | openedRegisterCode |
| 12 | Register Opened Register Name | `register_order_opened.opened_register_name` | openedRegisterName |
| 13 | Register Deleted Register Name | `register_sale_deleted.deleted_register_name` | deletedRegisterName |
| 14 | Register Deleted Register Code | `register_sale_deleted.deleted_register_code` | deletedRegisterCode |
| 15 | Sales Adjustments Has Promotion (Yes / No) | `deleted_sales.has_promotion` | — |
| 16 | Sales Data Note | `deleted_sales.note` | **note** |
| 17 | Sales Data Sale Number | `deleted_sales.sale_number` | **saleNumber (identity)** |
| 18 | Sales Data Sale Opened Minute 10 | `deleted_sales.sale_opened_minute_10` | — |
| 19 | Site Numeric ID | `site.numeric_id` | siteId |
| 20 | Staff Staff Name | `deleted_sales.staff_name` | staffName |
| 21 | Staff Staff Code | `deleted_sales.staff_code` | staffCode |
| 22 | Staff Order Opened Staff Name | `deleted_sales.order_opened_staff_name` | — |
| 23 | Staff Order Deleted Staff Name | `deleted_sales.order_deleted_staff_name` | deletedByStaffName |
| 24 | Staff Order Opened Staff Code | `deleted_sales.order_opened_by_staff_code` | — |
| 25 | Staff Order Deleted Staff Code | `deleted_sales.order_deleted_by_staff_code` | deletedByStaffCode |
| 26 | Tables Table Number | `deleted_sales.table_number` | tableNumber |
| 27 | Tables Has Table (Yes / No) | `deleted_sales.has_table` | — |
| 28–31 | Deleted Orders Average … / Total … Duration | averages/durations | — (derived) |
| 29 | Deleted Orders Total Tax | `deleted_sales.total_tax` | **totalTax** |
| 32 | Deleted Orders Total Inc Tax | `deleted_sales.total_inc_tax` | **totalIncTax** |
| 33 | Deleted Orders Total Ex Tax | `deleted_sales.total_ex_tax` | **totalExTax** |
| 34 | Deleted Orders Orders | `deleted_sales.sales` | — (count) |
| 35–38 | Average Transaction Value … / Merchant Count | derived | — |
| 39 | Sales Data Total Cost | `deleted_sales.total_cost` | totalCost |
| 40 | Sales Data Cost Inc Tax | `deleted_sales.cost_inc_tax` | — |
| 41 | Sales Data Cost Ex Tax | `deleted_sales.cost_ex_tax` | — |

### 4.2 Identity

`source_record_ref = sale_number` (unique). `logicalEntityId =
UUID.nameUUIDFromBytes("deleted-sale:" + saleNumber)`.

## 5. Canonical entities (proposed)

Both are **single-source** (`source_system = "LIGHTSPEED"`) — no reconciliation
against a second source; the resolved layer passes them through with
resolution type `single` (same pattern as reservations/labour).

### `CanonicalPayment` (`canonical_payment`)

Fact fields (immutable): `tradingDate` (LocalDate), `saleNumber` (String),
`paymentTypeCode`, `paymentTypeName`, `paymentSourceType`, `lspayPaymentMode`,
`clearingAccount`, `amount` (BigDecimal), `tip`, `tendered`, `surcharge`,
`paymentCount` (Integer), `tipCount` (Integer), `reconciled` (String),
`registerCode`, `registerName`, `staffName`, `staffCode`, `siteId` (String),
`customerName` (String, nullable).

Grain: one row per (source_system, source_record_ref) — see §3.2 for the
provisional ref.

### `CanonicalDeletedSale` (`canonical_deleted_sale`)

Fact fields (immutable): `tradingDate` (LocalDate), `saleNumber` (String),
`orderType`, `note` (String, nullable), `totalIncTax`, `totalExTax`, `totalTax`,
`totalCost` (BigDecimal, nullable), `openedRegisterCode`, `openedRegisterName`,
`deletedRegisterCode`, `deletedRegisterName`, `staffName`, `staffCode`,
`deletedByStaffName`, `deletedByStaffCode`, `tableNumber` (String, nullable),
`siteId`, `customerName` (String, nullable).

Grain: one row per (source_system, source_record_ref = sale_number).

## 6. Ingestion path (push, mirrors existing Lightspeed webhook)

- `fetcherIdentity`: `lightspeed-payments` and `lightspeed-deleted-sales`.
- `fetchMethod`: `FILE_EXPORT` (scheduled report export, like the other
  Lightspeed reports).
- Endpoints: `POST /api/ingest/lightspeed-payments` and
  `POST /api/ingest/lightspeed-deleted-sales`, token-gated via
  `X-Webhook-Token` header / `token` query param against
  `lightspeed.webhook-token` (fail closed when unset) — identical to
  `LightspeedIngestController`.
- Flow: `ingestPush(...)` (raw-first) → extract `attachment.data` → CSV parse
  by header name → `<Domain>Ingest.record(...)` with raw id.

## 7. Downstream surface (defer or include — see plan)

Single-source, so no reconciliation resolver is required, but the architecture
still demands the canonical → resolved → semantic → application → API chain
before any consumer reads the data. Proposed first slice ships canonical +
ingest + a minimal resolved projection + semantic query + REST read endpoint,
and defers override service, metrics and Ask Goldy's tool until a concrete
question exists.

## 8. Open questions

1. **Payments identity** (§3.2) — confirm the provisional `source_record_ref`
   or obtain a payment id from Lightspeed.
2. **Scope of first slice** — ingest+canonical only, or full resolved+API
   surface now?
3. Payment `source_type` numeric codes need a name mapping (0=Cash, 1=Manual,
   4=Tyro, 70000=Lightspeed Payments, 1000583=Mr Yum, 1000132=me&u) — store the
   code + the human name, or normalize on ingest?

## 9. Sale items (line items) — added after the data was confirmed complete

Originally deferred because the "sales-details" report looked ~90% incomplete.
The gap was traced to a single field: **`Product Salelines → Product ID`
(`product_salelines.product_id`)**. Joining to that link table silently drops
~92% of rows (1,832,655 → 138,360). Every other field — payments, adjustments,
reconciliation, product, customer, staff, register — returns the full line
count. Scheduling the report **without** that one field yields the complete
~1.83M receipt lines.

### Field mapping (17 columns captured, keyed by display name)

| Display name | Technical field | Canonical field |
|---|---|---|
| Sales Data Receipt Line ID | `salelines.id` | **source_record_ref** |
| Sales Data Sale Closed Date | `salelines.sale_date` | **tradingDate** |
| Saleline Payments Sale Number | `saleline_payments.sale_number` | saleNumber |
| Products Product Name | `product.product_name` | itemName |
| Products Product Number | `product.product_no` | productNumber |
| Products SKU | `product.sku` | sku |
| Products POS Category Name | `category.category_name` | categoryName |
| Sales Data Product Quantity | `salelines.quantity` | quantitySold |
| Sales Data Total Inc Tax | `salelines.total_inc_tax` | amount (line total) |
| Advanced Dimensions Sold Price Inc Tax | `salelines.sold_price_inc_tax` | soldPriceIncTax |
| Sales Data Total Tax | `salelines.total_tax` | totalTax |
| Sales Data Cost Inc Tax | `salelines.cost_inc_tax` | costIncTax |
| Sales Data Order Type | `salelines.order_type_addon_type` | orderType |
| Sales Data Sale Type | `salelines.sale_type` | saleType |
| Staff Sale Closed Staff Name | `salelines.sale_closed_staff_name` | staffName |
| Register Closed Register Name | `register_sale_closed.closed_register_name` | registerName |
| Tables Table Number | `salelines.table_number` | tableNumber |

### Entity + identity

Extends the existing (previously unused) `canonical_sale_item` scaffold
(V35 adds the line-detail columns). `source_record_ref = receiptLineId`
(unique); `logicalEntityId = UUID.nameUUIDFromBytes("sale-item:" +
receiptLineId)`.

### Delivery chunking

The full report is ~1–2 GB (1.83M lines × 116 columns) and does not deliver in
one webhook. Ingest in date-range chunks (yearly, or half-yearly for the
busiest years). Lightspeed's "is in the range" filter excludes the end day, so
add one day to the desired end date. Data range: 2020-11-23 → present.

