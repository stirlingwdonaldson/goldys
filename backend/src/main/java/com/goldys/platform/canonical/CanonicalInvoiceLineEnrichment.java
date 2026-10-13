package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies PDF enrichment to a canonical line: finds the current line and supersedes it with the
 * PDF's uom/unit_quantity/pack_size/wet_amount, keeping the CSV's quantity/unit_cost/line_total
 * (CSV is authoritative). The PDF line is mapped to one of the invoice's current lines by {@link
 * InvoiceLineMatcher}, which tolerates the code/description drift the CTB export and its PDFs
 * exhibit (synthetic {@code PDF_<hex>} codes, variant codes, noisy descriptions) and refuses to
 * guess when more than one line wins (spec §7).
 *
 * <p><b>Explicitly per-line transactional:</b> each {@link #enrich} call is its own transaction.
 * This is deliberate rather than a single whole-PDF transaction — it keeps the (potentially slow,
 * network-bound) text extraction/LLM parse outside any DB transaction, and means one anomalous line
 * cannot roll back the rest of a PDF's enrichment.
 *
 * <p><b>Provenance:</b> the successor reuses the CSV line's {@code rawRecordId}, never a PDF raw id
 * — the enriched row's provenance stays anchored to the authoritative CSV source, and only the
 * PDF-owned fields change.
 */
@Service
public class CanonicalInvoiceLineEnrichment {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalInvoiceLineRepository repository;

  public CanonicalInvoiceLineEnrichment(CanonicalInvoiceLineRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public EnrichmentResult enrich(
      String invoiceNumber,
      String stockCode,
      String description,
      String uom,
      BigDecimal unitQuantity,
      BigDecimal packSize,
      BigDecimal wetAmount,
      BigDecimal quantity,
      BigDecimal amountExTax) {
    List<CanonicalInvoiceLine> lines = repository.lockCurrentByInvoiceNumber(invoiceNumber);
    InvoiceLineMatcher.Selection selection =
        InvoiceLineMatcher.select(stockCode, description, quantity, amountExTax, candidates(lines));
    return switch (selection.result()) {
      case AMBIGUOUS -> EnrichmentResult.AMBIGUOUS; // several lines win; skip rather than guess
      case NO_MATCH -> EnrichmentResult.NO_MATCH; // no CSV line (pdf-only line)
      case MATCH ->
          enrichLine(lines.get(selection.index()), uom, unitQuantity, packSize, wetAmount);
    };
  }

  /**
   * True when the invoice has a current line the given PDF line maps to. Used to retire flags once
   * a replay with a better matcher resolves them. Quantity/amount are not available on a flag row,
   * so the tie-break does not apply here.
   */
  @Transactional(readOnly = true)
  public boolean hasMatch(String invoiceNumber, String stockCode, String description) {
    List<CanonicalInvoiceLine> lines = repository.findCurrentByInvoiceNumber(invoiceNumber);
    return InvoiceLineMatcher.select(stockCode, description, null, null, candidates(lines)).result()
        == InvoiceLineMatcher.Result.MATCH;
  }

  private static List<InvoiceLineMatcher.Candidate> candidates(List<CanonicalInvoiceLine> lines) {
    return lines.stream()
        .map(
            l ->
                new InvoiceLineMatcher.Candidate(
                    l.stockCode(), l.productNameKey(), l.quantity(), l.lineTotal()))
        .toList();
  }

  private EnrichmentResult enrichLine(
      CanonicalInvoiceLine l,
      String uom,
      BigDecimal unitQuantity,
      BigDecimal packSize,
      BigDecimal wetAmount) {
    if (Objects.equals(l.uom(), uom)
        && sameAmount(l.unitQuantity(), unitQuantity)
        && sameAmount(l.packSize(), packSize)
        && sameAmount(l.wetAmount(), wetAmount)) {
      return EnrichmentResult.UNCHANGED; // already enriched
    }
    Instant now = CLOCK.instant();
    l.supersede(now);
    repository.saveAndFlush(l);
    repository.save(
        CanonicalInvoiceLine.create(
            l.logicalEntityId(),
            l.sourceSystem(),
            l.sourceRecordRef(),
            l.rawRecordId(),
            now,
            now,
            l.invoiceNumber(),
            l.invoiceDate(),
            l.productNameKey(),
            l.stockCode(),
            l.quantity(),
            l.unitCost(),
            l.lineTotal(),
            l.category(),
            uom,
            unitQuantity,
            packSize,
            wetAmount,
            l.origin()));
    return EnrichmentResult.ENRICHED;
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }
}
