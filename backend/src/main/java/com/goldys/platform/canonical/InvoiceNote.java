package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Invoice-level annotations (delivery notes) parsed from a supplier PDF that are not costable
 * purchase lines. A plain idempotent evidence table (like {@code invoice_ingest_flag} and {@code
 * invoice_pdf_link}), not bitemporal: recording a note must not force a supersession of the invoice
 * fact. Keyed by invoice number, so each invoice carries at most one concatenated note.
 */
@Entity
@Table(name = "invoice_note")
class InvoiceNote {

  @Id
  @Column(name = "invoice_number")
  private String invoiceNumber;

  @Column(name = "note", nullable = false)
  private String note;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  protected InvoiceNote() {}

  private InvoiceNote(String invoiceNumber, String note, Instant recordedAt) {
    this.invoiceNumber = Objects.requireNonNull(invoiceNumber, "invoiceNumber");
    this.note = Objects.requireNonNull(note, "note");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
  }

  static InvoiceNote create(String invoiceNumber, String note, Instant recordedAt) {
    return new InvoiceNote(invoiceNumber, note, recordedAt);
  }

  String note() {
    return note;
  }
}
