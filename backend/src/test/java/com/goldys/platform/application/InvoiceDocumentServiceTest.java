package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.InvoicePdfLinkService;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceDocumentServiceTest {

  private final InvoicePdfLinkService pdfLinks = mock(InvoicePdfLinkService.class);
  private final RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final InvoiceDocumentService service =
      new InvoiceDocumentService(pdfLinks, rawLedger, permissions);

  private static UserRole role() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  @Test
  void pdfResolvesLinkAndReturnsBytes() {
    UUID id = UUID.randomUUID();
    byte[] bytes = new byte[] {1, 2, 3};
    when(pdfLinks.pdfRawRecordId("INV-1")).thenReturn(Optional.of(id));
    when(rawLedger.payloadBytes(id)).thenReturn(bytes);
    when(pdfLinks.pdfFilename("INV-1")).thenReturn(Optional.of("inv-1.pdf"));

    InvoiceDocumentService.PdfDocument doc = service.pdf(role(), "INV-1");

    verify(permissions).require(role(), new ResourceKey("inventory.cost"), PermissionAction.READ);
    assertThat(doc.bytes()).isEqualTo(bytes);
    assertThat(doc.filename()).isEqualTo("inv-1.pdf");
  }

  @Test
  void pdfFallsBackToInvoiceNumberFilename() {
    UUID id = UUID.randomUUID();
    when(pdfLinks.pdfRawRecordId("INV-1")).thenReturn(Optional.of(id));
    when(rawLedger.payloadBytes(id)).thenReturn(new byte[] {1});
    when(pdfLinks.pdfFilename("INV-1")).thenReturn(Optional.empty());

    InvoiceDocumentService.PdfDocument doc = service.pdf(role(), "INV-1");

    assertThat(doc.filename()).isEqualTo("INV-1.pdf");
  }

  @Test
  void pdfThrowsWhenNoLink() {
    when(pdfLinks.pdfRawRecordId("INV-1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.pdf(role(), "INV-1"))
        .isInstanceOf(java.util.NoSuchElementException.class);
  }

  @Test
  void pdfThrowsWhenPayloadBytesNull() {
    UUID id = UUID.randomUUID();
    when(pdfLinks.pdfRawRecordId("INV-1")).thenReturn(Optional.of(id));
    when(rawLedger.payloadBytes(id)).thenReturn(null);

    assertThatThrownBy(() -> service.pdf(role(), "INV-1"))
        .isInstanceOf(java.util.NoSuchElementException.class);
  }
}
