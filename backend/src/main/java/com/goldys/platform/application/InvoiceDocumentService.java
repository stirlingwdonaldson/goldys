package com.goldys.platform.application;

import com.goldys.platform.auth.*;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.InvoicePdfLinkService;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.util.*;
import org.springframework.stereotype.Service;

/** Original source documents require both financial detail and raw-source permissions. */
@Service
public class InvoiceDocumentService {
  private final InvoicePdfLinkService pdfLinks;
  private final RawLedgerQuery rawLedger;
  private final PermissionService permissions;
  private final CanonicalInvoiceQuery invoices;

  public InvoiceDocumentService(
      InvoicePdfLinkService pdfLinks,
      RawLedgerQuery rawLedger,
      PermissionService permissions,
      CanonicalInvoiceQuery invoices) {
    this.pdfLinks = pdfLinks;
    this.rawLedger = rawLedger;
    this.permissions = permissions;
    this.invoices = invoices;
  }

  private void authorize(UserRole role) {
    permissions.require(role, new ResourceKey("inventory.cost"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("connectors"), PermissionAction.READ);
  }

  public List<Document> documents(UserRole role, UUID invoiceId) {
    authorize(role);
    return pdfLinks.documents(invoiceId).stream()
        .map(d -> new Document(d.documentId(), d.filename(), d.linkedAt()))
        .toList();
  }

  public PdfDocument pdf(UserRole role, UUID invoiceId, UUID documentId) {
    authorize(role);
    return read(pdfLinks.document(invoiceId, documentId));
  }

  /** Compatibility route is available only for one qualified document on one invoice. */
  public PdfDocument pdf(UserRole role, String number) {
    authorize(role);
    UUID id =
        invoices
            .invoiceLogicalIdForInvoiceNumber(number)
            .orElseThrow(() -> new NoSuchElementException("Unknown invoice"));
    List<InvoicePdfLinkService.Document> links = pdfLinks.documents(id);
    if (links.isEmpty())
      throw new NoSuchElementException("No attributable PDF linked to this invoice");
    if (links.size() != 1)
      throw new IllegalArgumentException("Multiple documents: select a document explicitly");
    return read(links.getFirst());
  }

  private PdfDocument read(InvoicePdfLinkService.Document link) {
    byte[] bytes = rawLedger.payloadBytes(link.documentId());
    if (bytes == null) throw new NoSuchElementException("PDF bytes missing");
    return new PdfDocument(bytes, link.filename() == null ? "invoice.pdf" : link.filename());
  }

  public record Document(UUID documentId, String filename, java.time.Instant linkedAt) {}

  public record PdfDocument(byte[] bytes, String filename) {}
}
