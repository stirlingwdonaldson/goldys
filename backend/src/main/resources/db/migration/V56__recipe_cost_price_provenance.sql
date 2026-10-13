-- Recipe costing provenance: menu state, price source, and a last-sold fallback on the
-- disposable resolved read model. No canonical change; rebuilt by the projector.
ALTER TABLE resolved_recipe
    ADD COLUMN menu_state varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN price_source varchar(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN last_sold_price numeric(14,4),
    ADD COLUMN last_sold_cost numeric(14,4),
    ADD COLUMN last_sold_date date;
