package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** One version of a supplier-invoice metadata fact. Fact fields are immutable; a correction closes
 * this row and inserts a successor sharing the same logical identity (the invoice number). */
@Entity
@Table(name = "canonical_invoice")
class CanonicalInvoice extends BitemporalEntity {
  @Column(name = "supplier_name", updatable = false)
  private String supplierName;

  @Column(name = "invoice_number", nullable = false, updatable = false)
  private String invoiceNumber;

  @Column(name = "invoice_date", nullable = false, updatable = false)
  private LocalDate invoiceDate;

  @Column(name = "due_date", updatable = false)
  private LocalDate dueDate;

  @Column(name = "total_amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal totalAmount;

  protected CanonicalInvoice() {}

  private CanonicalInvoice(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String supplierName,
      String invoiceNumber,
      LocalDate invoiceDate,
      LocalDate dueDate,
      BigDecimal totalAmount) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.supplierName = supplierName;
    this.invoiceNumber = invoiceNumber;
    this.invoiceDate = invoiceDate;
    this.dueDate = dueDate;
    this.totalAmount = totalAmount;
  }

  static CanonicalInvoice create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String supplierName,
      String invoiceNumber,
      LocalDate invoiceDate,
      LocalDate dueDate,
      BigDecimal totalAmount) {
    return new CanonicalInvoice(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        supplierName,
        invoiceNumber,
        invoiceDate,
        dueDate,
        totalAmount);
  }

  boolean sameFact(InvoiceInput input) {
    return Objects.equals(supplierName, input.supplierName())
        && Objects.equals(invoiceNumber, input.invoiceNumber())
        && Objects.equals(invoiceDate, input.invoiceDate())
        && Objects.equals(dueDate, input.dueDate())
        && sameAmount(totalAmount, input.totalAmount());
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  String supplierName() {
    return supplierName;
  }

  String invoiceNumber() {
    return invoiceNumber;
  }

  LocalDate invoiceDate() {
    return invoiceDate;
  }

  LocalDate dueDate() {
    return dueDate;
  }

  BigDecimal totalAmount() {
    return totalAmount;
  }
}
