package com.goldys.platform.connectors.lightspeed;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One site-96181 sale-closed-date total; adjustments are separate evidence, already in revenue. */
public record LightspeedInsightsSale(
    LocalDate saleClosedDate,
    BigDecimal netTotal,
    BigDecimal totalIncTax,
    BigDecimal totalTax,
    BigDecimal totalAdjustmentIncTax) {}
