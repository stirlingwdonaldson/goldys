package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A normalized per-product sales total from one source, to be recorded canonically. */
public record ProductSalesInput(
    String sourceSystem,
    LocalDate tradingDate,
    String productNameKey,
    BigDecimal quantitySold,
    BigDecimal amount,
    UUID rawRecordId) {}
