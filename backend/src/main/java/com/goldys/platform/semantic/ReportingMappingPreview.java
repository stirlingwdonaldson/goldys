package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportingMappingPreview(
    ProductReportingMapping mapping,
    boolean reviewable,
    String evidence,
    LocalDate through,
    long observedRows,
    BigDecimal knownAmount,
    String amountBasis,
    List<String> notes) {}
