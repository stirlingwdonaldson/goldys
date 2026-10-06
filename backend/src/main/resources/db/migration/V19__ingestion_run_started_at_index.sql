-- The connector-run activity window (IngestionService.activity) filters on
-- started_at >= X alone. The existing (source_system, started_at DESC) index cannot
-- serve a started_at-only range predicate (its leading column is source_system), so
-- that query would sequential-scan the ledger. This index makes it an index range scan.
CREATE INDEX idx_ingestion_run_started_at ON ingestion_run (started_at);
