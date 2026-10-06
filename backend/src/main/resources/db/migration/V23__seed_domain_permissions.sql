-- Seed owner access to the new domain resources, matching the V6/V13 convention (ALL x OWNER).
-- BOH/FOH granular grants stay deferred to the stakeholder field-to-role matrix.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'reservations.metrics', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'labour.hours', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'labour.cost', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'labour.wages', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'inventory.cost', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'inventory.stock', true, true);
