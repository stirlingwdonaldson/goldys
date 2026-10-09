package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichedInvoiceLine;
import com.goldys.platform.canonical.InvoiceMetadataView;
import com.goldys.platform.semantic.InvoiceGraphNode;
import com.goldys.platform.semantic.InvoiceGraphQuery;
import com.goldys.platform.semantic.LineGraphNode;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * {@link InvoiceGraphQuery} over canonical invoice facts. Supplier identity is the normalized
 * name on the invoice header; lines whose invoice number has no header bucket to {@code "Unknown"}.
 * Spend is the sum of line totals, falling back to header totals when a supplier has invoices but
 * no lines.
 */
@Service
public class InvoiceGraphServiceImpl implements InvoiceGraphQuery {

  private static final String UNKNOWN = "Unknown";

  private final CanonicalInvoiceQuery invoices;
  private final CanonicalInventoryQuery inventory;

  public InvoiceGraphServiceImpl(
      CanonicalInvoiceQuery invoices, CanonicalInventoryQuery inventory) {
    this.invoices = invoices;
    this.inventory = inventory;
  }

  @Override
  public List<SupplierGraphNode> suppliers(LocalDate from, LocalDate to) {
    Map<String, Integer> invoiceCount = new LinkedHashMap<>();
    Map<String, BigDecimal> headerSpend = new LinkedHashMap<>();
    for (InvoiceMetadataView i : invoices.currentInvoices()) {
      if (!inRange(i.invoiceDate(), from, to)) {
        continue;
      }
      invoiceCount.merge(i.supplierName(), 1, Integer::sum);
      if (i.totalAmount() != null) {
        headerSpend.merge(i.supplierName(), i.totalAmount(), BigDecimal::add);
      }
    }

    Map<String, String> supplierByInvoice = invoices.currentSupplierNames();
    Map<String, BigDecimal> lineSpend = new LinkedHashMap<>();
    BigDecimal unknownSpend = BigDecimal.ZERO;
    boolean hasUnknown = false;
    for (EnrichedInvoiceLine line : inventory.currentEnrichedLines()) {
      if (!inRange(line.invoiceDate(), from, to) || line.lineTotal() == null) {
        continue;
      }
      String supplier = supplierByInvoice.get(line.invoiceNumber());
      if (supplier == null) {
        hasUnknown = true;
        unknownSpend = unknownSpend.add(line.lineTotal());
      } else {
        lineSpend.merge(supplier, line.lineTotal(), BigDecimal::add);
      }
    }

    List<SupplierGraphNode> out = new ArrayList<>();
    for (Map.Entry<String, Integer> e : invoiceCount.entrySet()) {
      String supplier = e.getKey();
      BigDecimal spend = lineSpend.get(supplier);
      if (spend == null) {
        spend = headerSpend.get(supplier); // invoices but no lines: fall back to header totals
      }
      out.add(new SupplierGraphNode(supplier, e.getValue(), spend));
    }
    for (String supplier : lineSpend.keySet()) {
      if (!invoiceCount.containsKey(supplier)) {
        out.add(new SupplierGraphNode(supplier, 0, lineSpend.get(supplier)));
      }
    }
    out.sort(Comparator.comparing(
        SupplierGraphNode::totalSpend, Comparator.nullsLast(Comparator.reverseOrder())));
    if (hasUnknown) {
      out.add(new SupplierGraphNode(UNKNOWN, 0, unknownSpend));
    }
    return out;
  }

  @Override
  public List<InvoiceGraphNode> invoicesForSupplier(
      String supplier, LocalDate from, LocalDate to) {
    return invoices.currentInvoices().stream()
        .filter(i -> supplier.equals(i.supplierName()))
        .filter(i -> inRange(i.invoiceDate(), from, to))
        .sorted(Comparator.comparing(InvoiceMetadataView::invoiceDate).reversed())
        .map(i -> new InvoiceGraphNode(
            i.invoiceNumber(), i.invoiceDate(), i.totalAmount(),
            i.purchaseNumber(), i.pdfFilename()))
        .toList();
  }

  @Override
  public List<LineGraphNode> linesForInvoice(String invoiceNumber) {
    return inventory.currentEnrichedLines().stream()
        .filter(l -> invoiceNumber.equals(l.invoiceNumber()))
        .sorted(Comparator.comparing(EnrichedInvoiceLine::lineTotal).reversed())
        .map(l -> new LineGraphNode(
            l.productNameKey(), l.stockCode(), l.quantity(), l.unitCost(),
            l.lineTotal(), l.uom()))
        .toList();
  }

  private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
    return date != null && !date.isBefore(from) && !date.isAfter(to);
  }
}
