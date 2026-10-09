# 02 — Live PostgreSQL inventory

**Production fact:** 34 application tables in `public`; no application SQL views found (S01–S04).
The schema is an evidence/versioned-observation/read-model architecture. It is not an entity-complete venue model.
Full native column types, ordinals, defaults and nullability: `evidence/schema.csv` S01 (348 columns).
Enforced keys/checks: S02; all indexes: S03. `table-inventory.csv` contains exact populations and field-level
null counts/date ranges/distinct identity counts. These metadata artefacts are part of the inventory, not samples.

## Exact population at inspection

Small fact-table heaps (largest structured table ~4.3 MB) justified bounded exact aggregate scans.
Raw ledger is ~394 MiB including TOAST; population probes inspect metadata/null presence, not all payload bytes.
`pg_stat_user_tables` estimates differed from exact counts, so the following are **exact** (P01).

| Table | All rows | Current | Superseded |
|---|---:|---:|---:|
| raw_record | 3,715 | — | — |
| ingestion_run | 2,719 | — | — |
| ingestion_failure | 21 | — | — |
| canonical_daily_sales | 203 | 195 | 8 |
| canonical_product_sales | 13,272 | 13,185 | 87 |
| canonical_invoice | 2,333 | 1,167 | 1,166 |
| canonical_invoice_line | 15,876 | 6,156 | 9,720 |
| canonical_labour_entry | 0 | 0 | 0 |
| canonical_reservation | 0 | 0 | 0 |
| canonical_sale_item | 0 | 0 | 0 |
| canonical_shift | 0 | 0 | 0 |
| canonical_stock_count | 0 | 0 | 0 |
| canonical_wastage | 0 | 0 | 0 |
| resolved_daily_sales | 187 | — | — |
| resolved_product_sales | 13,185 | — | — |
| resolved_inventory_day | 164 | — | — |
| resolved_labour_day | 0 | — | — |
| resolved_reservation_day | 0 | — | — |
| invoice_ingest_flag | 916 | — | — |
| daily_sales_override | 2 | 2 | 0 |
| product_sales_override | 0 | 0 | 0 |
| inventory_override | 0 | 0 | 0 |
| labour_override | 0 | 0 | 0 |
| reservation_override | 0 | 0 | 0 |
| resolution_rule | 4 | 1 | 3 |
| reconciliation_exception | 0 | — | — |
| saved_dashboard | 4 | — | — |
| saved_dashboard_revision | 4 | — | — |
| saved_dashboard_share | 0 | — | — |
| conversation_thread | 3 | — | — |
| conversation_message | 6 | — | — |
| permission | 12 | — | — |
| user_account | 4 | — | — |
| flyway_schema_history | 31 | — | — |

Current/superseded is an observation-version distinction, not a soft-delete convention applied to all tables.
Private conversation/user content was not queried; only aggregate row/null/date counts were collected.

## Native storage and keys

All tables have primary keys. Canonical versions use UUID `id`; `logical_entity_id` represents the
application's logical identity. `source_system`, `source_record_ref`, `raw_record_id` record provenance.
The ten canonical tables enforce **raw-record FKs**, not inter-business-entity FKs.

- Raw/run/failure linkage: `raw_record.ingestion_run_id → ingestion_run.id`, failures likewise.
- Header/line business linkage: **no FK**, joined by `invoice_number` in Java.
- Product-day linkage: **no product FK**, `product_name_key` plus date is the grain.
- Resolved keys: date; date/product; date/department; date/service-period. They contain no contributing-fact links.
- Current uniqueness: partial indexes for daily `(trading_date, source_system)`, product
  `(product_name_key,trading_date,source_system)`, invoice/line/reservation/labour/sale-item/shift
  `(source_system,source_record_ref)`. Stock count/wastage lack equivalent current-source unique indexes.
- Rule/override partial unique indexes enforce one current rule/decision per entity/field or natural key.
- Dashboard revisions/shares and conversation ownership have actual FKs; see the complete ER diagram.
- No database CHECK enforces business monetary equality, positive party sizes, valid version intervals,
  invoice-line/header consistency or stock/recipe identities. Application checks are partial.

## Field families

- Money/quantities use `numeric` with precision/scale detailed in S01; main monetary facts are 4-decimal values.
- Business dates use `date`; observations and instants use `timestamp(6) with time zone`.
  Flyway installation time is timestamp without timezone.
- Bitemporal fields: `valid_from`, nullable `valid_to`, `recorded_at`, nullable `superseded_at`.
  Every populated canonical version has `valid_from=recorded_at`, and every `valid_to` is null (T01).
- JSONB: raw `parsed_payload`, rule `source_priority`, dashboards `widgets`/`filters`, revision `document`,
  conversation `tool_trace`. No native array columns found. `source_priority` is a JSON array, not PostgreSQL ARRAY.
- Raw bytes: `bytea`, SHA-256 and byte length retained. All **3,715 parsed_payload values are null**.
  This is not evidence parsing never happened: current parsers read bytes without updating that column.
- There is **no venue table/venue FK**, transaction header, product master, supplier master, recipe,
  ingredient, movement or employee table. `user_account` is an application login, not an employee source entity.

## Population, dates and important current-field missingness

Sales: CTB 187 current date observations, Lightspeed 8; 190-day CTB calendar span with 3 missing dates.
Products: 520 names, 93 dates, 13,185 current rows. All structured product sales are **CTB**.
Invoices: 100 distinct supplier names in retained header versions; 1,167 current number keys.
Source snapshot instead has 101 supplier IDs and 1,174 invoice IDs (R01/payload analysis).

| Current field | Null count / population | Null % | Interpretation |
|---|---:|---:|---|
| invoice account_number / tax_code / freight_gst_amount | 1,167 / 1,167 each | 100% | Columns exist, service explicitly writes null |
| invoice pdf_filename | 5 / 1,167 | 0.43% | No canonical filename for those headers |
| invoice_line stock_code | 478 / 6,156 | 7.76% | Identity gap; product-name fallback remains |
| invoice_line category | 6,156 / 6,156 | 100% | No category analysis possible |
| invoice_line uom | 2,124 / 6,156 | 34.50% | Partial PDF extraction/linkage |
| invoice_line unit_quantity | 5,201 / 6,156 | 84.49% | Most base-unit denominators unknown |
| invoice_line pack_size | 5,825 / 6,156 | 94.62% | Detailed pack interpretation mostly unavailable |
| invoice_line wet_amount | 5,978 / 6,156 | 97.11% | Not all lines should have WET; null is not proven zero |
| resolved_inventory_day wastage / stock_on_hand | 164 / 164 each | 100% | Projector deliberately writes null |

Header total/ex-tax/GST/freight fields are populated for all current headers, but source accuracy is separate.
One header and 12 lines date to 1989-10-01. Latest invoice date 2026-10-09 is the audit day, not a future-date violation.
Invoices were recorded starting 2026-10-08; they are historical imports, not continuous data since 1989.

Earliest/latest ingestion and every table's timestamp/date field ranges are in P01.
No percentages are computed for zero-row tables: absence of a population is not 100% field completeness.

## Integrity results

I03: no current headers without lines, no current invoice-number orphan keys, no ambiguous current header keys.
**This does not validate invoice identity**: collided suppliers have already collapsed into one canonical key.
18 identical-value line groups / 79 excess candidate lines; 9/960 stock codes have multiple names;
18/946 product names have multiple stock codes. Code/name differences may be legitimate aliases or packaging.
T01: no orphan raw refs and no observed discontinuity in populated system-time chains.
R01: 5 raw AJAX invoice-number keys absent from canonical; source expected completeness remains unknown.

See `current-entity-model.mmd` for enforced versus application-only relations and report 06 for join semantics.
