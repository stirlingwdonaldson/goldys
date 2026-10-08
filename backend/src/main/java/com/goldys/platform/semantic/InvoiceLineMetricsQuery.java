package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;

/**
 * Line-level invoice enrichment reads for reporting: unit cost per UOM and COGS/WET by supplier.
 * These fields are single-source (CTB) and PDF-only, so there is no cross-source conflict to
 * resolve — they are read directly from canonical enrichment rather than a resolved projection.
 */
public interface InvoiceLineMetricsQuery {

  /** Blended unit cost per UOM over the inclusive date range, ascending by UOM. */
  List<UomUnitCost> unitCostByUom(LocalDate from, LocalDate to);

  /**
   * Purchases and WET grouped by supplier over the inclusive date range, descending by line total.
   */
  List<SupplierCogs> cogsBySupplier(LocalDate from, LocalDate to);
}
