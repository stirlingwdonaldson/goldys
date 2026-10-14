-- Auto category naming: the LLM-drafted taxonomy suggestions that staff review and apply.
-- A draft keyed to the run that produced it (run_id nullable so staff-added suggestions need no
-- run). Like the other classification_* tables, this is curated/resolved state, not a bitemporal
-- canonical fact: no valid_from/valid_to/superseded_at/recorded_at here.

CREATE TABLE category_suggestion (
    id uuid PRIMARY KEY,
    run_id uuid REFERENCES classification_run(id),
    name varchar(128) NOT NULL,
    group_name varchar(128),
    display_order int NOT NULL DEFAULT 0,
    hint varchar(512),
    created_at timestamp(6) with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_category_suggestion_run ON category_suggestion (run_id);
