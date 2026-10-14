-- Record invoice-level annotations (delivery notes) parsed from a supplier PDF that are not
-- costable purchase lines, e.g. "(2 BOXES)" or "COMMENT". A plain idempotent evidence table (like
-- invoice_ingest_flag and invoice_pdf_link), not bitemporal: recording a note must not force a
-- supersession of the invoice fact. Keyed by invoice number, so each invoice carries at most one
-- concatenated note.
CREATE TABLE invoice_note (
    invoice_number varchar(255) PRIMARY KEY,
    note text NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL
);
