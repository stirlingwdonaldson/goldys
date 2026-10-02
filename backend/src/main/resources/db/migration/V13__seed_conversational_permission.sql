-- Seed the Conversational BI feature gate: only the owner (ALL x OWNER) may use
-- "Ask Goldy's". Tool-level data access stays gated by the existing
-- reconciliation.sales permission, enforced again inside every tool call.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'conversational.chat', true, true);
