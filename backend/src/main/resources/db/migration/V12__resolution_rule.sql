-- Standing resolution rules, append-only and superseded like the override tables.

CREATE TABLE resolution_rule (
    id uuid PRIMARY KEY,
    entity_type varchar(64) NOT NULL,
    field_key varchar(512) NOT NULL,
    strategy varchar(32) NOT NULL,
    custom_logic varchar(32),
    source_priority jsonb,
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX ux_resolution_rule_current
    ON resolution_rule (entity_type, field_key)
    WHERE superseded_at IS NULL;
