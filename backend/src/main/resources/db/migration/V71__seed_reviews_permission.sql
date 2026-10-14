-- Seed owner access to the reviews analytics domain, matching the V6/V13/V23 convention
-- (ALL x OWNER). BOH/FOH granular grants stay deferred.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'reviews', true, true);
