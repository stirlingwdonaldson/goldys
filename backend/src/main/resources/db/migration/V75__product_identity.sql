-- V75 - Source-scoped product identity, business-product hub, and stock-item relationships.
--
-- Phase 2 slice 2. Adds the identity anchors for products, without rewriting any existing fact:
--   * source_product: a source-scoped product identity (Lightspeed product id, CTB stock code).
--     Identity is the source's own code, never the name, so a rename versions the name and two
--     products that share a name stay distinct.
--   * product: the internal business-product hub (stable id + display name). Source products map
--     onto it; the hub survives source renames and re-keying.
--   * product_mapping: the bitemporal, reviewable mapping source_product -> product. Confidence
--     records whether the link came from a source code, a name match, or a manual decision, so a
--     name match is a suggestion to review, never an assertion of identity.
--   * stock_item: a supplier-scoped purchasable item (supplier + stock code), linked to its source
--     product. Distinct from a sold product: a purchased ingredient is not a sold menu item.
--   * canonical_product_sales gains nullable source-native columns (stock code, source cost, food
--     cost, discount, tax, unit price) that CTB supplies and Lightspeed does not, so they stay on
--     the source observation and never enter the universal resolved projection.
--
-- No raw_record mutation, no permission seeds, no changes to resolved tables. New columns are
-- nullable because production has real rows predating this migration.
CREATE TABLE source_product (
    id                uuid                     NOT NULL,
    logical_entity_id uuid                     NOT NULL,
    source_system     varchar(255)             NOT NULL,
    source_record_ref varchar(512)             NOT NULL,
    source_product_id integer,
    name              varchar(512),
    sku               varchar(255),
    category          varchar(255),
    valid_from        timestamp(6) with time zone NOT NULL,
    valid_to          timestamp(6) with time zone,
    recorded_at       timestamp(6) with time zone NOT NULL,
    superseded_at     timestamp(6) with time zone,
    raw_record_id     uuid                     NOT NULL REFERENCES raw_record(id),
    CONSTRAINT source_product_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_source_product_current_source_fact
    ON source_product (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE product (
    id         uuid                     NOT NULL,
    name       varchar(512)             NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT product_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_product_name ON product (name);

CREATE TABLE product_mapping (
    id                uuid                     NOT NULL,
    logical_entity_id uuid                     NOT NULL,
    source_system     varchar(255)             NOT NULL,
    source_record_ref varchar(512)             NOT NULL,
    product_id        uuid                     NOT NULL REFERENCES product(id),
    confidence        varchar(16)              NOT NULL,
    review_status     varchar(16)              NOT NULL,
    valid_from        timestamp(6) with time zone NOT NULL,
    valid_to          timestamp(6) with time zone,
    recorded_at       timestamp(6) with time zone NOT NULL,
    superseded_at     timestamp(6) with time zone,
    raw_record_id     uuid                     NOT NULL REFERENCES raw_record(id),
    CONSTRAINT product_mapping_pkey PRIMARY KEY (id),
    CONSTRAINT product_mapping_confidence_check
        CHECK (confidence IN ('SOURCE_CODE', 'NAME', 'MANUAL')),
    CONSTRAINT product_mapping_review_check
        CHECK (review_status IN ('AUTO', 'ACCEPTED', 'REJECTED'))
);

CREATE UNIQUE INDEX uq_product_mapping_current_source_fact
    ON product_mapping (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE stock_item (
    id                  uuid                     NOT NULL,
    logical_entity_id   uuid                     NOT NULL,
    source_system       varchar(255)             NOT NULL,
    source_record_ref   varchar(512)             NOT NULL,
    supplier_logical_id uuid,
    stock_code          varchar(255),
    name                varchar(512),
    source_product_id   uuid,
    valid_from          timestamp(6) with time zone NOT NULL,
    valid_to            timestamp(6) with time zone,
    recorded_at         timestamp(6) with time zone NOT NULL,
    superseded_at       timestamp(6) with time zone,
    raw_record_id       uuid                     NOT NULL REFERENCES raw_record(id),
    CONSTRAINT stock_item_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_stock_item_current_source_fact
    ON stock_item (source_system, source_record_ref) WHERE superseded_at IS NULL;

ALTER TABLE canonical_product_sales
    ADD COLUMN stock_code varchar(255);

-- Source-native CTB product-day measures (source cost, food cost, discount, tax, unit price) that
-- have no universal canonical home and are dropped from the universal product-sales fact. Keyed by
-- (source, stock code, trading date); never projected into a resolved view.
CREATE TABLE source_product_sale_fact (
    id                uuid                     NOT NULL,
    logical_entity_id uuid                     NOT NULL,
    source_system     varchar(255)             NOT NULL,
    source_record_ref varchar(512)             NOT NULL,
    trading_date      date                     NOT NULL,
    stock_code        varchar(255),
    cost_per_unit     numeric(14,4),
    total_food_cost   numeric(14,4),
    discounted_amount numeric(14,4),
    tax_amount        numeric(14,4),
    unit_price        numeric(14,4),
    valid_from        timestamp(6) with time zone NOT NULL,
    valid_to          timestamp(6) with time zone,
    recorded_at       timestamp(6) with time zone NOT NULL,
    superseded_at     timestamp(6) with time zone,
    raw_record_id     uuid                     NOT NULL REFERENCES raw_record(id),
    CONSTRAINT source_product_sale_fact_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_source_product_sale_fact_current
    ON source_product_sale_fact (source_system, source_record_ref) WHERE superseded_at IS NULL;
