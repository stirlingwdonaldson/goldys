package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A normalized daily sales total from one source, to be recorded as a canonical version. */
record DailySalesInput(
    String sourceSystem,
    LocalDate tradingDate,
    BigDecimal totalSales,
    BigDecimal gstTotal,
    BigDecimal netTotal,
    UUID rawRecordId) {}
