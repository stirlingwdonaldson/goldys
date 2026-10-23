package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A normalized supplier-invoice metadata fact from one source. {@code totalAmount} is the inc-tax
 * total payable; {@code amountExTax} is the ex-tax subtotal, and the GST/freight figures are the
 * tax breakdown from the source export.
 *
 * <p>{@code supplierCode} is the source's own supplier identifier (CTB "SupplierCode"); it is the
 * supplier identity. {@code supplierName} is the display name and may change without changing
 * identity.
 */
public record InvoiceInput(
    String sourceSystem,
    String invoiceNumber,
    String supplierName,
    String supplierCode,
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
