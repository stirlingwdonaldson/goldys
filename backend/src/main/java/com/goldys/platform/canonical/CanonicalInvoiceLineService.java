package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical invoice line items, locking the current source fact and publishing a projection
 * event so the affected date's COGS is re-projected in the same transaction.
 *
 * <p>The line's identity is content-based (its {@code sourceRecordRef} is computed by the caller
 * from the line's content, not its ordinal position), so reordered lines keep their identity and
 * re-ingest is idempotent. The {@code invoiceLogicalId} parent reference ties each line to its
 * invoice entity.
 */
@Service
class CanonicalInvoiceLineService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalInvoiceLineRepository repository;
  private final CanonicalInvoiceQuery invoiceQuery;
  private final ApplicationEventPublisher publisher;

  CanonicalInvoiceLineService(
      CanonicalInvoiceLineRepository repository,
      CanonicalInvoiceQuery invoiceQuery,
      ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.invoiceQuery = invoiceQuery;
    this.publisher = publisher;
  }

  @Transactional
  CanonicalInvoiceLine record(InvoiceLineInput input) {
    CanonicalInvoiceLine saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new InvoiceLineRecorded(input.invoiceDate()));
    return saved;
  }

  @Transactional
  CanonicalInvoiceLine recordAt(InvoiceLineInput input, Instant recordedAt) {
    UUID logicalId = logicalIdFor(input.sourceRecordRef());
    Optional<CanonicalInvoiceLine> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), input.sourceRecordRef());

    if (current.isPresent()) {
      CanonicalInvoiceLine existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalInvoiceLine.create(
            logicalId,
            input.sourceSystem(),
            input.sourceRecordRef(),
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.invoiceNumber(),
            input.invoiceLogicalId(),
            input.invoiceDate(),
            input.productNameKey(),
            input.stockCode(),
            input.quantity(),
            input.unitCost(),
            input.lineTotal(),
            input.category(),
            input.uom(),
            input.unitQuantity(),
            input.packSize(),
            input.wetAmount(),
            null, // lineTotalIncTax — CSV does not supply tax treatment
            null, // lineGstAmount
            null, // lineTaxRate
            null, // incGst
            input.origin()));
  }

  private static UUID logicalIdFor(String sourceRecordRef) {
    return UUID.nameUUIDFromBytes(
        ("invoice-line:" + sourceRecordRef).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * One-off re-key: resolves the parent invoice reference for every current line whose {@code
   * invoiceLogicalId} is still null (legacy rows ingested before the column existed). Each such
   * line is closed and re-inserted with its parent id via {@code reparented}, so the bitemporal
   * "close-and-reinsert, never UPDATE" invariant holds. Lines whose invoice number resolves to zero
   * current invoices (orphans) or to more than one distinct current invoice (ambiguous) are left
   * null — an explicit unresolved relationship, never a guess. No projection event is published:
   * re-parenting changes only the structural join key, never a fact, so COGS is unaffected.
   *
   * @return the number of lines re-keyed.
   */
  @Transactional
  int backfillInvoiceLogicalId() {
    Instant now = CLOCK.instant();
    List<CanonicalInvoiceLine> lines = repository.findCurrentWithNullInvoiceLogicalId();
    int migrated = 0;
    for (CanonicalInvoiceLine line : lines) {
      Optional<UUID> parent =
          invoiceQuery.unambiguousInvoiceLogicalIdForInvoiceNumber(line.invoiceNumber());
      if (parent.isEmpty()) {
        continue;
      }
      line.supersede(now);
      repository.saveAndFlush(line);
      repository.save(line.reparented(parent.get(), now));
      migrated++;
    }
    return migrated;
  }

  /**
   * Closes every current CSV-sourced line of one invoice whose {@code sourceRecordRef} is not in
   * {@code currentSourceRefs} (a line the re-ingested export no longer carries). PDF-derived lines
   * are untouched — they are managed by the gap-recovery convergence path. Publishes one
   * re-projection event per affected invoice date and returns the number of lines closed.
   */
  @Transactional
  int convergeCsvLines(UUID invoiceLogicalId, Set<String> currentSourceRefs) {
    Instant now = CLOCK.instant();
    List<CanonicalInvoiceLine> csvLines =
        repository.findCurrentCsvByInvoiceLogicalId(invoiceLogicalId);
    Set<LocalDate> affectedDates = new HashSet<>();
    int superseded = 0;
    for (CanonicalInvoiceLine line : csvLines) {
      if (!currentSourceRefs.contains(line.sourceRecordRef())) {
        line.supersede(now);
        repository.saveAndFlush(line);
        affectedDates.add(line.invoiceDate());
        superseded++;
      }
    }
    publishProjectionEvents(affectedDates);
    return superseded;
  }

  /**
   * Closes every current PDF-derived line of one invoice (bitemporal supersede, never delete) and
   * publishes one re-projection event per affected invoice date, so the resolved COGS for those
   * dates no longer counts the PDF gap lines. Returns the number of lines closed.
   */
  @Transactional
  int supersedePdfOriginLines(String invoiceNumber) {
    Instant now = CLOCK.instant();
    List<CanonicalInvoiceLine> pdfLines =
        repository.findCurrentPdfOriginByInvoiceNumber(invoiceNumber);
    Set<LocalDate> affectedDates = new HashSet<>();
    for (CanonicalInvoiceLine line : pdfLines) {
      line.supersede(now);
      repository.saveAndFlush(line);
      affectedDates.add(line.invoiceDate());
    }
    publishProjectionEvents(affectedDates);
    return pdfLines.size();
  }

  /**
   * Prunes the current PDF-derived lines of one invoice down to the amount the CSV still leaves
   * uncovered: keeps lines in a deterministic order (recorded-at, then id) while their running
   * {@code lineTotal} stays within {@code allowedSum + tolerance}, superseding the rest. This is
   * the partial-catch-up path — when the CSV export is only partly fixed, the still-stale PDF gap
   * lines must stop over-counting COGS without removing the ones that still cover the remaining
   * shortfall.
   *
   * <p>Returns the number of lines superseded (0 when the CSV shortfall already admits every PDF
   * line). Publishes one re-projection event per affected invoice date.
   */
  @Transactional
  int prunePdfOriginLines(String invoiceNumber, BigDecimal allowedSum, BigDecimal tolerance) {
    Instant now = CLOCK.instant();
    List<CanonicalInvoiceLine> pdfLines =
        repository.findCurrentPdfOriginByInvoiceNumber(invoiceNumber);
    pdfLines.sort(
        Comparator.comparing(CanonicalInvoiceLine::recordedAt)
            .thenComparing(CanonicalInvoiceLine::id));
    BigDecimal running = BigDecimal.ZERO;
    Set<LocalDate> affectedDates = new HashSet<>();
    int superseded = 0;
    for (CanonicalInvoiceLine line : pdfLines) {
      BigDecimal lineTotal = line.lineTotal() == null ? BigDecimal.ZERO : line.lineTotal();
      if (running.add(lineTotal).compareTo(allowedSum.add(tolerance)) <= 0) {
        running = running.add(lineTotal);
      } else {
        line.supersede(now);
        repository.saveAndFlush(line);
        affectedDates.add(line.invoiceDate());
        superseded++;
      }
    }
    publishProjectionEvents(affectedDates);
    return superseded;
  }

  private void publishProjectionEvents(Set<LocalDate> affectedDates) {
    for (LocalDate date : affectedDates) {
      publisher.publishEvent(new InvoiceLineRecorded(date));
    }
  }
}
