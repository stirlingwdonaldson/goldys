package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichedInvoiceLine;
import com.goldys.platform.semantic.InvoiceLineMetricsQuery;
import com.goldys.platform.semantic.SupplierCogs;
import com.goldys.platform.semantic.UomUnitCost;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * {@link InvoiceLineMetricsQuery} over canonical invoice lines. The enrichment fields (UOM, pack
 * size, unit quantity, WET) are single-source CTB PDF data with no cross-source conflict, so they
 * are aggregated straight from canonical enrichment rather than a resolved projection — the same
 * read the inventory projector already performs.
 */
@Service
public class InvoiceLineMetricsServiceImpl implements InvoiceLineMetricsQuery {

  private static final int SCALE = 4;

  private final CanonicalInventoryQuery inventory;
  private final CanonicalInvoiceQuery invoices;

  public InvoiceLineMetricsServiceImpl(
      CanonicalInventoryQuery inventory, CanonicalInvoiceQuery invoices) {
    this.inventory = inventory;
    this.invoices = invoices;
  }

  @Override
  public List<UomUnitCost> unitCostByUom(LocalDate from, LocalDate to) {
    Map<String, BigDecimal[]> byUom = new LinkedHashMap<>(); // [lineTotal, effective quantity]
    Map<String, java.util.Set<String>> items = new LinkedHashMap<>();
    for (EnrichedInvoiceLine line : inRange(from, to)) {
      BigDecimal[] acc =
          byUom.computeIfAbsent(
              line.uom(), k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
      acc[0] = acc[0].add(line.lineTotal());
      BigDecimal quantity = effectiveQuantity(line);
      acc[1] = acc[1] == null || quantity == null ? null : acc[1].add(quantity);
      items
          .computeIfAbsent(line.uom(), key -> new java.util.HashSet<>())
          .add(line.stockCode() == null ? "unknown:" + line.productNameKey() : line.stockCode());
    }
    List<UomUnitCost> out = new ArrayList<>();
    for (Map.Entry<String, BigDecimal[]> e : byUom.entrySet()) {
      BigDecimal[] acc = e.getValue();
      out.add(
          new UomUnitCost(
              e.getKey(),
              acc[0],
              acc[1],
              e.getKey() == null || items.get(e.getKey()).size() != 1
                  ? null
                  : unitCost(acc[0], acc[1])));
    }
    out.sort(
        Comparator.comparing(UomUnitCost::uom, Comparator.nullsLast(Comparator.naturalOrder())));
    return out;
  }

  /**
   * Quantity in base units, so the blended unit cost is per the UOM's base unit: the CSV's coarse
   * {@code quantity} times the PDF's {@code unitQuantity} (the per-pack count) when it was
   * enriched, otherwise the coarse quantity alone. This is a best-effort blend — the CSV's coarse
   * quantity (which remains unknown when unparseable) means a few lines contribute an imprecise
   * denominator, so the figure is an approximation, not an exact cost-per-unit.
   */
  private static BigDecimal effectiveQuantity(EnrichedInvoiceLine line) {
    if (line.quantity() == null || line.uom() == null) {
      return null;
    }
    if (line.unitQuantity() != null) {
      return line.quantity().multiply(line.unitQuantity());
    }
    return line.quantity();
  }

  @Override
  public List<SupplierCogs> cogsBySupplier(LocalDate from, LocalDate to) {
    return InvoicePurchaseBreakdown.summarize(
        invoices.currentInvoices(), inventory.currentEnrichedLines(), from, to);
  }

  private List<EnrichedInvoiceLine> inRange(LocalDate from, LocalDate to) {
    return inventory.currentEnrichedLines().stream()
        .filter(l -> !l.invoiceDate().isBefore(from) && !l.invoiceDate().isAfter(to))
        .toList();
  }

  /** Blended cost per base unit; null when there is no quantity to divide by. */
  private static BigDecimal unitCost(BigDecimal lineTotal, BigDecimal quantity) {
    if (quantity == null || quantity.signum() == 0) {
      return null;
    }
    return lineTotal.divide(quantity, SCALE, RoundingMode.HALF_UP);
  }
}
