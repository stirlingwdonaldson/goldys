package com.goldys.platform.connectors.lightspeed;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One sale row from the Lightspeed Insights scheduled CSV. */
public record LightspeedInsightsSale(
    LocalDate saleDate,
    String saleNumber,
    String saleType,
    BigDecimal totalIncTax,
    BigDecimal totalTax,
    BigDecimal totalAdjustmentIncTax) {}
