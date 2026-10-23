package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical invoice metadata, so modules outside this package can
 * resolve the CSV's PDF filename to the invoice number without touching the package-private
 * repository. Used by the PDF enrichment path to prefer the filename mapping (spec §7) over the
 * PDF's own text invoice number.
 */
@Service
public class CanonicalInvoiceQuery {
  private final CanonicalInvoiceRepository repository;
  private final CanonicalInvoiceLineRepository lineRepository;

  public CanonicalInvoiceQuery(
      CanonicalInvoiceRepository repository, CanonicalInvoiceLineRepository lineRepository) {
    this.repository = repository;
    this.lineRepository = lineRepository;
  }

  public boolean hasAmbiguousInvoiceNumber(String number) {
    return number != null
        && repository.currentLogicalIdsByInvoiceNumber(number).stream().distinct().count() > 1;
  }

  public void requireUnambiguousNumber(String number) {
    if (hasAmbiguousInvoiceNumber(number))
      throw new IllegalArgumentException(
          "Ambiguous invoice number: use supplier-qualified identity");
  }

  public Optional<BigDecimal> amountExTaxForInvoiceId(UUID id) {
    return repository.currentByLogicalId(id).map(CanonicalInvoice::amountExTax);
  }

  /**
   * True when the CTB export knows this invoice number: it has a current header row or at least one
   * current line. Lines alone count because some invoices arrive with lines but no header metadata;
   * PDF matching is against lines, so those are still matchable.
   */
  public boolean isKnownInvoice(String invoiceNumber) {
    requireUnambiguousNumber(invoiceNumber);
    if (invoiceNumber == null) {
      return false;
    }
    return repository.countCurrentByInvoiceNumber(invoiceNumber) > 0
        || lineRepository.countCurrentByInvoiceNumber(invoiceNumber) > 0;
  }

  /** The current (non-superseded) invoice number for a PDF filename, or empty when unknown. */
  public Optional<String> invoiceNumberForPdfFilename(String pdfFilename) {
    if (pdfFilename == null) {
      return Optional.empty();
    }
    return repository.currentInvoiceNumberByPdfFilename(pdfFilename);
  }

  /**
   * The current PDF filename recorded for an invoice, or empty when unknown. Used by the replay
   * path to keep flag identities stable when the PDF's own raw record no longer carries its
   * filename.
   */
  public Optional<String> currentPdfFilenameForInvoiceNumber(String invoiceNumber) {
    requireUnambiguousNumber(invoiceNumber);
    if (invoiceNumber == null) {
      return Optional.empty();
    }
    return repository.currentPdfFilenamesByInvoiceNumber(invoiceNumber).stream()
        .filter(Objects::nonNull)
        .findFirst();
  }

  /**
   * The current ex-tax header amount for an invoice, or empty when unknown. An invoice number can
   * have multiple current rows in principle, so the first non-null amount wins.
   */
  public Optional<BigDecimal> amountExTaxForInvoiceNumber(String invoiceNumber) {
    requireUnambiguousNumber(invoiceNumber);
    if (invoiceNumber == null) {
      return Optional.empty();
    }
    return repository.currentAmountsExTaxByInvoiceNumber(invoiceNumber).stream()
        .filter(Objects::nonNull)
        .findFirst();
  }

  /** The current supplier name for every current invoice, keyed by invoice number. */
  public Map<String, String> currentSupplierNames() {
    Map<String, String> out = new LinkedHashMap<>();
    for (CanonicalInvoice invoice : repository.findAllCurrent()) {
      if (out.containsKey(invoice.invoiceNumber()))
        throw new IllegalArgumentException(
            "Ambiguous invoice number: supplier-qualified reporting required");
      out.put(invoice.invoiceNumber(), invoice.supplierName());
    }
    return out;
  }

  /** The current invoice's stable logical id for an invoice number, or empty when unknown. */
  public Optional<UUID> invoiceLogicalIdForInvoiceNumber(String invoiceNumber) {
    requireUnambiguousNumber(invoiceNumber);
    if (invoiceNumber == null) {
      return Optional.empty();
    }
    return repository.currentLogicalIdsByInvoiceNumber(invoiceNumber).stream().findFirst();
  }

  /**
   * The current invoice's stable logical id for an invoice number, but only when that number maps
   * to exactly one current invoice entity. Zero current rows (an orphan line) and more than one
   * distinct current invoice (a supplier-scoped collision not yet disambiguated) both return empty,
   * so callers leave the relationship unresolved rather than guessing.
   */
  public Optional<UUID> unambiguousInvoiceLogicalIdForInvoiceNumber(String invoiceNumber) {
    if (invoiceNumber == null) {
      return Optional.empty();
    }
    List<UUID> ids =
        repository.currentLogicalIdsByInvoiceNumber(invoiceNumber).stream().distinct().toList();
    return ids.size() == 1 ? Optional.of(ids.get(0)) : Optional.empty();
  }

  /** All current invoice metadata, so modules outside this package can read invoices. */
  public List<InvoiceMetadataView> currentInvoices() {
    return repository.findAllCurrent().stream().map(CanonicalInvoiceQuery::toView).toList();
  }

  public Optional<InvoiceMetadataView> currentInvoice(UUID id) {
    return repository.currentByLogicalId(id).map(CanonicalInvoiceQuery::toView);
  }

  public boolean hasUniqueCurrentPdfFilename(String filename) {
    return filename != null && repository.currentInvoiceCountForPdfFilename(filename) == 1;
  }

  public boolean hasUnqualifiedHistory(String number) {
    return repository.allLogicalIdsByInvoiceNumber(number).stream().distinct().count() != 1;
  }

  private static InvoiceMetadataView toView(CanonicalInvoice i) {
    return new InvoiceMetadataView(
        i.supplierName(),
        i.invoiceNumber(),
        i.invoiceDate(),
        i.dueDate(),
        i.totalAmount(),
        i.purchaseNumber(),
        i.pdfFilename(),
        i.amountExTax(),
        i.gstAmount(),
        i.freightAmount(),
        i.logicalEntityId(),
        i.supplierLogicalId());
  }
}
