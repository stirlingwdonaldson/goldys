-- Pending commands are durable; Pi remains the only conversation/context engine.
CREATE TABLE workspace_follow_up (
    sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES workspace_job(id) ON DELETE CASCADE,
    request_id uuid NOT NULL,
    prompt text NOT NULL CHECK (length(prompt) BETWEEN 1 AND 16000),
    state varchar(16) NOT NULL CHECK (state IN ('QUEUED','TAKEN','CANCELLED')),
    UNIQUE(job_id, request_id)
);
CREATE INDEX workspace_follow_up_queue ON workspace_follow_up(job_id, sequence) WHERE state='QUEUED';
