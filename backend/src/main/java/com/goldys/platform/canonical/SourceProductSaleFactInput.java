package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A source-native product-day measure observation (CTB cost/tax/discount/unit price/profit). {@code
 * uniqueGroupId} is the source's grouping key: when it distinguishes two otherwise-identical rows
 * it is part of the identity, so distinct rows that share a stock code and day are preserved.
 */
public record SourceProductSaleFactInput(
    String sourceSystem,
    String sourceRecordRef,
    LocalDate tradingDate,
    String stockCode,
    BigDecimal costPerUnit,
    BigDecimal foodCost,
    BigDecimal totalFoodCost,
    BigDecimal profit,
    BigDecimal discountedAmount,
    BigDecimal taxAmount,
    BigDecimal unitPrice,
    String uniqueGroupId,
    UUID rawRecordId) {

  /**
   * The stable source identity for one source product-day(-group) observation: {@code
   * "<stockCode>@<tradingDate>"}, extended with the source grouping key when one is present.
   */
  public static String sourceRecordRef(
      String stockCode, LocalDate tradingDate, String uniqueGroupId) {
    String base = stockCode + "@" + tradingDate;
    return uniqueGroupId == null || uniqueGroupId.isBlank() ? base : base + "@" + uniqueGroupId;
  }
}
