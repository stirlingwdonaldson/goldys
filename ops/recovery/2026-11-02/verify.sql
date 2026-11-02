-- Run only in a default_transaction_read_only=on production session.
-- After a new pull, compare these counts/amounts with its newly captured reports.
SELECT trading_date, count(*) AS current_rows, sum(amount) AS source_amount
FROM source_product_sale_fact
WHERE source_system = 'CTB' AND superseded_at IS NULL
  AND trading_date IN ('2026-08-07','2026-08-09','2026-08-15',
                       '2026-09-05','2026-09-22','2026-10-24')
GROUP BY trading_date ORDER BY trading_date;

SELECT id, status, started_at, completed_at, fetched_count, persisted_count, failure_summary
FROM ingestion_run WHERE connector_name = 'ctb-revenue'
ORDER BY started_at DESC LIMIT 5;
