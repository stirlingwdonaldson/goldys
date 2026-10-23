package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Known invoice purchases and WET; retained type name for API compatibility. */
public record SupplierCogs(
    String supplier,
    BigDecimal lineTotal,
    BigDecimal wetAmount,
    UUID supplierId,
    int invoiceCount,
    int invoicesWithoutLines,
    List<String> notices) {
  public SupplierCogs(String supplier, BigDecimal total, BigDecimal wet) {
    this(supplier, total, wet, null, 0, 0, List.of());
  }
}
