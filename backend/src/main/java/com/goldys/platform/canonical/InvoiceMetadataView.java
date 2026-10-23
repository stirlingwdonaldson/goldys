package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A read-only view of one current invoice's metadata, for modules outside this package. */
public record InvoiceMetadataView(
    String supplierName,
    String invoiceNumber,
    LocalDate invoiceDate,
    LocalDate dueDate,
    BigDecimal totalAmount,
    String purchaseNumber,
    String pdfFilename,
    BigDecimal amountExTax,
    BigDecimal gstAmount,
    BigDecimal freightAmount,
    java.util.UUID invoiceId,
    java.util.UUID supplierId) {
  public InvoiceMetadataView(
      String supplierName,
      String invoiceNumber,
      LocalDate invoiceDate,
      LocalDate dueDate,
      BigDecimal totalAmount,
      String purchaseNumber,
      String pdfFilename,
      BigDecimal amountExTax,
      BigDecimal gstAmount,
      BigDecimal freightAmount) {
    this(
        supplierName,
        invoiceNumber,
        invoiceDate,
        dueDate,
        totalAmount,
        purchaseNumber,
        pdfFilename,
        amountExTax,
        gstAmount,
        freightAmount,
        null,
        null);
  }
}
