-- Ruling E: the assignment model's UNASSIGNED status carries no category and no source, so
-- product_category_id and source must be nullable (V53 declared both NOT NULL by mistake).
-- status stays NOT NULL: every assignment row has a lifecycle state.
ALTER TABLE product_category_assignment ALTER COLUMN product_category_id DROP NOT NULL;
ALTER TABLE product_category_assignment ALTER COLUMN source DROP NOT NULL;
