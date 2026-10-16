-- V77 - Saved query definitions (versioned) for the relational query backend.
--
-- A saved query stores a versioned QueryPlan (JSON), owned by the account that created it. The
-- plan is re-validated and re-authorized on every run, so a later permission change invalidates
-- the result rather than leaking it. Revisions snapshot the plan on each save, mirroring
-- saved_dashboard_revision.
CREATE TABLE saved_query (
    id               uuid                     NOT NULL,
    created_by       varchar(255)             NOT NULL,
    title            varchar(255)             NOT NULL,
    plan_json        text                     NOT NULL,
    current_revision integer                  NOT NULL DEFAULT 1,
    created_at       timestamp(6) with time zone NOT NULL,
    updated_at       timestamp(6) with time zone NOT NULL,
    CONSTRAINT saved_query_pkey PRIMARY KEY (id)
);

CREATE TABLE saved_query_revision (
    id         uuid                     NOT NULL,
    query_id   uuid                     NOT NULL REFERENCES saved_query(id) ON DELETE CASCADE,
    revision   integer                  NOT NULL,
    plan_json  text                     NOT NULL,
    created_by varchar(255)             NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT saved_query_revision_pkey PRIMARY KEY (id),
    CONSTRAINT uq_saved_query_revision UNIQUE (query_id, revision)
);

CREATE INDEX idx_saved_query_created_by ON saved_query (created_by);
