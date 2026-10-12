package com.goldys.platform.connectors.ctb;

import com.goldys.platform.semantic.InvoiceIngestFlagView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records invoice-ingestion flags append-only. Recording is idempotent on the flag's identity (type
 * + invoice number + PDF filename + detail/description), so a re-pull of the same drop does not
 * pile up duplicate rows while every distinct anomaly remains a queryable record. The stock code is
 * deliberately <em>not</em> part of the identity: the PDF extractor produces an unstable code
 * across runs, so keying on it re-flagged the same line repeatedly.
 */
@Service
public class InvoiceIngestFlagService {

  private static final Clock CLOCK = Clock.systemUTC();

  private final InvoiceIngestFlagRepository repository;

  public InvoiceIngestFlagService(InvoiceIngestFlagRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void flag(
      InvoiceIngestFlagType type,
      String invoiceNumber,
      String pdfFilename,
      String stockCode,
      String detail) {
    if (repository.existsEquivalent(type, invoiceNumber, pdfFilename, detail)) {
      return;
    }
    repository.save(
        InvoiceIngestFlag.create(
            type, invoiceNumber, pdfFilename, stockCode, detail, Instant.now(CLOCK)));
  }

  /**
   * Retires duplicate open {@code PDF_ONLY_LINE} flags left by the pre-fix stock-code identity —
   * the same line flagged more than once under different stock codes. Keeps the most recent flag
   * per (invoice number, PDF filename, detail) group and resolves the rest. Idempotent: a second
   * run finds no open duplicates.
   *
   * @return how many duplicate flags were retired
   */
  @Transactional
  int retireDuplicatePdfOnlyLineFlags() {
    List<InvoiceIngestFlag> open =
        repository.findByFlagTypeAndResolvedAtIsNull(InvoiceIngestFlagType.PDF_ONLY_LINE);
    Map<LineKey, List<InvoiceIngestFlag>> groups = new HashMap<>();
    for (InvoiceIngestFlag flag : open) {
      groups
          .computeIfAbsent(
              new LineKey(flag.invoiceNumber(), flag.pdfFilename(), flag.detail()),
              k -> new ArrayList<>())
          .add(flag);
    }
    int retired = 0;
    for (List<InvoiceIngestFlag> group : groups.values()) {
      if (group.size() <= 1) {
        continue;
      }
      group.sort(Comparator.comparing(InvoiceIngestFlag::occurredAt).reversed());
      for (InvoiceIngestFlag flag : group.subList(1, group.size())) {
        flag.resolve(Instant.now(CLOCK));
        repository.save(flag);
        retired++;
      }
    }
    return retired;
  }

  /** Every recorded flag, most recent first, as a read-only semantic view (including resolved). */
  public List<InvoiceIngestFlagView> listFlags() {
    return repository.findAllByOrderByOccurredAtDesc().stream()
        .map(InvoiceIngestFlagService::toView)
        .toList();
  }

  /** Open (unresolved) flags, most recent first — what the work queue should show. */
  public List<InvoiceIngestFlagView> listOpenFlags() {
    return repository.findByResolvedAtIsNullOrderByOccurredAtDesc().stream()
        .map(InvoiceIngestFlagService::toView)
        .toList();
  }

  /** The open {@code PDF_ONLY_LINE} flags, reduced to the keys a replay needs to re-test them. */
  public List<OpenFlag> openPdfOnlyLineFlags() {
    return repository
        .findByFlagTypeAndResolvedAtIsNull(InvoiceIngestFlagType.PDF_ONLY_LINE)
        .stream()
        .map(f -> new OpenFlag(f.id(), f.invoiceNumber(), f.stockCode(), f.detail()))
        .toList();
  }

  /** Retires a flag once a better matcher/replay resolves it. Idempotent. */
  @Transactional
  public void resolve(UUID id) {
    repository
        .findById(id)
        .ifPresent(
            f -> {
              f.resolve(Instant.now(CLOCK));
              repository.save(f);
            });
  }

  private static InvoiceIngestFlagView toView(InvoiceIngestFlag f) {
    return new InvoiceIngestFlagView(
        f.flagType().name(),
        f.invoiceNumber(),
        f.pdfFilename(),
        f.stockCode(),
        f.detail(),
        f.occurredAt());
  }

  /** The identity of an open flag, for the reconciliation pass. */
  public record OpenFlag(UUID id, String invoiceNumber, String stockCode, String detail) {}

  /** The content-stable identity of a flagged line, used to detect duplicates. */
  private record LineKey(String invoiceNumber, String pdfFilename, String detail) {}
}
