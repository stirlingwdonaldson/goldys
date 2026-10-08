-- Invoice-ingestion flags: first-class, append-only observability for anomalies in the CSV↔PDF
-- enrichment path (spec §8). A missing/ambiguous/unparseable PDF, or a PDF-only line, is a
-- queryable record rather than a silent gap. Rows are never updated or deleted; the writer skips
-- inserting an equivalent row so a re-pull does not pile up duplicates.
CREATE TABLE invoice_ingest_flag (
    id             uuid                     NOT NULL,
    flag_type      character varying(64)    NOT NULL,
    invoice_number character varying(255),
    pdf_filename   character varying(512),
    stock_code     character varying(255),
    detail         text,
    occurred_at    timestamp(6) with time zone NOT NULL,
    CONSTRAINT invoice_ingest_flag_pkey PRIMARY KEY (id)
);

-- Backs "what still needs attention" grouped by invoice and by flag type (recent-first).
CREATE INDEX idx_invoice_ingest_flag_invoice ON invoice_ingest_flag (invoice_number);
CREATE INDEX idx_invoice_ingest_flag_type_occurred ON invoice_ingest_flag (flag_type, occurred_at DESC);
