package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical invoice metadata, locking the current source fact for concurrent corrections.
 */
@Service
class CanonicalInvoiceService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalInvoiceRepository repository;

  CanonicalInvoiceService(CanonicalInvoiceRepository repository) {
    this.repository = repository;
  }

  @Transactional
  CanonicalInvoice record(InvoiceInput input) {
    return recordAt(input, CLOCK.instant());
  }

  @Transactional
  CanonicalInvoice recordAt(InvoiceInput input, Instant recordedAt) {
    UUID logicalId = logicalIdFor(input.invoiceNumber());
    Optional<CanonicalInvoice> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), input.invoiceNumber());

    if (current.isPresent()) {
      CanonicalInvoice existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalInvoice.create(
            logicalId,
            input.sourceSystem(),
            input.invoiceNumber(),
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.supplierName(),
            input.invoiceNumber(),
            input.invoiceDate(),
            input.dueDate(),
            input.totalAmount()));
  }

  private static UUID logicalIdFor(String invoiceNumber) {
    return UUID.nameUUIDFromBytes(("invoice:" + invoiceNumber).getBytes(StandardCharsets.UTF_8));
  }
}
