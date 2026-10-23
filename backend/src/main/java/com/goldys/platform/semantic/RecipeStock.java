package com.goldys.platform.semantic;

import java.math.BigDecimal;

/** Source-independent recipe observation; quantities and costs retain their signs. */
public record RecipeStock(
    int lineId,
    Integer stockId,
    String stockCode,
    String description,
    String supplierName,
    String supplierStockCode,
    BigDecimal quantity,
    Integer unitId,
    String unit,
    BigDecimal stockPackSize,
    Integer stockUnitId,
    String stockUnit,
    BigDecimal costPerPack,
    BigDecimal cost,
    BigDecimal usablePercent,
    BigDecimal wastagePercent,
    Boolean brokenLink) {}
