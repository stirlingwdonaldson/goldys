package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A normalized supplier-invoice metadata fact from one source. */
public record InvoiceInput(
    String sourceSystem,
    String invoiceNumber,
    String supplierName,
    LocalDate invoiceDate,
    LocalDate dueDate,
    BigDecimal totalAmount,
    UUID rawRecordId) {}
