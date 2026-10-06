package com.goldys.platform.connectors.lightspeed;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One reconciliation-date aggregate row from the Lightspeed Insights scheduled CSV. */
public record LightspeedInsightsSale(
    LocalDate reconciliationDate,
    BigDecimal totalIncTax,
    BigDecimal totalTax,
    BigDecimal totalAdjustmentIncTax,
    BigDecimal totalAdjustmentTax) {}
