-- A product (entity_key = product name) can conflict on multiple trading dates, so the exception
-- key must include trading_date. Daily-sales rows already satisfy it (entity_key == the date).
ALTER TABLE reconciliation_exception DROP CONSTRAINT reconciliation_exception_pkey;
ALTER TABLE reconciliation_exception ADD PRIMARY KEY (entity_type, entity_key, trading_date, field_key);
