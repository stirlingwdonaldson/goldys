package com.goldys.platform.semantic;

import java.time.LocalDate;

/**
 * Independent dimensions; UNKNOWN is an explicit abstention, never inferred from menu hierarchy.
 */
public record ProductReportingMapping(
    Scope scope,
    String sourceSystem,
    String entityKey,
    LocalDate effectiveFrom,
    SalesNature salesNature,
    StockBucket stockBucket,
    ItemRole itemRole,
    BusinessArea businessArea,
    Lifecycle lifecycle,
    String reason) {
  public enum Scope {
    PRODUCT_NAME,
    CTB_PRODUCT,
    CTB_RECIPE,
    CTB_STOCK,
    WASTE_LINE
  }

  public enum SalesNature {
    FOOD,
    BEVERAGE,
    OTHER,
    UNKNOWN
  }

  public enum StockBucket {
    KEGS,
    WINE,
    SPIRITS,
    TINNIES,
    OTHER_BEVERAGE,
    MERCHANDISE,
    MEAT,
    SEAFOOD,
    POULTRY,
    DAIRY,
    FROZEN,
    BREAD,
    UNKNOWN
  }

  public enum ItemRole {
    SALE_PRODUCT,
    INGREDIENT,
    MODIFIER,
    MERCHANDISE,
    OTHER,
    UNKNOWN
  }

  public enum BusinessArea {
    PUB,
    CAFE,
    SHARED,
    UNKNOWN
  }

  public enum Lifecycle {
    CURRENT,
    HISTORICAL,
    RETIRED,
    UNKNOWN
  }
}
