package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A normalized stock-count line observation from one source. {@code countedDate} is the stocktake's
 * date (resolved from the header, which the detail payload does not carry); {@code lineId} is the
 * per-stocktake line id (the source {@code id} field, not {@code stocktakeDetailId}).
 */
public record StockCountInput(
    String sourceSystem,
    int stocktakeId,
    int lineId,
    LocalDate countedDate,
    String productNameKey,
    BigDecimal quantityOnHand,
    Integer stockId,
    String stockCode,
    String unitOfMeasurementName,
    BigDecimal unitCost,
    BigDecimal lineValue,
    String supplierName,
    String areaName,
    Integer recipeId,
    UUID rawRecordId) {}
