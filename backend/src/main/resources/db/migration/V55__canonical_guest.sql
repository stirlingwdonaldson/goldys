-- Bitemporal canonical guest, mirroring canonical_reservation's bitemporal base.
-- Schema-only this slice: the reservation export carries no Guest ID, so no guest row is
-- written yet (populated from the reviews export in slice 2). Every fact column is
-- immutable (updatable = false on the entity); a correction supersedes the whole row.

CREATE TABLE canonical_guest (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    guest_name varchar(1024),
    first_name varchar(1024),
    last_name varchar(1024),
    nickname varchar(1024),
    email varchar(1024),
    phone varchar(1024),
    company varchar(1024),
    marketing_opt_in boolean,
    first_visit_date date,
    recent_visit_date date,
    upcoming_visits integer,
    completed_visits integer,
    cancelled_count integer,
    no_show_count integer,
    lifetime_spend numeric(19,4),
    lifetime_per_cover_spend numeric(19,4),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX uq_guest_current_source_fact
    ON canonical_guest (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX ix_guest_logical_current
    ON canonical_guest (logical_entity_id) WHERE superseded_at IS NULL;
