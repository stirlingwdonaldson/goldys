-- Persist the CSV's PDF filename on the invoice so the PDF↔CSV join can use the filename as the
-- authoritative key (spec §7), falling back to the PDF's own text invoice number only when the
-- filename does not resolve. Nullable: an invoice with no PDF (or a PDF-less CSV row) has none.
ALTER TABLE canonical_invoice ADD COLUMN pdf_filename varchar(512);
