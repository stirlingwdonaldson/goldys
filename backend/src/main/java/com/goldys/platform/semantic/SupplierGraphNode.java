package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Known ex-tax invoice-line purchases, with source coverage qualifications. */
public record SupplierGraphNode(
    String name,
    int invoiceCount,
    BigDecimal totalSpend,
    UUID supplierId,
    int invoicesWithoutLines,
    List<String> notices) {
  public SupplierGraphNode(String name, int count, BigDecimal amount) {
    this(name, count, amount, null, 0, List.of());
  }
}
