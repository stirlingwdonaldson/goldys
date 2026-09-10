-- V1 baseline - the scaffold schema (PRD Requirements 1-3).
--
-- This DDL was generated from Hibernate's own schema export (ddl-auto=create
-- against PostgreSQL 16), so that switching application.yml to 'validate' is an
-- exact match rather than a guess, and then hand-edited in two places:
--
--   1. Enum CHECK constraints were dropped. Hibernate generates a CHECK
--      constraint for every @Enumerated(STRING) column, but `validate` does not
--      check constraints, so a hand-maintained CHECK would silently drift from
--      the Java enum and only fail at INSERT time - the first time someone adds
--      a Department or Seniority value, which auth/Department.java explicitly
--      promises is a no-migration change. The enums are the source of truth.
--   2. A uniqueness guarantee was added to `permission`, which Hibernate does
--      not infer from the entity. Duplicate grants for the same
--      (department, seniority, resource) triple would make the permission model
--      ambiguous to reason about, which is the one thing it must not be.
--
-- Timestamps are `timestamp with time zone` because Hibernate maps Instant to
-- TIMESTAMP_UTC. All bitemporal columns are therefore stored in UTC.

-- Layer 1: the append-only raw event log (Requirement 1).
--
-- Immutability is currently a convention enforced by application code (every
-- column but `id` is mapped updatable = false, and there is no update/delete
-- path in the repositories) rather than by the database. If that needs to be a
-- hard guarantee, it belongs in its own migration as a rule/trigger - not
-- folded into the baseline, because it changes how tests and any future
-- deletion request must behave.
CREATE TABLE raw_record (
    id               uuid                     NOT NULL,
    source_system    character varying(255)   NOT NULL,
    fetch_method     character varying(255)   NOT NULL,
    content_type     character varying(255)   NOT NULL,
    payload          jsonb                    NOT NULL,
    fetcher_identity character varying(255)   NOT NULL,
    fetched_at       timestamp(6) with time zone NOT NULL,
    CONSTRAINT raw_record_pkey PRIMARY KEY (id)
);

-- The append-only log's natural access path: "latest record per source", which
-- is how a connector establishes its watermark between runs.
CREATE INDEX idx_raw_record_source_fetched
    ON raw_record (source_system, fetched_at DESC);

-- First-class ingestion failures (Requirement 6), deliberately a separate table
-- from raw_record so a failure is never mistaken for "no new data".
CREATE TABLE ingestion_failure (
    id            uuid                     NOT NULL,
    source_system character varying(255)   NOT NULL,
    failure_type  character varying(255)   NOT NULL,
    detail        text,
    occurred_at   timestamp(6) with time zone NOT NULL,
    CONSTRAINT ingestion_failure_pkey PRIMARY KEY (id)
);

-- Backs IngestionFailureRepository.findBySourceSystemOrderByOccurredAtDesc.
CREATE INDEX idx_ingestion_failure_source_occurred
    ON ingestion_failure (source_system, occurred_at DESC);

-- The role model (Requirement 3): one row per (department, seniority, resource)
-- grant. Table-driven on purpose - "Owner sees more" is expressed as rows, not
-- as an ordinal comparison in code, and adding a department or seniority value
-- only ever adds rows.
--
-- Ships empty. Populating it is blocked on the PRD's field-to-role mapping open
-- question; do not seed it with guessed grants.
CREATE TABLE permission (
    id         uuid                   NOT NULL,
    department character varying(255) NOT NULL,
    seniority  character varying(255) NOT NULL,
    resource   character varying(255) NOT NULL,
    can_read   boolean                NOT NULL,
    can_write  boolean                NOT NULL,
    CONSTRAINT permission_pkey PRIMARY KEY (id),
    CONSTRAINT permission_department_seniority_resource_key
        UNIQUE (department, seniority, resource)
);

-- Layer 2: bitemporal canonical entities (Requirement 2). Both canonical tables
-- carry the same two independent time axes:
--
--   valid_from / valid_to        - valid time: when the fact was true
--   recorded_at / superseded_at  - system time: when we recorded / replaced it
--
-- A correction closes the old row (superseded_at) and inserts a new one; rows
-- are never updated in place. That is what makes "as of a past system time"
-- queries and rule recomputation possible.
--
-- Field lists are placeholders pending the entity-matching design session (PRD
-- Requirement 7). Expect the first real migration after that session to add the
-- matching keys and a provenance link back to the raw_record that produced each
-- row.

CREATE TABLE canonical_shift (
    id              uuid                     NOT NULL,
    valid_from      timestamp(6) with time zone NOT NULL,
    valid_to        timestamp(6) with time zone,
    recorded_at     timestamp(6) with time zone NOT NULL,
    superseded_at   timestamp(6) with time zone,
    staff_member_ref uuid,
    shift_start     timestamp(6) with time zone,
    shift_end       timestamp(6) with time zone,
    CONSTRAINT canonical_shift_pkey PRIMARY KEY (id)
);

CREATE TABLE canonical_sale_item (
    id            uuid                     NOT NULL,
    valid_from    timestamp(6) with time zone NOT NULL,
    valid_to      timestamp(6) with time zone,
    recorded_at   timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone,
    item_name     character varying(255),
    quantity_sold integer,
    amount        numeric(38,2),
    CONSTRAINT canonical_sale_item_pkey PRIMARY KEY (id)
);

-- Every canonical read is "the current version of X" (superseded_at IS NULL) or
-- "X as of time T". A partial index keeps the common current-row lookup cheap
-- without indexing the history that accumulates behind it.
CREATE INDEX idx_canonical_shift_current
    ON canonical_shift (valid_from) WHERE superseded_at IS NULL;
CREATE INDEX idx_canonical_sale_item_current
    ON canonical_sale_item (valid_from) WHERE superseded_at IS NULL;
