-- Sale-item read model: disposable resolved daily-by-category aggregates.
-- Reconstructed from canonical_sale_item by SaleItemProjector. Never a source of truth.

CREATE TABLE resolved_sale_item_day (
    trading_date date NOT NULL,
    category_name text NOT NULL,
    quantity numeric(14,4) NOT NULL,
    amount numeric(14,4) NOT NULL,
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (trading_date, category_name)
);
