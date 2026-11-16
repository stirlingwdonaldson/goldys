ALTER TABLE workspace_job DROP CONSTRAINT workspace_job_state_check;
ALTER TABLE workspace_job ADD CONSTRAINT workspace_job_state_check
CHECK (state IN ('QUEUED','RUNNING','COMPLETED','CANCELLED','INTERRUPTED','FAILED','BUDGET_EXHAUSTED'));
