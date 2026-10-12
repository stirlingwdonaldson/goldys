-- Recipe composition (Slice D): canonical recipe cost + sale↔recipe link (single-source CTB),
-- and the resolved per-recipe read model. CTB is the single authority for the theoretical cost;
-- Lightspeed menu price and actual cost are joined in the projector and stored on resolved_recipe.

CREATE TABLE canonical_recipe_cost (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    recipe_id integer NOT NULL,
    recipe_name varchar(512),
    cost_per_portion numeric(14,4),
    cost_per_portion_incl_labour numeric(14,4),
    total_cost numeric(14,4),
    portion_size varchar(255),
    portion_size_value numeric(14,4),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_recipe_cost_current_source_fact
    ON canonical_recipe_cost (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE canonical_sale_recipe_link (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    sale_item_recipe_id integer NOT NULL,
    sale_item_stock_code varchar(255),
    sale_item_stock_description varchar(512),
    recipe_id integer NOT NULL,
    recipe_name varchar(512),
    recipe_folder_name varchar(512),
    is_recipe_active boolean,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_sale_recipe_link_current_source_fact
    ON canonical_sale_recipe_link (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE resolved_recipe (
    recipe_id integer PRIMARY KEY,
    recipe_name varchar(512),
    recipe_folder_name varchar(512),
    cost_per_portion numeric(14,4),
    cost_per_portion_incl_labour numeric(14,4),
    total_cost numeric(14,4),
    portion_size varchar(255),
    portion_size_value numeric(14,4),
    menu_price numeric(14,4),
    actual_cost numeric(14,4),
    resolution_type text NOT NULL,
    authoritative_source text NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL
);
