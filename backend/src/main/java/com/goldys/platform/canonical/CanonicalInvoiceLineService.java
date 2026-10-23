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
    LocalDate previousDate =
        repository
            .lockCurrentSourceFact(input.sourceSystem(), input.sourceRecordRef())
            .map(CanonicalInvoiceLine::invoiceDate)
            .orElse(input.invoiceDate());
    CanonicalInvoiceLine saved = recordAt(input, CLOCK.instant());
    if (!previousDate.equals(input.invoiceDate())) {
      publisher.publishEvent(new InvoiceLineRecorded(previousDate));
    }
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

  /** Unknown export scope allows replay/subsets, never deletion or an inferred correction. */
  @Transactional
  void recordCsvInvoice(
      InvoiceIdentity invoice,
      String number,
      List<InvoiceLineInput> inputs,
      boolean completeSnapshot) {
    List<CanonicalInvoiceLine> existing =
        new java.util.ArrayList<>(
            repository.findCurrentCsvByInvoiceLogicalId(invoice.logicalEntityId()));
    List<CanonicalInvoiceLine> unparented =
        repository.findCurrentCsvByInvoiceNumber(number).stream()
            .filter(line -> line.invoiceLogicalId() == null)
            .toList();
    if (!unparented.isEmpty()) {
      UUID parent =
          invoiceQuery
              .unambiguousInvoiceLogicalIdForInvoiceNumber(number)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Ambiguous legacy invoice lines; supplier relationship requires review"));
      if (!parent.equals(invoice.logicalEntityId()))
        throw new IllegalArgumentException("Ambiguous invoice parent");
      existing.addAll(unparented);
    }
    List<CanonicalInvoiceLine> legacyPdf =
        repository.findCurrentPdfOriginByInvoiceNumber(number).stream()
            .filter(line -> line.invoiceLogicalId() == null)
            .toList();
    if (!legacyPdf.isEmpty()) {
      invoiceQuery.requireUnambiguousNumber(number);
      for (CanonicalInvoiceLine line : legacyPdf) {
        Instant now = CLOCK.instant();
        line.supersede(now);
        repository.saveAndFlush(line);
        repository.save(line.reparented(invoice.logicalEntityId(), now));
      }
    }
    List<CanonicalInvoiceLine> remaining = new java.util.ArrayList<>(existing);
    java.util.Map<InvoiceLineInput, CanonicalInvoiceLine> matches = new java.util.LinkedHashMap<>();
    for (InvoiceLineInput input : inputs) {
      CanonicalInvoiceLine match =
          remaining.stream()
              .filter(
                  line ->
                      line.sourceRecordRef().equals(input.sourceRecordRef())
                          && line.sameCsvContent(input))
              .findFirst()
              .orElseGet(
                  () ->
                      remaining.stream()
                          .filter(line -> line.sameCsvContent(input))
                          .findFirst()
                          .orElse(null));
      if (match == null && !existing.isEmpty() && !completeSnapshot) {
        throw new IllegalArgumentException(
            "Invoice export revision withheld: complete-versus-partial semantics require confirmation");
      }
      if (match != null) {
        matches.put(input, match);
        remaining.remove(match);
      }
    }
    for (InvoiceLineInput input : inputs) {
      CanonicalInvoiceLine match = matches.get(input);
      if (match != null) {
        if (match.sourceRecordRef().equals(input.sourceRecordRef())
            && match.invoiceDate().equals(input.invoiceDate())
            && java.util.Objects.equals(match.invoiceLogicalId(), input.invoiceLogicalId()))
          continue;
        Instant now = CLOCK.instant();
        match.supersede(now);
        repository.saveAndFlush(match);
        repository.save(match.rekeyed(input, now));
        publishProjectionEvents(new HashSet<>(List.of(match.invoiceDate(), input.invoiceDate())));
      } else record(input);
    }
    if (completeSnapshot) {
      Set<LocalDate> dates = new HashSet<>();
      for (CanonicalInvoiceLine line : remaining) {
        line.supersede(CLOCK.instant());
        repository.saveAndFlush(line);
        dates.add(line.invoiceDate());
      }
      publishProjectionEvents(dates);
    }
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
    invoiceQuery.requireUnambiguousNumber(invoiceNumber);
    return closePdfLines(repository.findCurrentPdfOriginByInvoiceNumber(invoiceNumber));
  }

  @Transactional
  int supersedePdfOriginLines(UUID invoiceId) {
    return closePdfLines(
        repository.findCurrentByInvoiceId(invoiceId).stream()
            .filter(line -> "PDF".equals(line.origin()))
            .toList());
  }

  private int closePdfLines(List<CanonicalInvoiceLine> pdfLines) {
    Instant now = CLOCK.instant();
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
    invoiceQuery.requireUnambiguousNumber(invoiceNumber);
    return prunePdfLines(
        repository.findCurrentPdfOriginByInvoiceNumber(invoiceNumber), allowedSum, tolerance);
  }

  @Transactional
  int prunePdfOriginLines(UUID invoiceId, BigDecimal allowedSum, BigDecimal tolerance) {
    return prunePdfLines(
        new java.util.ArrayList<>(
            repository.findCurrentByInvoiceId(invoiceId).stream()
                .filter(line -> "PDF".equals(line.origin()))
                .toList()),
        allowedSum,
        tolerance);
  }

  private int prunePdfLines(
      List<CanonicalInvoiceLine> pdfLines, BigDecimal allowedSum, BigDecimal tolerance) {
    Instant now = CLOCK.instant();
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
