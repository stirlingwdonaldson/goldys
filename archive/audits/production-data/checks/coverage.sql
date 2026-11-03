-- C01: Delivery classification and freshness; never output source payload values.
SELECT source_system,fetch_method,content_type,fetcher_identity,count(*) AS raw_records,
       count(parsed_payload) AS parsed_payload_present,min(fetched_at),max(fetched_at),
       sum(payload_byte_length) AS bytes,max(payload_byte_length) AS largest_payload
FROM raw_record GROUP BY 1,2,3,4 ORDER BY 1,4;
-- C02: Connector run accounting across all retained history.
SELECT source_system,connector_name,status,count(*) AS runs,min(started_at),max(started_at),
       sum(fetched_count) AS fetched,sum(persisted_count) AS persisted,
       count(*) FILTER (WHERE completed_at IS NULL) AS incomplete_runs
FROM ingestion_run GROUP BY 1,2,3 ORDER BY 1,2,3;
-- C03: Recent-window status, bounded by indexed started_at.
SELECT source_system,connector_name,status,count(*) AS runs,max(started_at),
       sum(fetched_count),sum(persisted_count)
FROM ingestion_run WHERE started_at >= now()-interval '7 days' GROUP BY 1,2,3 ORDER BY 1,2,3;
SELECT source_system,failure_type,count(*),min(occurred_at),max(occurred_at)
FROM ingestion_failure GROUP BY 1,2 ORDER BY 1,2;
SELECT flag_type,count(*),count(DISTINCT invoice_number),min(occurred_at),max(occurred_at)
FROM invoice_ingest_flag GROUP BY 1 ORDER BY 1;
-- C04: Raw records linked to any canonical history/current facts (many-to-one is legitimate).
WITH refs AS (
 SELECT raw_record_id,'daily_sales' AS domain,superseded_at FROM canonical_daily_sales
 UNION ALL SELECT raw_record_id,'product_sales',superseded_at FROM canonical_product_sales
 UNION ALL SELECT raw_record_id,'invoice',superseded_at FROM canonical_invoice
 UNION ALL SELECT raw_record_id,'invoice_line',superseded_at FROM canonical_invoice_line
 UNION ALL SELECT raw_record_id,'labour',superseded_at FROM canonical_labour_entry
 UNION ALL SELECT raw_record_id,'reservation',superseded_at FROM canonical_reservation
 UNION ALL SELECT raw_record_id,'stock_count',superseded_at FROM canonical_stock_count
 UNION ALL SELECT raw_record_id,'wastage',superseded_at FROM canonical_wastage
 UNION ALL SELECT raw_record_id,'sale_item',superseded_at FROM canonical_sale_item
 UNION ALL SELECT raw_record_id,'shift',superseded_at FROM canonical_shift
), links AS (SELECT raw_record_id,count(*) AS facts,count(*) FILTER (WHERE superseded_at IS NULL) AS current_facts FROM refs GROUP BY 1)
SELECT r.source_system,r.fetcher_identity,count(*) AS raw_records,
       count(l.raw_record_id) AS raw_with_any_canonical_history,
       count(*) FILTER (WHERE l.current_facts>0) AS raw_with_current_canonical,
       coalesce(sum(l.facts),0) AS canonical_versions
FROM raw_record r LEFT JOIN links l ON l.raw_record_id=r.id GROUP BY 1,2 ORDER BY 1,2;
-- C05: Retained raw exact-content repeats (not necessarily business duplicates).
SELECT source_system,count(*) AS raw_rows,count(DISTINCT payload_sha256) AS distinct_payloads
FROM raw_record GROUP BY 1 ORDER BY 1;
-- C06: Run counters versus raw ledger (not canonical success).
WITH r AS (SELECT ingestion_run_id,count(*) AS ledger_count FROM raw_record GROUP BY 1)
SELECT i.source_system,i.connector_name,i.status,count(*) AS runs,
       count(*) FILTER (WHERE i.persisted_count<>coalesce(r.ledger_count,0)) AS ledger_counter_mismatches,
       count(*) FILTER (WHERE i.status='SUCCESS' AND i.persisted_count=0) AS empty_success_runs
FROM ingestion_run i LEFT JOIN r ON r.ingestion_run_id=i.id GROUP BY 1,2,3 ORDER BY 1,2,3;
