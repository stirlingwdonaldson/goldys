-- Request upper bounds, not invented provider invoices. Unknown calls retain their bound.
CREATE TABLE workspace_request_reservation (
    job_id uuid NOT NULL REFERENCES workspace_job(id) ON DELETE CASCADE,
    request_id uuid NOT NULL,
    kind varchar(8) NOT NULL CHECK (kind IN ('MODEL','SEARCH')),
    bound_aud numeric(14,6) NOT NULL CHECK (bound_aud > 0 AND bound_aud <= 5),
    created_at timestamp(6) with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY(job_id,request_id)
);
ALTER TABLE workspace_job ADD COLUMN budget_reconciled boolean NOT NULL DEFAULT false;
