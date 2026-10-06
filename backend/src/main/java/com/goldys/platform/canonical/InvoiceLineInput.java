package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A normalized invoice line-item observation from one source. */
public record InvoiceLineInput(
    String sourceSystem,
    String sourceRecordRef,
    String invoiceNumber,
    LocalDate invoiceDate,
    String productNameKey,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal lineTotal,
    String category,
    UUID rawRecordId) {}
