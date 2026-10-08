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
 * (CSV is authoritative). Matching is on {@code (invoice_number, stock_code)} first, falling back
 * to {@code (invoice_number, normalized description)} when the stock code is absent or unmatched
 * (spec §7).
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
      BigDecimal wetAmount) {
    List<CanonicalInvoiceLine> matches =
        stockCode == null || stockCode.isBlank()
            ? List.of()
            : repository.lockCurrentByInvoiceNumberAndStockCode(invoiceNumber, stockCode);
    if (matches.isEmpty() && description != null && !description.isBlank()) {
      matches =
          repository.lockCurrentByInvoiceNumberAndProductNameKey(
              invoiceNumber, ProductNameKey.normalize(description));
    }
    if (matches.isEmpty()) {
      return EnrichmentResult.NO_MATCH; // no CSV line (pdf-only line)
    }
    if (matches.size() > 1) {
      return EnrichmentResult.AMBIGUOUS; // duplicate code/description; skip rather than guess
    }
    CanonicalInvoiceLine l = matches.get(0);
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
            wetAmount));
    return EnrichmentResult.ENRICHED;
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }
}
