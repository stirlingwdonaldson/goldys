-- Preserve historical evidence; no number-only assignment or raw-ledger mutation.
ALTER TABLE invoice_pdf_link ADD COLUMN invoice_logical_id uuid;
CREATE INDEX idx_invoice_pdf_link_parent ON invoice_pdf_link(invoice_logical_id);
