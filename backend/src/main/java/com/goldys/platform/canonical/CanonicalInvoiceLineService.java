package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical invoice line items, locking the current source fact and publishing a projection
 * event so the affected date's COGS is re-projected in the same transaction.
 */
@Service
class CanonicalInvoiceLineService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalInvoiceLineRepository repository;
  private final ApplicationEventPublisher publisher;

  CanonicalInvoiceLineService(
      CanonicalInvoiceLineRepository repository, ApplicationEventPublisher publisher) {
    this.repository = repository;
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
    UUID logicalId = logicalIdFor(input.invoiceNumber(), input.sourceRecordRef());
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
            input.wetAmount()));
  }

  private static UUID logicalIdFor(String invoiceNumber, String sourceRecordRef) {
    return UUID.nameUUIDFromBytes(
        ("invoice-line:" + invoiceNumber + ":" + sourceRecordRef).getBytes(StandardCharsets.UTF_8));
  }
}
