package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Which raw record holds the PDF document for a canonical invoice, so the owner can open the
 * original. A plain idempotent evidence table (like {@code invoice_ingest_flag}), not bitemporal: a
 * provenance pointer must not force a supersession of the invoice fact, and the raw ledger is
 * append-only so the link cannot live on the PDF raw record itself. Keyed by the PDF raw record id,
 * so a re-pull cannot pile up duplicate links for the same document.
 */
@Entity
@Table(name = "invoice_pdf_link")
class InvoicePdfLink {

  @Id
  @Column(name = "pdf_raw_record_id")
  private UUID pdfRawRecordId;

  @Column(name = "invoice_number", nullable = false)
  private String invoiceNumber;

  @Column(name = "pdf_filename")
  private String pdfFilename;

  @Enumerated(EnumType.STRING)
  @Column(name = "link_source", nullable = false)
  private InvoicePdfLinkSource linkSource;

  @Column(name = "linked_at", nullable = false)
  private Instant linkedAt;

  protected InvoicePdfLink() {}

  private InvoicePdfLink(
      UUID pdfRawRecordId,
      String invoiceNumber,
      String pdfFilename,
      InvoicePdfLinkSource linkSource,
      Instant linkedAt) {
    this.pdfRawRecordId = Objects.requireNonNull(pdfRawRecordId, "pdfRawRecordId");
    this.invoiceNumber = Objects.requireNonNull(invoiceNumber, "invoiceNumber");
    this.pdfFilename = pdfFilename;
    this.linkSource = Objects.requireNonNull(linkSource, "linkSource");
    this.linkedAt = Objects.requireNonNull(linkedAt, "linkedAt");
  }

  static InvoicePdfLink create(
      UUID pdfRawRecordId,
      String invoiceNumber,
      String pdfFilename,
      InvoicePdfLinkSource linkSource,
      Instant linkedAt) {
    return new InvoicePdfLink(pdfRawRecordId, invoiceNumber, pdfFilename, linkSource, linkedAt);
  }

  UUID pdfRawRecordId() {
    return pdfRawRecordId;
  }

  String invoiceNumber() {
    return invoiceNumber;
  }

  String pdfFilename() {
    return pdfFilename;
  }

  InvoicePdfLinkSource linkSource() {
    return linkSource;
  }

  Instant linkedAt() {
    return linkedAt;
  }
}
