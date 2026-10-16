-- C2-01: Stage accounting per dataset — separates "raw stored" from "parsed",
-- "canonicalized" and "enriched" so a push that stored bytes but failed to
-- transform stays visible (audit F06/F09). Append-only facts.
SELECT source_system, dataset, stage, outcome, count(*) AS stage_rows,
       max(occurred_at) AS last_occurred
FROM ingestion_stage GROUP BY 1,2,3,4 ORDER BY 1,2,3,4;
-- C2-02: Per-dataset freshness: latest raw-stored vs latest canonicalized.
WITH raw AS (
  SELECT source_system,dataset,max(occurred_at) AS last_raw
  FROM ingestion_stage WHERE stage='RAW_STORED' AND outcome='SUCCESS'
  GROUP BY 1,2
), canon AS (
  SELECT source_system,dataset,max(occurred_at) AS last_canon
  FROM ingestion_stage WHERE stage='CANONICALIZED' AND outcome='SUCCESS'
  GROUP BY 1,2
)
SELECT r.source_system,r.dataset,r.last_raw,c.last_canon
FROM raw r LEFT JOIN canon c USING(source_system,dataset)
ORDER BY 1,2;
-- C2-03: Run-level ledger vs persisted-count accounting.
WITH r AS (SELECT ingestion_run_id,count(*) AS ledger FROM raw_record GROUP BY 1)
SELECT i.source_system,i.connector_name,i.status,count(*) AS runs,
       count(*) FILTER (WHERE i.persisted_count<>coalesce(r.ledger,0)) AS counter_mismatch,
       count(*) FILTER (WHERE i.status='SUCCESS' AND i.persisted_count=0) AS empty_success
FROM ingestion_run i LEFT JOIN r ON r.ingestion_run_id=i.id
GROUP BY 1,2,3 ORDER BY 1,2,3;
