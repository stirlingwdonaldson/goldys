package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.List;

/** A product/day whose sources disagree ("conflict") or where a source is absent ("missing"). */
public record ProductSalesConflict(
    LocalDate tradingDate,
    String productNameKey,
    List<ProductSourceTotal> sources,
    String status) {}
