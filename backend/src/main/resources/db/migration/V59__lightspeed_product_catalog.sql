-- Lightspeed menu source: canonical product facts from the Lightspeed product/menu
-- export, plus a name-alias table for matching Lightspeed product names to canonical
-- recipe/sale names. `canonical_sale_recipe_link.lightspeed_product_id` records the
-- resolved Lightspeed product on a sale↔recipe link so the projector can join menu
-- price to theoretical cost without re-parsing the raw payload.

CREATE TABLE canonical_product (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    product_id integer NOT NULL,
    product_name varchar(512),
    category varchar(255),
    sell_price_inc_tax numeric(14,4),
    is_modifier boolean,
    reporting_group varchar(255),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_product_current_source_fact
    ON canonical_product (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE product_alias (
    id uuid PRIMARY KEY,
    canonical_name varchar(512) NOT NULL,
    alias varchar(512) NOT NULL,
    source varchar(32),
    created_at timestamp(6) with time zone NOT NULL
);
CREATE UNIQUE INDEX uq_product_alias_pair ON product_alias (canonical_name, alias);

ALTER TABLE canonical_sale_recipe_link ADD COLUMN lightspeed_product_id integer;
