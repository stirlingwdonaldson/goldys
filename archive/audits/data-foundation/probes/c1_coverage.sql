-- C01: Raw-ledger coverage by dataset (fetcher identity). No payload values.
SELECT source_system, fetch_method, content_type, fetcher_identity,
       count(*) AS raw_records, min(fetched_at) AS first, max(fetched_at) AS last,
       sum(payload_byte_length) AS bytes, max(payload_byte_length) AS largest,
       count(DISTINCT payload_sha256) AS distinct_payloads
FROM raw_record GROUP BY 1,2,3,4 ORDER BY 1,4;
-- C02: Connector run accounting (all retained history).
SELECT source_system, connector_name, status, count(*) AS runs,
       min(started_at) AS first, max(started_at) AS last,
       sum(fetched_count) AS fetched, sum(persisted_count) AS persisted,
       count(*) FILTER (WHERE completed_at IS NULL) AS incomplete
FROM ingestion_run GROUP BY 1,2,3 ORDER BY 1,2,3;
-- C03: Ingestion failures by type (counts only, never messages).
SELECT source_system, failure_type, count(*) AS failures,
       min(occurred_at) AS first, max(occurred_at) AS last
FROM ingestion_failure GROUP BY 1,2 ORDER BY 1,2;
-- C04: Raw -> canonical coverage across every canonical domain.
WITH refs AS (
  SELECT raw_record_id,'daily_sales' AS d,superseded_at FROM canonical_daily_sales
  UNION ALL SELECT raw_record_id,'product_sales',superseded_at FROM canonical_product_sales
  UNION ALL SELECT raw_record_id,'invoice',superseded_at FROM canonical_invoice
  UNION ALL SELECT raw_record_id,'invoice_line',superseded_at FROM canonical_invoice_line
  UNION ALL SELECT raw_record_id,'labour',superseded_at FROM canonical_labour_entry
  UNION ALL SELECT raw_record_id,'reservation',superseded_at FROM canonical_reservation
  UNION ALL SELECT raw_record_id,'stock_count',superseded_at FROM canonical_stock_count
  UNION ALL SELECT raw_record_id,'wastage',superseded_at FROM canonical_wastage
  UNION ALL SELECT raw_record_id,'sale_item',superseded_at FROM canonical_sale_item
  UNION ALL SELECT raw_record_id,'shift',superseded_at FROM canonical_shift
  UNION ALL SELECT raw_record_id,'payment',superseded_at FROM canonical_payment
  UNION ALL SELECT raw_record_id,'deleted_sale',superseded_at FROM canonical_deleted_sale
  UNION ALL SELECT raw_record_id,'review',superseded_at FROM canonical_review
  UNION ALL SELECT raw_record_id,'guest',superseded_at FROM canonical_guest
  UNION ALL SELECT raw_record_id,'recipe_cost',superseded_at FROM canonical_recipe_cost
  UNION ALL SELECT raw_record_id,'recipe_ingredient',superseded_at FROM canonical_recipe_ingredient
  UNION ALL SELECT raw_record_id,'sale_recipe_link',superseded_at FROM canonical_sale_recipe_link
  UNION ALL SELECT raw_record_id,'stocktake',superseded_at FROM canonical_stocktake
  UNION ALL SELECT raw_record_id,'product',superseded_at FROM canonical_product
), links AS (
  SELECT raw_record_id,count(*) AS facts,count(*) FILTER (WHERE superseded_at IS NULL) AS current_facts
  FROM refs GROUP BY 1
)
SELECT r.source_system,r.fetcher_identity,count(*) AS raw_records,
       count(l.raw_record_id) AS raw_with_any_canonical,
       count(*) FILTER (WHERE l.current_facts>0) AS raw_with_current_canonical,
       coalesce(sum(l.facts),0) AS canonical_versions
FROM raw_record r LEFT JOIN links l ON l.raw_record_id=r.id
GROUP BY 1,2 ORDER BY 1,2;
