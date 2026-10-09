package com.goldys.platform.semantic;

import java.math.BigDecimal;

/** One invoice line for the invoice graph; `uom` is null when not PDF-enriched. */
public record LineGraphNode(
    String productNameKey,
    String stockCode,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal lineTotal,
    String uom) {}
