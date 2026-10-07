-- Seed owner access to the new Ask Goldy's surfaces, matching the V6/V13/V23
-- convention (ALL x OWNER). reconciliation.status gates the reconciliation status
-- view; conversational.threads gates the persisted conversation thread store.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'reconciliation.status', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'conversational.threads', true, true);
