package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a supplier-invoice metadata fact. Fact fields are immutable; a correction closes
 * this row and inserts a successor sharing the same logical identity (the invoice number).
 */
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

  @Column(name = "purchase_number", updatable = false)
  private String purchaseNumber;

  @Column(name = "account_number", updatable = false)
  private String accountNumber;

  @Column(name = "tax_code", updatable = false)
  private String taxCode;

  @Column(name = "amount_ex_tax", updatable = false, precision = 14, scale = 4)
  private BigDecimal amountExTax;

  @Column(name = "gst_amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal gstAmount;

  @Column(name = "freight_amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal freightAmount;

  @Column(name = "freight_gst_amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal freightGstAmount;

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
      BigDecimal totalAmount,
      String purchaseNumber,
      String accountNumber,
      String taxCode,
      BigDecimal amountExTax,
      BigDecimal gstAmount,
      BigDecimal freightAmount,
      BigDecimal freightGstAmount) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.supplierName = supplierName;
    this.invoiceNumber = invoiceNumber;
    this.invoiceDate = invoiceDate;
    this.dueDate = dueDate;
    this.totalAmount = totalAmount;
    this.purchaseNumber = purchaseNumber;
    this.accountNumber = accountNumber;
    this.taxCode = taxCode;
    this.amountExTax = amountExTax;
    this.gstAmount = gstAmount;
    this.freightAmount = freightAmount;
    this.freightGstAmount = freightGstAmount;
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
      BigDecimal totalAmount,
      String purchaseNumber,
      String accountNumber,
      String taxCode,
      BigDecimal amountExTax,
      BigDecimal gstAmount,
      BigDecimal freightAmount,
      BigDecimal freightGstAmount) {
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
        totalAmount,
        purchaseNumber,
        accountNumber,
        taxCode,
        amountExTax,
        gstAmount,
        freightAmount,
        freightGstAmount);
  }

  boolean sameFact(InvoiceInput input) {
    return Objects.equals(supplierName, input.supplierName())
        && Objects.equals(invoiceNumber, input.invoiceNumber())
        && Objects.equals(invoiceDate, input.invoiceDate())
        && Objects.equals(dueDate, input.dueDate())
        && Objects.equals(purchaseNumber, input.purchaseNumber())
        && Objects.equals(accountNumber, input.accountNumber())
        && Objects.equals(taxCode, input.taxCode())
        && sameAmount(totalAmount, input.totalAmount())
        && sameAmount(amountExTax, input.amountExTax())
        && sameAmount(gstAmount, input.gstAmount())
        && sameAmount(freightAmount, input.freightAmount())
        && sameAmount(freightGstAmount, input.freightGstAmount());
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

  String purchaseNumber() {
    return purchaseNumber;
  }

  String accountNumber() {
    return accountNumber;
  }

  String taxCode() {
    return taxCode;
  }

  BigDecimal amountExTax() {
    return amountExTax;
  }

  BigDecimal gstAmount() {
    return gstAmount;
  }

  BigDecimal freightAmount() {
    return freightAmount;
  }

  BigDecimal freightGstAmount() {
    return freightGstAmount;
  }
}
