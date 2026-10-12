-- Seed owner access to the recipe costing resource, matching the V6/V13/V23 convention
-- (ALL x OWNER). BOH/FOH granular grants stay deferred to the stakeholder field-to-role matrix.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'recipes.cost', true, true);
