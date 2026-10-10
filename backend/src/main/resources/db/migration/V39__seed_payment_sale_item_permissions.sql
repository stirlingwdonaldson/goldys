-- Seed owner access to the Lightspeed payments / deleted-sales / sale-item resources.
-- Follows the V6/V13/V23 convention (ALL x OWNER); BOH/FOH grants stay deferred.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'payments.metrics', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'deleted-sales.metrics', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'sale-items.metrics', true, true);
