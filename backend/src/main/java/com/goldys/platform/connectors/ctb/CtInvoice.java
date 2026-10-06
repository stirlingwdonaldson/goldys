package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A parsed invoice metadata row from the CTB invoice CSV (provisional schema). */
public record CtInvoice(
    String supplierName,
    String invoiceNumber,
    LocalDate invoiceDate,
    LocalDate dueDate,
    BigDecimal totalAmount) {}
