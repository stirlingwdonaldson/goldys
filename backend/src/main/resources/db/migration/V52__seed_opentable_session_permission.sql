-- Seed owner access to the OpenTable assisted sign-in (live view of the sidecar browser). It is a
-- window into the venue's OpenTable account, so it is its own resource rather than part of
-- 'connectors'. ALL x OWNER only; BOH/FOH grants stay deferred to the field-to-role matrix.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'connectors.opentable.session', true, true);
