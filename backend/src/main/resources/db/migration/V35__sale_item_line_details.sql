-- Extend the canonical sale-item scaffold with the Lightspeed line-item detail.
-- The table was previously a 3-field placeholder (item_name, quantity_sold, amount)
-- with no ingestion path; it now holds the real receipt-line facts.

ALTER TABLE canonical_sale_item
    ADD COLUMN trading_date date NOT NULL,
    ADD COLUMN sale_number varchar(255),
    ADD COLUMN product_number varchar(255),
    ADD COLUMN sku varchar(255),
    ADD COLUMN category_name varchar(255),
    ADD COLUMN sold_price_inc_tax numeric(14,4),
    ADD COLUMN total_tax numeric(14,4),
    ADD COLUMN cost_inc_tax numeric(14,4),
    ADD COLUMN order_type varchar(64),
    ADD COLUMN sale_type varchar(64),
    ADD COLUMN staff_name varchar(255),
    ADD COLUMN register_name varchar(255),
    ADD COLUMN table_number varchar(255);
