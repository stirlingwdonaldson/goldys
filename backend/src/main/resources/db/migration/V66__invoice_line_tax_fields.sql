-- Line-level tax treatment parsed from the supplier PDF and retained on the canonical line, so the
-- pipeline no longer drops the tax information a PDF prints (tax-inclusive total, GST amount, tax
-- rate, GST-inclusive flag). Nullable: a CSV-only line (or a PDF that does not print tax detail)
-- leaves them null, and the CSV stays authoritative for line_total (ex-tax). line_tax_rate is a
-- percentage (10.0 = 10%).
ALTER TABLE canonical_invoice_line
    ADD COLUMN line_total_inc_tax numeric(14,4),
    ADD COLUMN line_gst_amount numeric(14,4),
    ADD COLUMN line_tax_rate numeric(14,4),
    ADD COLUMN inc_gst boolean;
