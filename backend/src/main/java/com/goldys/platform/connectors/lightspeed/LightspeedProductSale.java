package com.goldys.platform.connectors.lightspeed;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One per-product row from the Lightspeed "sales by product" report. */
public record LightspeedProductSale(
    LocalDate tradingDate, String productName, BigDecimal quantitySold, BigDecimal amount) {}
