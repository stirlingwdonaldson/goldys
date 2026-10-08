package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies PDF enrichment to a canonical line: finds the current line by (invoice_number,
 * stock_code) and supersedes it with the PDF's uom/unit_quantity/pack_size/wet_amount, keeping the
 * CSV's quantity/unit_cost/line_total (CSV is authoritative). A no-op when no line matches, when
 * the stock code is ambiguous (more than one current line), or when the fields are already present.
 *
 * <p>This deliberately does NOT route through {@link CanonicalInvoiceLineService#record}:
 * enrichment changes only the PDF-owned fields, which {@link CanonicalInvoiceLine#sameFact} must
 * not compare — otherwise a later CSV re-ingest (which supplies {@code null} for those fields)
 * would un-enrich the line and churn versions on every poll.
 */
@Service
public class CanonicalInvoiceLineEnrichment {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalInvoiceLineRepository repository;

  public CanonicalInvoiceLineEnrichment(CanonicalInvoiceLineRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void enrich(
      String invoiceNumber,
      String stockCode,
      String uom,
      BigDecimal unitQuantity,
      BigDecimal packSize,
      BigDecimal wetAmount) {
    List<CanonicalInvoiceLine> matches =
        repository.lockCurrentByInvoiceNumberAndStockCode(invoiceNumber, stockCode);
    if (matches.size() != 1) {
      return; // 0 = no CSV line (pdf-only line); >1 = ambiguous, skip rather than guess
    }
    CanonicalInvoiceLine l = matches.get(0);
    if (Objects.equals(l.uom(), uom)
        && sameAmount(l.unitQuantity(), unitQuantity)
        && sameAmount(l.packSize(), packSize)
        && sameAmount(l.wetAmount(), wetAmount)) {
      return; // already enriched
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
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }
}
