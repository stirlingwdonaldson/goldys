package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A typed view of one current sale line item, for the Sale-items list endpoint. */
public record SaleItemRow(
    LocalDate tradingDate,
    String saleNumber,
    String receiptLineId,
    String itemName,
    String productNumber,
    String sku,
    String categoryName,
    Integer quantitySold,
    BigDecimal amount,
    BigDecimal soldPriceIncTax,
    BigDecimal totalTax,
    BigDecimal costIncTax,
    String orderType,
    String saleType,
    String staffName,
    String registerName,
    String tableNumber) {}
