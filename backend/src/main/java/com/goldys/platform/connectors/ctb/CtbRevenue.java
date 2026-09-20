package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One daily revenue row from CTB's {@code Revenue/SearchRevenues}. */
public record CtbRevenue(
    long revenueId,
    LocalDate revenueDate,
    String departmentName,
    BigDecimal kitchenRevenueTotal,
    BigDecimal totalSales,
    BigDecimal gstTotal,
    String outletName) {}
