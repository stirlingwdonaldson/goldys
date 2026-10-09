-- Post-raw ingestion stage facts: each dataset's observed outcome at each pipeline stage
-- (parsed, canonicalized, enriched), recorded append-only so a push that stored raw bytes but then
-- failed to parse or canonicalize stays visible (audit F06). Rows are never updated or deleted.
CREATE TABLE ingestion_stage (
    id               uuid                     NOT NULL,
    ingestion_run_id uuid                     NOT NULL REFERENCES ingestion_run(id),
    source_system    varchar(255)             NOT NULL,
    dataset          varchar(255)             NOT NULL,
    stage            varchar(32)              NOT NULL,
    outcome          varchar(32)              NOT NULL,
    occurred_at      timestamp(6) with time zone NOT NULL,
    CONSTRAINT ingestion_stage_pkey PRIMARY KEY (id),
    CONSTRAINT ingestion_stage_stage_check
        CHECK (stage IN ('RAW_STORED', 'PARSED', 'CANONICALIZED', 'ENRICHED')),
    CONSTRAINT ingestion_stage_outcome_check
        CHECK (outcome IN ('SUCCESS', 'EMPTY', 'FAILED'))
);

CREATE INDEX idx_ingestion_stage_run ON ingestion_stage (ingestion_run_id);
CREATE INDEX idx_ingestion_stage_source_dataset
    ON ingestion_stage (source_system, dataset, occurred_at DESC);
