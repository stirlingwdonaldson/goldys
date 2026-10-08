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
    for (EnrichedInvoiceLine line : inRange(from, to)) {
      BigDecimal[] acc =
          byUom.computeIfAbsent(
              line.uom(), k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
      acc[0] = acc[0].add(line.lineTotal());
      acc[1] = acc[1].add(effectiveQuantity(line));
    }
    List<UomUnitCost> out = new ArrayList<>();
    for (Map.Entry<String, BigDecimal[]> e : byUom.entrySet()) {
      BigDecimal[] acc = e.getValue();
      out.add(new UomUnitCost(e.getKey(), acc[0], acc[1], unitCost(acc[0], acc[1])));
    }
    out.sort(
        Comparator.comparing(UomUnitCost::uom, Comparator.nullsLast(Comparator.naturalOrder())));
    return out;
  }

  /**
   * Quantity in base units, so the blended unit cost is per the UOM's base unit: the CSV's coarse
   * {@code quantity} times the PDF's {@code unitQuantity} (the per-pack count) when it was
   * enriched, otherwise the coarse quantity alone. This is a best-effort blend — the CSV's coarse
   * quantity (which falls back to 1 when unparseable) means a few lines contribute an imprecise
   * denominator, so the figure is an approximation, not an exact cost-per-unit.
   */
  private static BigDecimal effectiveQuantity(EnrichedInvoiceLine line) {
    if (line.quantity() == null) {
      return BigDecimal.ZERO;
    }
    if (line.unitQuantity() != null) {
      return line.quantity().multiply(line.unitQuantity());
    }
    return line.quantity();
  }

  @Override
  public List<SupplierCogs> cogsBySupplier(LocalDate from, LocalDate to) {
    Map<String, String> supplierByInvoice = invoices.currentSupplierNames();
    Map<String, BigDecimal[]> bySupplier = new LinkedHashMap<>(); // [lineTotal, wetAmount]
    for (EnrichedInvoiceLine line : inRange(from, to)) {
      String supplier = supplierByInvoice.getOrDefault(line.invoiceNumber(), "Unknown");
      BigDecimal[] acc =
          bySupplier.computeIfAbsent(
              supplier, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
      acc[0] = acc[0].add(line.lineTotal());
      if (line.wetAmount() != null) {
        acc[1] = acc[1].add(line.wetAmount());
      }
    }
    List<SupplierCogs> out = new ArrayList<>();
    for (Map.Entry<String, BigDecimal[]> e : bySupplier.entrySet()) {
      BigDecimal[] acc = e.getValue();
      out.add(new SupplierCogs(e.getKey(), acc[0], acc[1]));
    }
    out.sort(Comparator.comparing(SupplierCogs::lineTotal).reversed());
    return out;
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
