-- Product categorisation: the curated category tree, the product->category assignments, and the
-- machine-learning clustering runs that propose assignments for review. These are operational
-- tables, not bitemporal canonical facts: a user's curation is authoritative, so there is no
-- valid_from/valid_to/superseded_at/recorded_at or logical_entity_id here.

CREATE TABLE product_category (
    id uuid PRIMARY KEY,
    name varchar(128) NOT NULL,
    group_name varchar(128),
    display_order int NOT NULL DEFAULT 0,
    description varchar(512),
    created_at timestamp(6) with time zone NOT NULL DEFAULT now(),
    updated_at timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_category_name UNIQUE (name)
);

CREATE TABLE product_category_assignment (
    product_name_key varchar(512) PRIMARY KEY,
    product_category_id uuid NOT NULL REFERENCES product_category(id),
    confidence numeric(6,4),
    source varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    assigned_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);
CREATE INDEX ix_assignment_category ON product_category_assignment (product_category_id);
CREATE INDEX ix_assignment_status ON product_category_assignment (status);

CREATE TABLE classification_run (
    id uuid PRIMARY KEY,
    kind varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    product_count int,
    started_at timestamp(6) with time zone NOT NULL,
    finished_at timestamp(6) with time zone,
    error_message text
);

CREATE TABLE classification_cluster (
    id uuid PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES classification_run(id),
    parent_id uuid,
    is_leaf boolean NOT NULL,
    product_name_key varchar(512),
    merge_height double precision,
    size int
);
CREATE INDEX ix_cluster_run ON classification_cluster (run_id);
CREATE INDEX ix_cluster_parent ON classification_cluster (parent_id);

CREATE TABLE classification_projection (
    run_id uuid NOT NULL REFERENCES classification_run(id),
    product_name_key varchar(512) NOT NULL,
    x double precision NOT NULL,
    y double precision NOT NULL,
    PRIMARY KEY (run_id, product_name_key)
);
