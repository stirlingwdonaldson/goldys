package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A source-native product-day measure observation (CTB cost/tax/discount/unit price/profit). {@code
 * uniqueGroupId} is the source's grouping key: when it distinguishes two otherwise-identical rows
 * it is part of the legacy identity. CTB report snapshots instead qualify rows by product/site/day
 * and report-local row ID in sourceRecordRef; uniqueGroupId remains an unmodified source field.
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
    UUID rawRecordId,
    BigDecimal quantitySold,
    BigDecimal amount,
    String productName,
    Integer siteNumber) {
  public SourceProductSaleFactInput(
      String source,
      String ref,
      LocalDate day,
      String code,
      BigDecimal cpu,
      BigDecimal fc,
      BigDecimal tfc,
      BigDecimal profit,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal price,
      String group,
      UUID raw) {
    this(
        source, ref, day, code, cpu, fc, tfc, profit, discount, tax, price, group, raw, null, null,
        null, null);
  }

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
