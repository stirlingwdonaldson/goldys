package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A parsed invoice from CTB's Custom Invoice Export CSV: header fields plus its line items. The raw
 * CSV bytes are always preserved separately; this holds the fields the canonical model consumes.
 */
public record CtInvoice(
    String supplierName, // Supplier
    String purchaseNumber, // PONumber
    LocalDate invoiceDate, // Date
    String invoiceNumber, // Invoice
    LocalDate dueDate, // InvoiceDueDate
    BigDecimal amountExTax, // InvoiceTotalExTax
    BigDecimal gstAmount, // GST
    BigDecimal freightAmount, // InvoiceFreight
    BigDecimal incTaxAmount, // Total
    List<CtInvoiceLine> lines) {}
