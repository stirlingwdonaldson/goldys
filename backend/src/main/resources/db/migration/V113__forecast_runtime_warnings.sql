-- NULL on historical runs means runtime warnings were not retained, not a clean outcome.
ALTER TABLE forecast_run ADD COLUMN runtime_warnings jsonb;
