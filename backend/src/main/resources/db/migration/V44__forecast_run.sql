-- Persisted asynchronous TimesFM forecast runs: one row per submitted forecast. The request
-- (target, past-only covariates, history window, horizon) is fixed at creation; the quantile
-- series, model version and timing are filled in when the run succeeds. The series is JSONB on
-- the row because compare-to-actuals always loads the whole series anyway.
CREATE TABLE forecast_run (
    id                uuid PRIMARY KEY,
    created_at        timestamptz NOT NULL,
    created_by        varchar(255) NOT NULL,   -- caller email, matching saved_dashboard.created_by
    target_metric     text NOT NULL,           -- MetricId, e.g. 'sales.gross'
    target_dimension  text,                    -- null in v1
    covariates        jsonb NOT NULL,          -- list of covariate MetricIds, e.g. ["reservations.covers"]
    history_from      date NOT NULL,
    history_to        date NOT NULL,
    horizon_days      int  NOT NULL,
    grain             text NOT NULL DEFAULT 'DAY',
    status            text NOT NULL,           -- QUEUED | RUNNING | SUCCEEDED | FAILED
    error_message     text,
    model_version     text,                    -- e.g. 'timesfm-3.0'
    elapsed_ms        bigint,
    forecast_series   jsonb,                   -- [{ "date": "…", "quantiles": { "0.1": … } }], set on SUCCEEDED
    CHECK (horizon_days > 0)
);

CREATE INDEX forecast_run_created_at_idx ON forecast_run (created_at DESC);
