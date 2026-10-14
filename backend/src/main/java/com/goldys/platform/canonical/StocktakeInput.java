package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A normalized stocktake-header observation from one source. {@code totalStockValue} is the
 * source-computed total value of the stocktake; it is {@code null} when the source sent nothing
 * parseable.
 */
public record StocktakeInput(
    String sourceSystem,
    int stocktakeId,
    LocalDate stocktakeDate,
    String description,
    String outletName,
    BigDecimal totalStockValue,
    Boolean isPartial,
    Boolean isLocked,
    UUID rawRecordId) {}
