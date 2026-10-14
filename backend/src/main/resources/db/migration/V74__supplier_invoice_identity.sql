-- V74 - Source-scoped supplier identity and invoice/invoice-line relationship repair.
--
-- Phase 2 slice 1. Adds the identity anchors the invoice facts reference, without
-- rewriting any existing fact. All changes are additive:
--   * canonical_supplier: a bitemporal supplier master keyed on (source_system,
--     source_record_ref), where source_record_ref is the source's own supplier
--     code (CTB "SupplierCode") — so two suppliers that happen to share a name
--     stay distinct entities, and a supplier rename versions the name, not the
--     identity.
--   * canonical_invoice.supplier_logical_id / supplier_source_ref: the invoice's
--     supplier identity. The invoice's own source_record_ref becomes supplier
--     scoped ("<supplierKey>::<invoiceNumber>") going forward, so an invoice
--     number is no longer treated as globally unique.
--   * canonical_invoice_line.invoice_logical_id: the stable parent invoice
--     identity (the invoice's logical_entity_id). A line is a child of an
--     invoice entity, not of a particular invoice version.
--
-- No raw_record mutation, no permission seeds (supplier data is read through the
-- existing invoice/inventory surfaces). Columns are nullable because production
-- has real rows that predate this migration and will be backfilled separately.
CREATE TABLE canonical_supplier (
    id                uuid                     NOT NULL,
    logical_entity_id uuid                     NOT NULL,
    source_system     varchar(255)             NOT NULL,
    source_record_ref varchar(512)             NOT NULL,
    supplier_code     varchar(255),
    name              varchar(512),
    valid_from        timestamp(6) with time zone NOT NULL,
    valid_to          timestamp(6) with time zone,
    recorded_at       timestamp(6) with time zone NOT NULL,
    superseded_at     timestamp(6) with time zone,
    raw_record_id     uuid                     NOT NULL REFERENCES raw_record(id),
    CONSTRAINT canonical_supplier_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_supplier_current_source_fact
    ON canonical_supplier (source_system, source_record_ref) WHERE superseded_at IS NULL;

ALTER TABLE canonical_invoice
    ADD COLUMN supplier_logical_id uuid,
    ADD COLUMN supplier_source_ref varchar(512);

ALTER TABLE canonical_invoice_line
    ADD COLUMN invoice_logical_id uuid;

CREATE INDEX idx_invoice_supplier_logical
    ON canonical_invoice (supplier_logical_id);
CREATE INDEX idx_invoice_line_invoice_logical
    ON canonical_invoice_line (invoice_logical_id);
