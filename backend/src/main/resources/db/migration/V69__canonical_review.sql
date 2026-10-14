-- Bitemporal canonical review, mirroring canonical_guest's bitemporal base. Logical identity =
-- UUID.nameUUIDFromBytes("review:" + reviewId); source identity = (source_system = OPENTABLE,
-- source_record_ref = Review ID). Every fact column is immutable (updatable = false on the
-- entity); a correction closes this row and inserts a successor sharing the same logical identity.
-- guest_id links the review to the canonical_guest populated this slice (nullable: some reviews
-- carry no Guest ID).

CREATE TABLE canonical_review (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone,
    source varchar(16) NOT NULL,
    guest_id uuid REFERENCES canonical_guest(id),
    guest_name varchar(1024),
    reviewed_at timestamp(6) with time zone NOT NULL,
    visited_at timestamp(6) with time zone,
    overall_rating int,
    food_rating int,
    service_rating int,
    ambience_rating int,
    value_rating int,
    noise_label varchar(1024),
    would_recommend boolean,
    comment text,
    private_note text,
    tags text[],
    keywords text[]
);

CREATE UNIQUE INDEX uq_review_current_source_fact
    ON canonical_review (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX ix_review_reviewed_at_current
    ON canonical_review (reviewed_at) WHERE superseded_at IS NULL;
CREATE INDEX ix_review_guest_id
    ON canonical_review (guest_id);
