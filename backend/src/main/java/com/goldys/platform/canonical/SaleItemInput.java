package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** The normalized candidate for one sale line item, independent of any JPA entity. */
public record SaleItemInput(
    String sourceSystem,
    String receiptLineId,
    LocalDate tradingDate,
    String saleNumber,
    String itemName,
    String productNumber,
    String sku,
    String categoryName,
    BigDecimal quantitySold,
    BigDecimal amount,
    BigDecimal soldPriceIncTax,
    BigDecimal totalTax,
    BigDecimal costIncTax,
    String orderType,
    String saleType,
    String staffName,
    String registerName,
    String tableNumber,
    UUID rawRecordId,
    com.goldys.platform.semantic.SaleAdjustment adjustment,
    Long siteNumber) {
  public SaleItemInput(
      String sourceSystem,
      String receiptLineId,
      LocalDate tradingDate,
      String saleNumber,
      String itemName,
      String productNumber,
      String sku,
      String categoryName,
      BigDecimal quantitySold,
      BigDecimal amount,
      BigDecimal soldPriceIncTax,
      BigDecimal totalTax,
      BigDecimal costIncTax,
      String orderType,
      String saleType,
      String staffName,
      String registerName,
      String tableNumber,
      UUID rawRecordId,
      com.goldys.platform.semantic.SaleAdjustment adjustment) {
    this(
        sourceSystem,
        receiptLineId,
        tradingDate,
        saleNumber,
        itemName,
        productNumber,
        sku,
        categoryName,
        quantitySold,
        amount,
        soldPriceIncTax,
        totalTax,
        costIncTax,
        orderType,
        saleType,
        staffName,
        registerName,
        tableNumber,
        rawRecordId,
        adjustment,
        null);
  }

  SaleItemInput preserveUnobservedMetadata(
      String previousSaleNumber, String previousStaffName, Long previousSiteNumber) {
    return new SaleItemInput(
        sourceSystem,
        receiptLineId,
        tradingDate,
        observedOrPrevious(saleNumber, previousSaleNumber),
        itemName,
        productNumber,
        sku,
        categoryName,
        quantitySold,
        amount,
        soldPriceIncTax,
        totalTax,
        costIncTax,
        orderType,
        saleType,
        observedOrPrevious(staffName, previousStaffName),
        registerName,
        tableNumber,
        rawRecordId,
        adjustment,
        siteNumber == null ? previousSiteNumber : siteNumber);
  }

  private static String observedOrPrevious(String observation, String previous) {
    if (observation != null && !observation.isBlank()) return observation;
    return previous == null || previous.isBlank() ? null : previous;
  }
}
