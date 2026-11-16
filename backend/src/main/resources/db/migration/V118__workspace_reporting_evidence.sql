-- Evidence is captured by Spring before data leaves the governed reporting boundary.
-- It does not replace the conservative policy stamp or assert complete context provenance.
CREATE TABLE workspace_reporting_evidence (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES workspace_job(id) ON DELETE CASCADE,
    tool varchar(64) NOT NULL,
    checks jsonb NOT NULL CHECK (jsonb_typeof(checks) = 'array' AND jsonb_array_length(checks) > 0),
    created_at timestamp(6) with time zone NOT NULL DEFAULT now()
);
CREATE INDEX workspace_reporting_evidence_job ON workspace_reporting_evidence(job_id);
