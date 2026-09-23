CREATE TABLE canonical_reservation (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    reservation_at timestamp(6) with time zone NOT NULL,
    party_size integer NOT NULL,
    status varchar(64) NOT NULL,
    table_name varchar(255),
    source_channel varchar(255),
    party_name varchar(255),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX uq_reservation_current_source_fact
    ON canonical_reservation (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX ix_reservation_logical_current
    ON canonical_reservation (logical_entity_id) WHERE superseded_at IS NULL;
