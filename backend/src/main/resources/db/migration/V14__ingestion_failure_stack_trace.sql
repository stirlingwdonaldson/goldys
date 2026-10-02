-- Capture the full stack trace of each failure so operators can diagnose a run
-- beyond the short operator-facing detail. Nullable: not every failure carries one.
ALTER TABLE ingestion_failure ADD COLUMN stack_trace text;
