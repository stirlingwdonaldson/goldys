package com.goldys.platform.connectors.ctb;

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
 * A single invoice-ingestion anomaly, recorded append-only so a missing or unparseable PDF is a
 * queryable fact rather than a silent gap (spec §8). Identity fields ({@code invoiceNumber}, {@code
 * pdfFilename}, {@code stockCode}) are nullable and their combination — together with the flag type
 * — is what the writer deduplicates on, so a re-pull does not pile up duplicate rows.
 */
@Entity
@Table(name = "invoice_ingest_flag")
class InvoiceIngestFlag {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "flag_type", nullable = false, updatable = false)
  private InvoiceIngestFlagType flagType;

  @Column(name = "invoice_number", updatable = false)
  private String invoiceNumber;

  @Column(name = "pdf_filename", updatable = false)
  private String pdfFilename;

  @Column(name = "stock_code", updatable = false)
  private String stockCode;

  @Column(name = "detail", updatable = false)
  private String detail;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected InvoiceIngestFlag() {}

  private InvoiceIngestFlag(
      InvoiceIngestFlagType flagType,
      String invoiceNumber,
      String pdfFilename,
      String stockCode,
      String detail,
      Instant occurredAt) {
    this.id = UUID.randomUUID();
    this.flagType = Objects.requireNonNull(flagType, "flagType");
    this.invoiceNumber = invoiceNumber;
    this.pdfFilename = pdfFilename;
    this.stockCode = stockCode;
    this.detail = detail;
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  static InvoiceIngestFlag create(
      InvoiceIngestFlagType flagType,
      String invoiceNumber,
      String pdfFilename,
      String stockCode,
      String detail,
      Instant occurredAt) {
    return new InvoiceIngestFlag(
        flagType, invoiceNumber, pdfFilename, stockCode, detail, occurredAt);
  }

  InvoiceIngestFlagType flagType() {
    return flagType;
  }

  String invoiceNumber() {
    return invoiceNumber;
  }

  String pdfFilename() {
    return pdfFilename;
  }

  String stockCode() {
    return stockCode;
  }

  String detail() {
    return detail;
  }

  Instant occurredAt() {
    return occurredAt;
  }
}
