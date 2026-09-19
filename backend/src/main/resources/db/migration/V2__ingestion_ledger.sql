-- The cleared Phase 1 rebuild has no retained V1 ingestion rows or running V1
-- application instances. Exact source bytes become the authoritative evidence;
-- JSONB remains available only as an optional derived representation.
CREATE TABLE ingestion_run (
    id uuid PRIMARY KEY,
    source_system varchar(255) NOT NULL,
    connector_name varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    started_at timestamp(6) with time zone NOT NULL,
    completed_at timestamp(6) with time zone,
    input_watermark text,
    output_watermark text,
    fetched_count bigint NOT NULL DEFAULT 0 CHECK (fetched_count >= 0),
    persisted_count bigint NOT NULL DEFAULT 0 CHECK (persisted_count >= 0),
    failure_summary text,
    CONSTRAINT ingestion_run_status_check
      CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED', 'NO_NEW_DATA'))
);

ALTER TABLE raw_record RENAME COLUMN payload TO parsed_payload;
ALTER TABLE raw_record ALTER COLUMN parsed_payload DROP NOT NULL;
ALTER TABLE raw_record
    ADD COLUMN ingestion_run_id uuid NOT NULL REFERENCES ingestion_run(id),
    ADD COLUMN payload_bytes bytea NOT NULL,
    ADD COLUMN payload_sha256 char(64) NOT NULL,
    ADD COLUMN payload_byte_length bigint NOT NULL CHECK (payload_byte_length >= 0),
    ADD COLUMN character_encoding varchar(64),
    ADD CONSTRAINT raw_record_digest_format_check
      CHECK (payload_sha256 ~ '^[0-9a-f]{64}$');

ALTER TABLE ingestion_failure
    ADD COLUMN ingestion_run_id uuid NOT NULL REFERENCES ingestion_run(id);

CREATE INDEX idx_ingestion_run_source_started
    ON ingestion_run (source_system, started_at DESC);
CREATE INDEX idx_raw_record_run ON raw_record (ingestion_run_id);

CREATE FUNCTION reject_raw_record_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'raw_record is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER raw_record_append_only
    BEFORE UPDATE OR DELETE ON raw_record
    FOR EACH ROW EXECUTE FUNCTION reject_raw_record_mutation();
