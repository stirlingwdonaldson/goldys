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
    Integer quantitySold,
    BigDecimal amount,
    BigDecimal soldPriceIncTax,
    BigDecimal totalTax,
    BigDecimal costIncTax,
    String orderType,
    String saleType,
    String staffName,
    String registerName,
    String tableNumber,
    UUID rawRecordId) {}
