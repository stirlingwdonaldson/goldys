-- Reservation read model: disposable resolved daily aggregates + manual overrides.
-- Reconstructed from canonical_reservation by ReservationProjector. Never a source of truth.

CREATE TABLE resolved_reservation_day (
    trading_date date NOT NULL,
    service_period text NOT NULL,
    bookings bigint NOT NULL,
    attended bigint NOT NULL,
    covers bigint NOT NULL,
    cancelled bigint NOT NULL,
    no_shows bigint NOT NULL,
    walk_ins bigint NOT NULL,
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (trading_date, service_period)
);

CREATE TABLE reservation_override (
    id uuid PRIMARY KEY,
    trading_date date NOT NULL,
    service_period text NOT NULL,
    overridden_covers bigint,
    reason text,
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX ux_reservation_override_current
    ON reservation_override (trading_date, service_period) WHERE superseded_at IS NULL;
