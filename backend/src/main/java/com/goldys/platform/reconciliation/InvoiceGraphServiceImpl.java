package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichedInvoiceLine;
import com.goldys.platform.canonical.InvoiceMetadataView;
import com.goldys.platform.semantic.InvoiceGraphNode;
import com.goldys.platform.semantic.InvoiceGraphQuery;
import com.goldys.platform.semantic.LineGraphNode;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Supplier-qualified invoice reads; supplier amounts are known ex-tax line subtotals. */
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
    return InvoicePurchaseBreakdown.summarize(
            invoices.currentInvoices(), inventory.currentEnrichedLines(), from, to)
        .stream()
        .map(
            s ->
                new SupplierGraphNode(
                    s.supplier(),
                    s.invoiceCount(),
                    s.lineTotal(),
                    s.supplierId(),
                    s.invoicesWithoutLines(),
                    s.notices()))
        .toList();
  }

  @Override
  public List<InvoiceGraphNode> invoicesForSupplier(String supplier, LocalDate from, LocalDate to) {
    List<InvoiceMetadataView> matches =
        invoices.currentInvoices().stream().filter(i -> supplier.equals(i.supplierName())).toList();
    if (matches.stream().map(InvoiceMetadataView::supplierId).distinct().count() > 1)
      throw new IllegalArgumentException("Ambiguous supplier name: use supplier identity");
    return invoiceNodes(matches, from, to);
  }

  @Override
  public List<InvoiceGraphNode> invoicesForSupplierId(
      java.util.UUID supplier, LocalDate from, LocalDate to) {
    return invoiceNodes(
        invoices.currentInvoices().stream().filter(i -> supplier.equals(i.supplierId())).toList(),
        from,
        to);
  }

  private List<InvoiceGraphNode> invoiceNodes(
      List<InvoiceMetadataView> headers, LocalDate from, LocalDate to) {
    return headers.stream()
        .filter(i -> inRange(i.invoiceDate(), from, to))
        .sorted(Comparator.comparing(InvoiceMetadataView::invoiceDate).reversed())
        .map(
            i ->
                new InvoiceGraphNode(
                    i.invoiceNumber(),
                    i.invoiceDate(),
                    i.totalAmount(),
                    i.purchaseNumber(),
                    i.pdfFilename(),
                    i.invoiceId(),
                    i.amountExTax()))
        .toList();
  }

  @Override
  public List<LineGraphNode> linesForInvoice(String invoiceNumber) {
    invoices.requireUnambiguousNumber(invoiceNumber);
    java.util.UUID id =
        invoices
            .invoiceLogicalIdForInvoiceNumber(invoiceNumber)
            .orElseThrow(() -> new java.util.NoSuchElementException("Unknown invoice"));
    return linesForInvoiceId(id);
  }

  @Override
  public List<LineGraphNode> linesForInvoiceId(java.util.UUID id) {
    if (invoices.currentInvoice(id).isEmpty())
      throw new java.util.NoSuchElementException("Unknown invoice");
    return inventory.enrichedLinesForInvoice(id).stream()
        .sorted(Comparator.comparing(EnrichedInvoiceLine::lineTotal).reversed())
        .map(
            l ->
                new LineGraphNode(
                    l.productNameKey(),
                    l.stockCode(),
                    l.quantity(),
                    l.unitCost(),
                    l.lineTotal(),
                    l.uom()))
        .toList();
  }

  private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
    return date != null && !date.isBefore(from) && !date.isAfter(to);
  }
}
