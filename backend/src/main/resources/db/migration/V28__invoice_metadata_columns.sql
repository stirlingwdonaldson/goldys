-- Add the remaining columns of CTB's Custom Invoice Export to canonical_invoice.
-- The CSV supplies both the supplier's invoice number and CTB's internal purchase number,
-- the supplier account number, a tax code, and a full tax/freight breakdown. `total_amount`
-- (V22) is the inc-tax total payable; the ex-tax subtotal is `amount_ex_tax`.
-- All new columns are nullable: the canonical_invoice table ships empty, so no backfill is
-- required, and a future ingestion path (the invoice PDF) only supplies line items.
ALTER TABLE canonical_invoice
    ADD COLUMN purchase_number varchar(255),
    ADD COLUMN account_number varchar(255),
    ADD COLUMN tax_code varchar(64),
    ADD COLUMN amount_ex_tax numeric(14,4),
    ADD COLUMN gst_amount numeric(14,4),
    ADD COLUMN freight_amount numeric(14,4),
    ADD COLUMN freight_gst_amount numeric(14,4);
