CREATE TABLE canonical_recipe_section (
 id uuid PRIMARY KEY, logical_entity_id uuid NOT NULL, source_system varchar(255) NOT NULL,
 source_record_ref varchar(255) NOT NULL, raw_record_id uuid NOT NULL REFERENCES raw_record(id),
 valid_from timestamptz NOT NULL, valid_to timestamptz, recorded_at timestamptz NOT NULL, superseded_at timestamptz,
 recipe_id integer NOT NULL, section varchar(255) NOT NULL, successful boolean NOT NULL, notice text,
 endpoint varchar(255), fetcher varchar(255), capture_ref varchar(255), ingestion_run_id uuid REFERENCES ingestion_run(id), sha256 varchar(255)
);
CREATE UNIQUE INDEX canonical_recipe_section_current ON canonical_recipe_section(source_record_ref) WHERE superseded_at IS NULL;
CREATE TABLE canonical_recipe_section_fields (
 snapshot_id uuid NOT NULL REFERENCES canonical_recipe_section(id), field_order integer NOT NULL,
 field_path varchar(255) NOT NULL, field_value text, field_type varchar(255) NOT NULL,
 PRIMARY KEY(snapshot_id,field_order)
);
CREATE TABLE resolved_recipe_section (id varchar(255) PRIMARY KEY, recipe_id integer NOT NULL, observation jsonb NOT NULL);
CREATE INDEX resolved_recipe_section_recipe ON resolved_recipe_section(recipe_id);
ALTER TABLE resolved_recipe ADD COLUMN cost_evidence jsonb NOT NULL DEFAULT '[]', ADD COLUMN price_evidence jsonb NOT NULL DEFAULT '[]';
ALTER TABLE resolved_recipe_ingredient ADD COLUMN source_evidence jsonb;

CREATE INDEX resolved_recipe_usage_children ON resolved_recipe_definition USING GIN ((definition->'usages'));
