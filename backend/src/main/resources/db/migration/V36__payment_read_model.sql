-- Payments read model: disposable resolved daily-by-type aggregates.
-- Reconstructed from canonical_payment by PaymentProjector. Never a source of truth.

CREATE TABLE resolved_payment_day (
    trading_date date NOT NULL,
    payment_type_name text NOT NULL,
    amount numeric(14,4) NOT NULL,
    tip numeric(14,4) NOT NULL,
    payment_count bigint NOT NULL,
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (trading_date, payment_type_name)
);
