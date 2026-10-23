package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.canonical.*;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.util.*;
import org.junit.jupiter.api.Test;

class InvoiceDocumentServiceTest {
  final InvoicePdfLinkService links = mock(InvoicePdfLinkService.class);
  final RawLedgerQuery raw = mock(RawLedgerQuery.class);
  final PermissionService permissions = mock(PermissionService.class);
  final CanonicalInvoiceQuery invoices = mock(CanonicalInvoiceQuery.class);
  final InvoiceDocumentService service =
      new InvoiceDocumentService(links, raw, permissions, invoices);
  final UserRole role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  final UUID invoice = UUID.randomUUID(), doc = UUID.randomUUID();
  final InvoicePdfLinkService.Document link =
      new InvoicePdfLinkService.Document(doc, "right.pdf", java.time.Instant.now());

  @Test
  void pairsFilenameWithTheSelectedScopedBytes() {
    when(links.document(invoice, doc)).thenReturn(link);
    when(raw.payloadBytes(doc)).thenReturn(new byte[] {1, 2});
    var result = service.pdf(role, invoice, doc);
    assertThat(result.filename()).isEqualTo("right.pdf");
    assertThat(result.bytes()).containsExactly(1, 2);
    verify(permissions).require(role, new ResourceKey("inventory.cost"), PermissionAction.READ);
    verify(permissions).require(role, new ResourceKey("connectors"), PermissionAction.READ);
  }

  @Test
  void costOnlyGrantCannotReadOrListRawDocuments() {
    doThrow(new AccessDeniedException("connectors denied"))
        .when(permissions)
        .require(role, new ResourceKey("connectors"), PermissionAction.READ);
    assertThatThrownBy(() -> service.pdf(role, invoice, doc))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.documents(role, invoice))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(raw, links, invoices);
  }

  @Test
  void invoicePermissionIsRequiredBeforeSourceLookup() {
    doThrow(new AccessDeniedException("cost denied"))
        .when(permissions)
        .require(role, new ResourceKey("inventory.cost"), PermissionAction.READ);
    assertThatThrownBy(() -> service.pdf(role, invoice, doc))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(raw, links, invoices);
  }

  @Test
  void legacyNumberDoesNotChooseBetweenMultipleDocuments() {
    when(invoices.invoiceLogicalIdForInvoiceNumber("N")).thenReturn(Optional.of(invoice));
    when(links.documents(invoice))
        .thenReturn(
            List.of(
                link,
                new InvoicePdfLinkService.Document(
                    UUID.randomUUID(), "other.pdf", java.time.Instant.now())));
    assertThatThrownBy(() -> service.pdf(role, "N")).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(raw);
  }

  @Test
  void unavailableBytesAreNotAnEmptyPdf() {
    when(links.document(invoice, doc)).thenReturn(link);
    assertThatThrownBy(() -> service.pdf(role, invoice, doc))
        .isInstanceOf(NoSuchElementException.class);
  }
}
