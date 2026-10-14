package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A normalized per-product sales total from one source, to be recorded canonically. {@code
 * stockCode} is the source's own product code (CTB stock code, or Lightspeed where present) and is
 * nullable when the source does not supply one.
 */
public record ProductSalesInput(
    String sourceSystem,
    LocalDate tradingDate,
    String productNameKey,
    BigDecimal quantitySold,
    BigDecimal amount,
    String stockCode,
    UUID rawRecordId) {}
