-- Synthetic fixture data for validating the Phase 1 audit probes.
-- NO real customer, employee, guest or supplier records. Every value is an
-- obviously synthetic sentinel. This file is applied to a throwaway Postgres
-- container that already has the real schema (all Flyway migrations) applied.
--
-- Load order note: this is meant to run AFTER the migrations, so the append-only
-- trigger and all tables exist.

BEGIN;

-- Two ingestion runs.
INSERT INTO ingestion_run (id, source_system, connector_name, status, started_at, completed_at, fetched_count, persisted_count)
VALUES
  ('00000000-0000-0000-0000-0000000000a1','CTB','ctb-revenue','SUCCESS', now() - interval '2 days', now() - interval '2 days', 2, 2),
  ('00000000-0000-0000-0000-0000000000a2','LIGHTSPEED','lightspeed-insights','PARTIAL', now() - interval '1 day', now() - interval '1 day', 1, 1);

-- A few raw records.
INSERT INTO raw_record (id, source_system, fetch_method, content_type, fetcher_identity, fetched_at, ingestion_run_id, payload_bytes, payload_sha256, payload_byte_length, character_encoding, source_record_ref)
VALUES
  ('00000000-0000-0000-0000-0000000000b1','CTB','FILE_EXPORT','text/csv','ctb-invoices', now() - interval '2 days', '00000000-0000-0000-0000-0000000000a1', decode('53796e74686574696320696e766f69636520435356','hex'), 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 24, 'UTF-8', NULL),
  ('00000000-0000-0000-0000-0000000000b2','LIGHTSPEED','FILE_EXPORT','application/json','lightspeed-insights', now() - interval '1 day', '00000000-0000-0000-0000-0000000000a2', decode('7b2273796e746865746963223a747275657d','hex'), 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 18, 'UTF-8', NULL);

-- One ingestion stage fact (raw stored but not yet canonicalized) and a failure.
INSERT INTO ingestion_stage (id, ingestion_run_id, source_system, dataset, stage, outcome, occurred_at)
VALUES
  ('00000000-0000-0000-0000-0000000000c1','00000000-0000-0000-0000-0000000000a2','LIGHTSPEED','daily_sales','RAW_STORED','SUCCESS', now() - interval '1 day');

INSERT INTO ingestion_failure (id, source_system, failure_type, detail, occurred_at, ingestion_run_id, stack_trace)
VALUES
  ('00000000-0000-0000-0000-0000000000c2','CTB','CONNECTOR_FETCH_FAILED','synthetic non-JSON response', now() - interval '1 day', '00000000-0000-0000-0000-0000000000a2', NULL);

-- Daily sales: two sources disagree on the same date (a genuine conflict).
INSERT INTO canonical_daily_sales (id, logical_entity_id, trading_date, source_system, source_record_ref, raw_record_id, total_sales, gst_total, net_total, valid_from, recorded_at)
VALUES
  ('00000000-0000-0000-0000-0000000000d1','11111111-1111-1111-1111-111111111111', DATE '2026-10-02','CTB','syn:ctb:2026-10-02','00000000-0000-0000-0000-0000000000b1', 1000.00, 90.00, 910.00, now(), now()),
  ('00000000-0000-0000-0000-0000000000d2','22222222-2222-2222-2222-222222222222', DATE '2026-10-02','LIGHTSPEED','syn:ls:2026-10-02','00000000-0000-0000-0000-0000000000b2', 1010.00, 91.00, 919.00, now(), now());

INSERT INTO resolved_daily_sales (trading_date, total_sales, net_sales, resolution_type, authoritative_source, has_conflict, resolved_at)
VALUES (DATE '2026-10-02', 1000.00, 910.00, 'rule', 'CTB', true, now());

-- Invoice header + two lines, including a supplier-scoped identity example.
INSERT INTO canonical_invoice (id, logical_entity_id, source_system, source_record_ref, raw_record_id, supplier_name, invoice_number, invoice_date, total_amount, amount_ex_tax, gst_amount, freight_amount, account_number, tax_code, valid_from, recorded_at)
VALUES
  ('00000000-0000-0000-0000-0000000000e1','33333333-3333-3333-3333-333333333333','CTB','SYN-1001','00000000-0000-0000-0000-0000000000b1','SYNTHETIC SUPPLIER A','SYN-1001', DATE '2026-10-01', 110.00, 100.00, 10.00, 0.00, 'SYN-ACCT', 'GST', now(), now());

INSERT INTO canonical_invoice_line (id, logical_entity_id, source_system, source_record_ref, raw_record_id, invoice_number, invoice_date, product_name_key, quantity, unit_cost, line_total, stock_code, uom, unit_quantity, pack_size, wet_amount, origin, line_total_inc_tax, line_gst_amount, line_tax_rate, inc_gst, valid_from, recorded_at)
VALUES
  ('00000000-0000-0000-0000-0000000000f1','44444444-4444-4444-4444-444444444444','CTB','SYN-1001:1','00000000-0000-0000-0000-0000000000b1','SYN-1001', DATE '2026-10-01','synthetic burger patty', 10, 5.00, 50.00, 'SYN-STOCK-001', 'each', 1, 10, 0.00, 'CSV', 55.00, 5.00, 0.10, true, now(), now()),
  ('00000000-0000-0000-0000-0000000000f2','55555555-5555-5555-5555-555555555555','CTB','SYN-1001:2','00000000-0000-0000-0000-0000000000b1','SYN-1001', DATE '2026-10-01','synthetic bun', 10, 5.00, 50.00, 'SYN-STOCK-002', 'each', 1, 10, 0.00, 'CSV', 55.00, 5.00, 0.10, true, now(), now());

-- One payment, one sale item, one deleted sale.
INSERT INTO canonical_payment (id, logical_entity_id, source_system, source_record_ref, raw_record_id, trading_date, sale_number, payment_type_code, payment_type_name, amount, tip, tendered, surcharge, payment_count, tip_count, reconciled, valid_from, recorded_at)
VALUES
  ('00000000-0000-0000-0000-0000000000f3','66666666-6666-6666-6666-666666666666','LIGHTSPEED','SYN-SALE-1|card|reg1|2026-10-02','00000000-0000-0000-0000-0000000000b2', DATE '2026-10-02','SYN-SALE-1','card','Card', 50.00, 5.00, 55.00, 0.00, 1, 1, true, now(), now());

INSERT INTO canonical_sale_item (id, logical_entity_id, source_system, source_record_ref, raw_record_id, trading_date, item_name, quantity_sold, amount, sale_number, sku, category_name, sold_price_inc_tax, total_tax, cost_inc_tax, valid_from, recorded_at)
VALUES
  ('00000000-0000-0000-0000-0000000000f4','77777777-7777-7777-7777-777777777777','LIGHTSPEED','SYN-RECEIPT-1','00000000-0000-0000-0000-0000000000b2', DATE '2026-10-02','synthetic burger', 2, 30.00, 'SYN-SALE-1', 'SYN-SKU-1', 'Burgers', 33.00, 3.00, 12.00, now(), now());

INSERT INTO canonical_deleted_sale (id, logical_entity_id, source_system, source_record_ref, raw_record_id, trading_date, sale_number, total_inc_tax, total_ex_tax, total_tax, valid_from, recorded_at)
VALUES
  ('00000000-0000-0000-0000-0000000000f5','88888888-8888-8888-8888-888888888888','LIGHTSPEED','SYN-DELETED-1','00000000-0000-0000-0000-0000000000b2', DATE '2026-10-02','SYN-DELETED-1', 20.00, 18.18, 1.82, now(), now());

COMMIT;
