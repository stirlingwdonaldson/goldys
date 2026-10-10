package com.goldys.platform.connectors.lightspeed;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One receipt-line row from the Lightspeed Insights "sales-details" scheduled CSV. */
public record LightspeedSaleItem(
    String receiptLineId,
    LocalDate saleDate,
    String saleNumber,
    String itemName,
    String productNumber,
    String sku,
    String categoryName,
    BigDecimal quantity,
    BigDecimal amount,
    BigDecimal soldPriceIncTax,
    BigDecimal totalTax,
    BigDecimal costIncTax,
    String orderType,
    String saleType,
    String staffName,
    String registerName,
    String tableNumber) {}
