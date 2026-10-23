package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvoicePdfLinkServiceTest {

  private static final UUID PDF = UUID.randomUUID();

  private InvoicePdfLinkRepository repository;
  private InvoicePdfLinkService service;
  private CanonicalInvoiceQuery invoices;

  @BeforeEach
  void setUp() {
    repository = mock(InvoicePdfLinkRepository.class);
    invoices = mock(CanonicalInvoiceQuery.class);
    service = new InvoicePdfLinkService(repository, invoices);
  }

  @Test
  void linkPersistsOnceAndSecondLinkIsNoOp() {
    when(repository.existsById(PDF)).thenReturn(false);
    service.link("INV-1", PDF, "inv-1.pdf", InvoicePdfLinkSource.ENRICHMENT);
    verify(repository, times(1)).save(any(InvoicePdfLink.class));

    when(repository.existsById(PDF)).thenReturn(true);
    service.link("INV-1", PDF, "inv-1.pdf", InvoicePdfLinkSource.ENRICHMENT);
    verify(repository, times(1)).save(any(InvoicePdfLink.class));
  }

  @Test
  void attributableLegacyDocumentHasTheSameAvailabilityAndRetrievalStatus() {
    UUID invoiceId = UUID.randomUUID();
    when(invoices.currentInvoice(invoiceId))
        .thenReturn(
            Optional.of(
                new InvoiceMetadataView(
                    "A",
                    "INV-1",
                    java.time.LocalDate.of(2026, 10, 1),
                    null,
                    null,
                    null,
                    "unique.pdf",
                    null,
                    null,
                    null,
                    invoiceId,
                    UUID.randomUUID())));
    when(invoices.hasUniqueCurrentPdfFilename("unique.pdf")).thenReturn(true);
    when(repository.findByInvoiceNumber("INV-1"))
        .thenReturn(List.of(link("INV-1", PDF, "unique.pdf")));
    assertThat(service.status(invoiceId)).isEqualTo("AVAILABLE");
    assertThat(service.document(invoiceId, PDF).filename()).isEqualTo("unique.pdf");
    when(invoices.hasUnqualifiedHistory("INV-1")).thenReturn(true);
    assertThat(service.status(invoiceId)).isEqualTo("UNRESOLVED");
    assertThat(service.documents(invoiceId)).isEmpty();
  }

  @Test
  void pairedDocumentsStayScopedEvenWhenNumbersCollide() {
    UUID invoiceId = UUID.randomUUID();
    when(invoices.currentInvoice(invoiceId))
        .thenReturn(
            Optional.of(
                new InvoiceMetadataView(
                    "A",
                    "INV-1",
                    java.time.LocalDate.of(2026, 10, 1),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    invoiceId,
                    UUID.randomUUID())));
    when(invoices.hasUnqualifiedHistory("INV-1")).thenReturn(true);
    when(repository.findByInvoiceLogicalIdOrderByLinkedAtAscPdfRawRecordIdAsc(invoiceId))
        .thenReturn(
            List.of(
                InvoicePdfLink.scoped(
                    invoiceId,
                    PDF,
                    "INV-1",
                    "selected.pdf",
                    InvoicePdfLinkSource.ENRICHMENT,
                    Instant.now())));
    var selected = service.document(invoiceId, PDF);
    assertThat(selected.documentId()).isEqualTo(PDF);
    assertThat(selected.filename()).isEqualTo("selected.pdf");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.document(invoiceId, UUID.randomUUID()))
        .isInstanceOf(java.util.NoSuchElementException.class);
  }

  @Test
  void isLinkedTrueAfterLinkAndFalseForUnknownId() {
    when(repository.existsById(PDF)).thenReturn(true);
    assertThat(service.isLinked(PDF)).isTrue();

    when(repository.existsById(PDF)).thenReturn(false);
    assertThat(service.isLinked(PDF)).isFalse();
  }

  @Test
  void linkedInvoiceNumbersReturnsDistinctStoredNumbers() {
    when(repository.findAllInvoiceNumbers()).thenReturn(List.of("INV-1", "INV-2", "INV-1"));
    assertThat(service.linkedInvoiceNumbers()).isEqualTo(Set.of("INV-1", "INV-2"));
  }

  private static InvoicePdfLink link(String invoiceNumber, UUID pdfRawRecordId, String filename) {
    return InvoicePdfLink.create(
        pdfRawRecordId,
        invoiceNumber,
        filename,
        InvoicePdfLinkSource.ENRICHMENT,
        Instant.parse("2026-10-12T00:00:00Z"));
  }
}
