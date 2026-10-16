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
 *
 * <p>The identity is supplier-scoped: the invoice's {@code sourceRecordRef} is {@code
 * "<supplierKey>::<invoiceNumber>"}, so an invoice number is not treated as globally unique. The
 * supplier is resolved through {@link CanonicalSupplierService}, which keys on the source's own
 * supplier code and never merges on name alone.
 */
@Service
class CanonicalInvoiceService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalInvoiceRepository repository;
  private final CanonicalSupplierService suppliers;

  CanonicalInvoiceService(
      CanonicalInvoiceRepository repository, CanonicalSupplierService suppliers) {
    this.repository = repository;
    this.suppliers = suppliers;
  }

  @Transactional
  InvoiceIdentity record(InvoiceInput input) {
    return recordAt(input, CLOCK.instant());
  }

  @Transactional
  InvoiceIdentity recordAt(InvoiceInput input, Instant recordedAt) {
    String sourceSystem = input.sourceSystem();
    SupplierIdentity supplier =
        suppliers.recordAt(
            new SupplierInput(
                sourceSystem, input.supplierCode(), input.supplierName(), input.rawRecordId()),
            recordedAt);

    String scopedRef = supplier.sourceRecordRef() + "::" + input.invoiceNumber();
    UUID logicalId =
        UUID.nameUUIDFromBytes(("invoice:" + scopedRef).getBytes(StandardCharsets.UTF_8));

    // Transition: supersede any legacy current row keyed on the bare invoice number that represents
    // the same supplier + number (rows recorded before supplier-scoped identity existed).
    Optional<CanonicalInvoice> current = repository.lockCurrentSourceFact(sourceSystem, scopedRef);
    Optional<CanonicalInvoice> legacy =
        repository.lockCurrentSourceFact(sourceSystem, input.invoiceNumber());
    if (legacy.isPresent()
        && java.util.Objects.equals(legacy.get().supplierName(), input.supplierName())) {
      if (current.isPresent()
          && !current.get().logicalEntityId().equals(legacy.get().logicalEntityId())) {
        throw new IllegalArgumentException(
            "Conflicting legacy and scoped invoice identities require review");
      }
      // Keep the enduring entity ID so already-backfilled children do not become orphans.
      logicalId = legacy.get().logicalEntityId();
      legacy.get().supersede(recordedAt);
      repository.saveAndFlush(legacy.get());
    }

    if (current.isPresent()) {
      CanonicalInvoice existing = current.get();
      if (existing.sameFact(input)) {
        return new InvoiceIdentity(
            existing.logicalEntityId(),
            scopedRef,
            supplier.logicalEntityId(),
            supplier.sourceRecordRef());
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    repository.save(
        CanonicalInvoice.create(
            logicalId,
            sourceSystem,
            scopedRef,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            supplier.logicalEntityId(),
            supplier.sourceRecordRef(),
            input.supplierName(),
            input.invoiceNumber(),
            input.invoiceDate(),
            input.dueDate(),
            input.totalAmount(),
            input.purchaseNumber(),
            input.pdfFilename(),
            input.accountNumber(),
            input.taxCode(),
            input.amountExTax(),
            input.gstAmount(),
            input.freightAmount(),
            input.freightGstAmount()));
    return new InvoiceIdentity(
        logicalId, scopedRef, supplier.logicalEntityId(), supplier.sourceRecordRef());
  }
}
