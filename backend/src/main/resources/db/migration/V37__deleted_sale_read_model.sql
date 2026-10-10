-- Deleted-sales read model: disposable resolved daily aggregates.
-- Reconstructed from canonical_deleted_sale by DeletedSaleProjector. Never a source of truth.

CREATE TABLE resolved_deleted_sale_day (
    trading_date date NOT NULL,
    deleted_count bigint NOT NULL,
    total_inc_tax numeric(14,4) NOT NULL,
    total_tax numeric(14,4) NOT NULL,
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (trading_date)
);
