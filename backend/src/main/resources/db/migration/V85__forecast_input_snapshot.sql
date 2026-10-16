-- Freeze the exact input checked at submission; legacy runs keep unknown evidence.
ALTER TABLE forecast_run ADD COLUMN input_snapshot jsonb;
