-- Product-sales read model: disposable resolved-value projection.
-- Reconstructed from canonical_product_sales + product_sales_override + resolution_rule by
-- ProductSalesProjector. Never a source of truth; safe to truncate and replay.

CREATE TABLE resolved_product_sales (
    trading_date date NOT NULL,
    product_name_key varchar(512) NOT NULL,
    quantity_sold numeric(14,4),
    amount numeric(14,4),
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (trading_date, product_name_key)
);
