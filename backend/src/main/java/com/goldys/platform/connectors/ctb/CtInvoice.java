package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A parsed invoice metadata row from CTB's Custom Invoice Export CSV. Holds the full column set of
 * the export, not just the fields the canonical model stores — the raw CSV bytes are always
 * preserved separately, but keeping every column here means nothing the export supplies is dropped
 * at the parse boundary.
 */
public record CtInvoice(
    String supplierName,
    String purchaseNumber,
    LocalDate invoiceDate,
    String invoiceNumber,
    String accountNumber,
    BigDecimal amountExTax,
    String taxCode,
    BigDecimal gstAmount,
    BigDecimal freightAmount,
    BigDecimal freightGstAmount,
    BigDecimal incTaxAmount) {}
