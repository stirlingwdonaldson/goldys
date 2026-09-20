-- Seed a single administrator role with full access, so the slice is usable end-to-end while
-- the concrete field-to-role matrix is still being decided. More granular (department, seniority)
-- grants are added as rows later; nothing is inferred from seniority order.
--
-- The admin role is the "ALL departments / OWNER seniority" pair. The staff profile that carries
-- this role is seeded separately (see the deployment docs) because it needs the real OIDC
-- issuer + subject of the administrator, which are environment values, not schema.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'reconciliation.sales', true, true),
    (gen_random_uuid(), 'ALL', 'OWNER', 'connectors', true, true);
