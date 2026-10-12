-- Seed owner access to the forecasting feature, following the V6/V13/V23/V26/V39 convention
-- (ALL x OWNER). BOH/FOH grants stay deferred until the field-to-role mapping is decided.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'forecasting', true, true);
