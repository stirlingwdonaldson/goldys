package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One invoice header for the invoice graph. */
public record InvoiceGraphNode(
    String invoiceNumber,
    LocalDate invoiceDate,
    BigDecimal totalAmount,
    String purchaseNumber,
    String pdfFilename) {}
