package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical inventory facts, so modules outside this package never touch
 * the package-private entities or repositories directly.
 */
@Service
public class CanonicalInventoryQuery {
  private final CanonicalInvoiceLineRepository lineRepository;

  public CanonicalInventoryQuery(CanonicalInvoiceLineRepository lineRepository) {
    this.lineRepository = lineRepository;
  }

  /** All current invoice lines, mapped to views. */
  public List<InvoiceLineView> currentInvoiceLines() {
    return lineRepository.findAllCurrent().stream().map(CanonicalInventoryQuery::toView).toList();
  }

  /** Current invoice lines whose invoice date falls in {@code dates}. */
  public List<InvoiceLineView> currentInvoiceLinesForDates(Collection<LocalDate> dates) {
    return currentInvoiceLines().stream().filter(v -> dates.contains(v.invoiceDate())).toList();
  }

  /** All current invoice lines with the full enrichment field set (for line-level reporting). */
  public List<EnrichedInvoiceLine> currentEnrichedLines() {
    return lineRepository.findAllCurrent().stream()
        .map(CanonicalInventoryQuery::toEnriched)
        .toList();
  }

  private static EnrichedInvoiceLine toEnriched(CanonicalInvoiceLine l) {
    return new EnrichedInvoiceLine(
        l.invoiceNumber(),
        l.invoiceDate(),
        l.productNameKey(),
        l.stockCode(),
        l.quantity(),
        l.unitCost(),
        l.lineTotal(),
        l.uom(),
        l.unitQuantity(),
        l.packSize(),
        l.wetAmount());
  }

  private static InvoiceLineView toView(CanonicalInvoiceLine l) {
    return new InvoiceLineView(
        l.sourceSystem(), l.invoiceNumber(), l.invoiceDate(), l.productNameKey(), l.lineTotal());
  }
}
