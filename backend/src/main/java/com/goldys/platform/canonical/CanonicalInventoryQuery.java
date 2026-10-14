package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

  /**
   * The sum of the current {@code lineTotal} for one invoice's lines, or {@link BigDecimal#ZERO}
   * when the invoice has no current lines. Used by the reconciliation path to compare CSV line sums
   * against header totals.
   */
  public BigDecimal currentLineTotalForInvoice(String invoiceNumber) {
    if (invoiceNumber == null) {
      return BigDecimal.ZERO;
    }
    return lineRepository.findCurrentByInvoiceNumber(invoiceNumber).stream()
        .map(CanonicalInvoiceLine::lineTotal)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * The sum of the current {@code lineTotal} for one invoice's CSV-sourced lines only (origin null
   * or {@code 'CSV'}, i.e. not {@code 'PDF'}), or {@link BigDecimal#ZERO} when there are none. Used
   * by the convergence check to compare the CSV-only total against the header total without the
   * still-current PDF gap lines, which would otherwise over-count the CSV shortfall.
   */
  public BigDecimal currentCsvLineTotalForInvoice(String invoiceNumber) {
    if (invoiceNumber == null) {
      return BigDecimal.ZERO;
    }
    return lineRepository.findCurrentCsvByInvoiceNumber(invoiceNumber).stream()
        .map(CanonicalInvoiceLine::lineTotal)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        l.wetAmount(),
        l.lineTotalIncTax(),
        l.lineGstAmount(),
        l.lineTaxRate(),
        l.incGst());
  }

  private static InvoiceLineView toView(CanonicalInvoiceLine l) {
    return new InvoiceLineView(
        l.sourceSystem(), l.invoiceNumber(), l.invoiceDate(), l.productNameKey(), l.lineTotal());
  }
}
