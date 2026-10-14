package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A source-native product-day measure observation (CTB cost/tax/discount/unit price). */
public record SourceProductSaleFactInput(
    String sourceSystem,
    String sourceRecordRef,
    LocalDate tradingDate,
    String stockCode,
    BigDecimal costPerUnit,
    BigDecimal totalFoodCost,
    BigDecimal discountedAmount,
    BigDecimal taxAmount,
    BigDecimal unitPrice,
    UUID rawRecordId) {}
