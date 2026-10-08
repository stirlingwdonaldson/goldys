package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A normalized supplier-invoice metadata fact from one source. {@code totalAmount} is the inc-tax
 * total payable; {@code amountExTax} is the ex-tax subtotal, and the GST/freight figures are the
 * tax breakdown from the source export.
 */
public record InvoiceInput(
    String sourceSystem,
    String invoiceNumber,
    String supplierName,
    LocalDate invoiceDate,
    LocalDate dueDate,
    BigDecimal totalAmount,
    String purchaseNumber,
    String accountNumber,
    String taxCode,
    BigDecimal amountExTax,
    BigDecimal gstAmount,
    BigDecimal freightAmount,
    BigDecimal freightGstAmount,
    String pdfFilename,
    UUID rawRecordId) {}
