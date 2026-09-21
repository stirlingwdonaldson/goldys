CREATE TABLE product_sales_override (
    id uuid PRIMARY KEY,
    trading_date date NOT NULL,
    product_name_key varchar(512) NOT NULL,
    authoritative_source varchar(255) NOT NULL,
    reason text,
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX ux_product_sales_override_current
    ON product_sales_override (product_name_key, trading_date) WHERE superseded_at IS NULL;
