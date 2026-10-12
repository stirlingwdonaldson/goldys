-- Recipe ingredients (the deferred "recipes.ingredients" slice of the food-cost goal):
-- canonical recipe ingredient lines (single-source CTB) and the disposable resolved read model.
-- CTB is the single authority for per-recipe ingredient composition.

-- One bitemporal row per ingredient line. `parent_recipe_id` is the recipe the payload was
-- fetched for (derived from the raw record's `source_record_ref = "recipe:{id}"`); `line_recipe_id`
-- is the recipe the line itself belongs to (the parent itself for main lines, a sub-recipe for
-- `is_sub_recipe = true` lines). `recipe_detail_id` is CTB's line id — OWNER-scoped, not globally
-- unique, and NOT unique even per recipe (recipe 617 repeats a line verbatim), so the current-fact
-- uniqueness lives on `(source_system, source_record_ref)`, never on the line id.
CREATE TABLE canonical_recipe_ingredient (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    parent_recipe_id integer NOT NULL,
    line_recipe_id integer,
    line_recipe_name varchar(512),
    is_sub_recipe boolean,
    recipe_detail_id integer NOT NULL,
    stock_id integer,
    stock_code varchar(255),
    stock_description varchar(512),
    ingredient_description varchar(512),
    supplier_name varchar(512),
    supplier_stock_code varchar(255),
    portion_weight numeric(14,4),
    unit_of_measurement varchar(64),
    stock_uom_shortname varchar(32),
    cost_per_qty numeric(14,4),
    cost_per_unit numeric(14,4),
    stock_usable numeric(14,4),
    stock_wastage numeric(14,4),
    is_broken_link boolean,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_recipe_ingredient_current_source_fact
    ON canonical_recipe_ingredient (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX idx_recipe_ingredient_parent_recipe ON canonical_recipe_ingredient (parent_recipe_id);

-- Disposable resolved read model, rebuilt from canonical on every projection pass. Because
-- `line_recipe_id` and `stock_id` are nullable (a line can have no stock reference — sentinel or
-- broken link), a natural composite key would be impossible in PostgreSQL, so the row identity is a
-- surrogate id. Lookups are on `parent_recipe_id`; the projection is never queried by id.
CREATE TABLE resolved_recipe_ingredient (
    id uuid PRIMARY KEY,
    parent_recipe_id integer NOT NULL,
    line_recipe_id integer,
    line_recipe_name varchar(512),
    is_sub_recipe boolean,
    recipe_detail_id integer NOT NULL,
    stock_id integer,
    stock_code varchar(255),
    stock_description varchar(512),
    portion_weight numeric(14,4),
    unit_of_measurement varchar(64),
    cost_per_qty numeric(14,4),
    cost_per_unit numeric(14,4),
    supplier_name varchar(512),
    resolved_at timestamp(6) with time zone NOT NULL
);
CREATE INDEX idx_resolved_recipe_ingredient_parent ON resolved_recipe_ingredient (parent_recipe_id);
