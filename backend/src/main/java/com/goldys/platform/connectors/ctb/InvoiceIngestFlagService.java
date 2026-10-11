package com.goldys.platform.connectors.ctb;

import com.goldys.platform.semantic.InvoiceIngestFlagView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records invoice-ingestion flags append-only. Recording is idempotent on the flag's identity (type
 * + invoice number + PDF filename + stock code), so a re-pull of the same drop does not pile up
 * duplicate rows while every distinct anomaly remains a queryable record.
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
    if (repository.existsEquivalent(type, invoiceNumber, pdfFilename, stockCode)) {
      return;
    }
    repository.save(
        InvoiceIngestFlag.create(
            type, invoiceNumber, pdfFilename, stockCode, detail, Instant.now(CLOCK)));
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
}
