-- PDF-enrichment columns (spec §4). All nullable: only a minority of PDFs carry these.
ALTER TABLE canonical_invoice_line
    ADD COLUMN stock_code varchar(255),
    ADD COLUMN uom varchar(32),
    ADD COLUMN unit_quantity numeric(14,4),
    ADD COLUMN pack_size numeric(14,4),
    ADD COLUMN wet_amount numeric(14,4);
