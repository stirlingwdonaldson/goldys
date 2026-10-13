-- V51 - record which raw record is the PDF for a canonical invoice, so the owner can open the
-- original document. A plain idempotent evidence table (like invoice_ingest_flag), not bitemporal:
-- a provenance pointer must not force a supersession of the invoice fact, and the raw ledger is
-- append-only so the link cannot be stored on the PDF raw record itself.
CREATE TABLE invoice_pdf_link (
    pdf_raw_record_id uuid PRIMARY KEY,
    invoice_number varchar(255) NOT NULL,
    pdf_filename varchar(512),
    link_source varchar(32) NOT NULL,
    linked_at timestamp(6) with time zone NOT NULL
);
CREATE INDEX idx_invoice_pdf_link_invoice ON invoice_pdf_link(invoice_number);
