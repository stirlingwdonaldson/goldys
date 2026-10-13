package com.goldys.platform.reporting;

import java.time.LocalDate;

/** Input for {@link ToolId#GET_SALES_BY_CATEGORY}: the inclusive date range to total over. */
public record GetSalesByCategoryInput(LocalDate startDate, LocalDate endDate)
    implements ToolInput {}
