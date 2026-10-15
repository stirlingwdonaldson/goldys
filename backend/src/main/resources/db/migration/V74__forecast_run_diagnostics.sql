-- The XGBoost twin's diagnostic payload (accuracy vs baseline, feature importance, backtest),
-- captured verbatim from the forecast service and stored as a whole JSONB document alongside the
-- run's series. Present only on SUCCEEDED runs; null otherwise.
ALTER TABLE forecast_run ADD COLUMN diagnostics jsonb;
