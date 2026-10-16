-- Owner decisions recorded in docs/data-handling-drift-plan.md (15 October 2026):
-- managers may read aggregate sales, bookings and wages-only totals; all app users
-- may view forecasting and its bounded aggregate support. No raw or individual pay grants.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write)
SELECT gen_random_uuid(), d.department, 'MANAGER', r.resource, true, false
FROM (VALUES ('ALL'), ('FOH'), ('BOH')) AS d(department)
CROSS JOIN (VALUES ('reconciliation.sales'), ('reservations.metrics'),
                   ('labour.hours'), ('labour.cost')) AS r(resource)
ON CONFLICT (department, seniority, resource) DO NOTHING;

INSERT INTO permission (id, department, seniority, resource, can_read, can_write)
SELECT gen_random_uuid(), d.department, s.seniority, 'forecasting', true, false
FROM (VALUES ('ALL'), ('FOH'), ('BOH')) AS d(department)
CROSS JOIN (VALUES ('OWNER'), ('MANAGER'), ('STAFF')) AS s(seniority)
ON CONFLICT (department, seniority, resource) DO NOTHING;
