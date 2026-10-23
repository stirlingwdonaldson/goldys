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

  private final CanonicalInvoiceQuery invoices;

  public InvoicePdfLinkService(
      InvoicePdfLinkRepository repository, CanonicalInvoiceQuery invoices) {
    this.repository = repository;
    this.invoices = invoices;
  }

  /**
   * Records that {@code pdfRawRecordId} is the PDF document for {@code invoiceNumber}. Idempotent:
   * a second link for the same raw record is a no-op, so a replay never piles up duplicate rows.
   */
  @Transactional
  public void link(
      String invoiceNumber, UUID pdfRawRecordId, String pdfFilename, InvoicePdfLinkSource source) {
    invoices.requireUnambiguousNumber(invoiceNumber);
    if (repository.existsById(pdfRawRecordId)) {
      return;
    }
    Optional<UUID> parent = invoices.unambiguousInvoiceLogicalIdForInvoiceNumber(invoiceNumber);
    InvoicePdfLink link =
        parent.isPresent() && !invoices.hasUnqualifiedHistory(invoiceNumber)
            ? InvoicePdfLink.scoped(
                parent.get(), pdfRawRecordId, invoiceNumber, pdfFilename, source, CLOCK.instant())
            : InvoicePdfLink.create(
                pdfRawRecordId, invoiceNumber, pdfFilename, source, CLOCK.instant());
    repository.save(link);
  }

  /** True when {@code pdfRawRecordId} is already recorded as an invoice's PDF. */
  public boolean isLinked(UUID pdfRawRecordId) {
    return repository.existsById(pdfRawRecordId);
  }

  public record Document(java.util.UUID documentId, String filename, java.time.Instant linkedAt) {}

  /** Scoped evidence, plus legacy links only when identity history and filename agree uniquely. */
  public java.util.List<Document> documents(UUID invoiceId) {
    InvoiceMetadataView header =
        invoices
            .currentInvoice(invoiceId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Unknown invoice"));
    java.util.Map<UUID, InvoicePdfLink> links = new java.util.LinkedHashMap<>();
    repository
        .findByInvoiceLogicalIdOrderByLinkedAtAscPdfRawRecordIdAsc(invoiceId)
        .forEach(link -> links.put(link.pdfRawRecordId(), link));
    if (!invoices.hasUnqualifiedHistory(header.invoiceNumber()) && header.pdfFilename() != null) {
      if (invoices.hasUniqueCurrentPdfFilename(header.pdfFilename()))
        repository.findByInvoiceNumber(header.invoiceNumber()).stream()
            .filter(
                link ->
                    link.invoiceLogicalId() == null
                        && header.pdfFilename().equals(link.pdfFilename()))
            .forEach(link -> links.put(link.pdfRawRecordId(), link));
    }
    return links.values().stream()
        .sorted(
            java.util.Comparator.comparing(InvoicePdfLink::linkedAt)
                .thenComparing(InvoicePdfLink::pdfRawRecordId))
        .map(link -> new Document(link.pdfRawRecordId(), link.pdfFilename(), link.linkedAt()))
        .toList();
  }

  public String status(UUID invoiceId) {
    if (!documents(invoiceId).isEmpty()) return "AVAILABLE";
    InvoiceMetadataView header = invoices.currentInvoice(invoiceId).orElseThrow();
    return repository.findByInvoiceNumber(header.invoiceNumber()).isEmpty()
        ? "NOT_LINKED"
        : "UNRESOLVED";
  }

  public Document document(UUID invoiceId, UUID documentId) {
    return documents(invoiceId).stream()
        .filter(d -> d.documentId().equals(documentId))
        .findFirst()
        .orElseThrow(
            () -> new java.util.NoSuchElementException("Document is not linked to this invoice"));
  }

  /** The distinct invoice numbers that currently have a PDF link recorded. */
  public Set<String> linkedInvoiceNumbers() {
    Set<String> numbers = new HashSet<>(repository.findAllInvoiceNumbers());
    numbers.forEach(invoices::requireUnambiguousNumber);
    return numbers;
  }
}
