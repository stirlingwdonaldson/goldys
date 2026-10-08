package com.goldys.platform.semantic;

import java.math.BigDecimal;

/**
 * Blended unit cost for one unit-of-measure over a period: total line total divided by total
 * quantity, so a UOM like KG yields a $/kg figure and EACH a $/each figure. Null UOM (lines the PDF
 * did not enrich) is grouped under a {@code null} key.
 */
public record UomUnitCost(
    String uom, BigDecimal lineTotal, BigDecimal quantity, BigDecimal unitCost) {}
