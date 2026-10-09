package com.goldys.platform.semantic;

import java.math.BigDecimal;

/** Purchases grouped by supplier for the invoice graph; `totalSpend` is null when unknown. */
public record SupplierGraphNode(String name, int invoiceCount, BigDecimal totalSpend) {}
