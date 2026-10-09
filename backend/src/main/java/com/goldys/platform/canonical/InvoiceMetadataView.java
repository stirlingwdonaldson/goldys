package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A read-only view of one current invoice's metadata, for modules outside this package. */
public record InvoiceMetadataView(
    String supplierName,
    String invoiceNumber,
    LocalDate invoiceDate,
    BigDecimal totalAmount,
    String purchaseNumber,
    String pdfFilename) {}
