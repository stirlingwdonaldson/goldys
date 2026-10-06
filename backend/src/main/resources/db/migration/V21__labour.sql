-- Labour canonical entity + resolved daily read model + manual overrides.
-- Canonical field list is domain-driven and provisional (Deputy payload unconfirmed).

CREATE TABLE canonical_labour_entry (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    staff_ref varchar(255),
    department varchar(64) NOT NULL,
    labour_date date NOT NULL,
    scheduled_hours numeric(10,2) NOT NULL,
    actual_hours numeric(10,2) NOT NULL,
    scheduled_cost numeric(14,4),
    actual_cost numeric(14,4),
    shift_start timestamp(6) with time zone,
    shift_end timestamp(6) with time zone,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_labour_current_source_fact
    ON canonical_labour_entry (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE resolved_labour_day (
    trading_date date NOT NULL,
    department text NOT NULL,
    scheduled_hours numeric(14,4),
    actual_hours numeric(14,4),
    scheduled_cost numeric(14,4),
    actual_cost numeric(14,4),
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (trading_date, department)
);

CREATE TABLE labour_override (
    id uuid PRIMARY KEY,
    trading_date date NOT NULL,
    department text NOT NULL,
    overridden_actual_hours numeric(14,4),
    reason text,
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX ux_labour_override_current
    ON labour_override (trading_date, department) WHERE superseded_at IS NULL;
