-- Pi-native snapshots are the sole workspace conversation state. Legacy threads stay separate.
CREATE TABLE workspace_thread (
    id uuid PRIMARY KEY,
    user_account_id uuid NOT NULL REFERENCES user_account(id),
    policy_stamp varchar(64) NOT NULL,
    snapshot jsonb,
    revision bigint NOT NULL DEFAULT 0,
    created_at timestamp(6) with time zone NOT NULL DEFAULT now()
);
CREATE INDEX workspace_thread_owner ON workspace_thread(user_account_id, created_at DESC);
CREATE TABLE workspace_job (
    id uuid PRIMARY KEY,
    thread_id uuid NOT NULL REFERENCES workspace_thread(id) ON DELETE CASCADE,
    request_id uuid NOT NULL,
    prompt text NOT NULL CHECK (length(prompt) BETWEEN 1 AND 16000),
    state varchar(16) NOT NULL CHECK (state IN ('QUEUED','RUNNING','COMPLETED','CANCELLED','INTERRUPTED')),
    fence uuid NOT NULL,
    lease_until timestamp(6) with time zone,
    deadline timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL DEFAULT now(),
    UNIQUE(thread_id, request_id)
);
CREATE UNIQUE INDEX workspace_one_active_job ON workspace_job(thread_id) WHERE state IN ('QUEUED','RUNNING');
CREATE INDEX workspace_claim_queue ON workspace_job(created_at) WHERE state = 'QUEUED';
-- Conservative reservations remain charged for the day, including interrupted attempts.
CREATE TABLE workspace_daily_budget (
    venue_day date PRIMARY KEY,
    reserved_aud numeric(14,4) NOT NULL CHECK (reserved_aud BETWEEN 0 AND 50)
);
INSERT INTO permission(id, department, seniority, resource, can_read, can_write)
VALUES (gen_random_uuid(), 'ALL', 'OWNER', 'workspace.investigations', true, true);
