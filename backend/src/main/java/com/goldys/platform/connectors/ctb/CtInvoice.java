package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A parsed invoice from CTB's Custom Invoice Export CSV: header fields plus its line items. The raw
 * CSV bytes are always preserved separately; this holds the fields the canonical model consumes.
 * {@code pdfFilenames} is the distinct, first-seen set of {@code PDF} column values across the
 * invoice's rows — usually one, empty for a PDF-less invoice, and more than one for an ambiguous
 * invoice whose rows reference different PDFs.
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
    List<String> pdfFilenames, // distinct PDF column values, first-seen order
    List<CtInvoiceLine> lines) {}
