package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.InvoicePdfLinkService;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves an invoice's PDF bytes from the raw ledger via its recorded link. Gated on the same
 * {@code inventory.cost} READ permission as the rest of the inventory surface; unknown invoices,
 * missing links and missing bytes all surface as {@code NOT_FOUND} via {@link
 * NoSuchElementException}.
 */
@Service
public class InvoiceDocumentService {

  private static final ResourceKey RESOURCE = new ResourceKey("inventory.cost");

  private final InvoicePdfLinkService pdfLinks;
  private final RawLedgerQuery rawLedger;
  private final PermissionService permissions;

  public InvoiceDocumentService(
      InvoicePdfLinkService pdfLinks, RawLedgerQuery rawLedger, PermissionService permissions) {
    this.pdfLinks = pdfLinks;
    this.rawLedger = rawLedger;
    this.permissions = permissions;
  }

  /** The linked PDF document for {@code invoiceNumber}, or {@code NOT_FOUND}. */
  public PdfDocument pdf(UserRole role, String invoiceNumber) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    UUID rawRecordId =
        pdfLinks
            .pdfRawRecordId(invoiceNumber)
            .orElseThrow(
                () -> new NoSuchElementException("No PDF linked for invoice: " + invoiceNumber));
    byte[] bytes = rawLedger.payloadBytes(rawRecordId);
    if (bytes == null) {
      throw new NoSuchElementException("PDF bytes missing for invoice: " + invoiceNumber);
    }
    String filename = pdfLinks.pdfFilename(invoiceNumber).orElse(invoiceNumber + ".pdf");
    return new PdfDocument(bytes, filename);
  }

  /** The PDF bytes and the filename to present in the download header. */
  public record PdfDocument(byte[] bytes, String filename) {}
}
