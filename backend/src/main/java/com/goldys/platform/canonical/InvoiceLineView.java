package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A read-only view of one current invoice line, for modules outside the {@code canonical} package. */
public record InvoiceLineView(
    String sourceSystem,
    String invoiceNumber,
    LocalDate invoiceDate,
    String productNameKey,
    BigDecimal lineTotal) {}
