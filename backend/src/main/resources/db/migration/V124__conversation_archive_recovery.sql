INSERT INTO permission(id, department, seniority, resource, can_read, can_write)
VALUES (gen_random_uuid(), 'ALL', 'OWNER', 'conversational.archive-recovery', true, false);
