package com.goldys.platform.canonical;

import java.time.Clock;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records which raw record is the PDF for a canonical invoice, and reads that link back. The link
 * is plain evidence, not a fact: writing it must never supersede the invoice, so this service only
 * ever inserts, keyed by the PDF raw record id.
 */
@Service
public class InvoicePdfLinkService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final InvoicePdfLinkRepository repository;

  public InvoicePdfLinkService(InvoicePdfLinkRepository repository) {
    this.repository = repository;
  }

  /**
   * Records that {@code pdfRawRecordId} is the PDF document for {@code invoiceNumber}. Idempotent:
   * a second link for the same raw record is a no-op, so a replay never piles up duplicate rows.
   */
  @Transactional
  public void link(
      String invoiceNumber, UUID pdfRawRecordId, String pdfFilename, InvoicePdfLinkSource source) {
    if (repository.existsById(pdfRawRecordId)) {
      return;
    }
    repository.save(
        InvoicePdfLink.create(pdfRawRecordId, invoiceNumber, pdfFilename, source, CLOCK.instant()));
  }

  /** The PDF raw record id linked to {@code invoiceNumber}, or empty when none is recorded. */
  public Optional<UUID> pdfRawRecordId(String invoiceNumber) {
    return repository.findFirstByInvoiceNumber(invoiceNumber).map(InvoicePdfLink::pdfRawRecordId);
  }

  /** The PDF filename linked to {@code invoiceNumber}, or empty when none is recorded. */
  public Optional<String> pdfFilename(String invoiceNumber) {
    return repository.findFirstByInvoiceNumber(invoiceNumber).map(InvoicePdfLink::pdfFilename);
  }

  /** True when {@code pdfRawRecordId} is already recorded as an invoice's PDF. */
  public boolean isLinked(UUID pdfRawRecordId) {
    return repository.existsById(pdfRawRecordId);
  }

  /** The distinct invoice numbers that currently have a PDF link recorded. */
  public Set<String> linkedInvoiceNumbers() {
    return new HashSet<>(repository.findAllInvoiceNumbers());
  }
}
