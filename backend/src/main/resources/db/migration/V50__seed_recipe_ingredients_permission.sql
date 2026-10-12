-- Seed owner access to the recipe ingredients resource, matching the V48 recipe convention
-- (ALL x OWNER). BOH/FOH granular grants stay deferred to the stakeholder field-to-role matrix.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'recipes.ingredients', true, true);
