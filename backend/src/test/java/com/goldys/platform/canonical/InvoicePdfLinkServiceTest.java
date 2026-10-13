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

  @BeforeEach
  void setUp() {
    repository = mock(InvoicePdfLinkRepository.class);
    service = new InvoicePdfLinkService(repository);
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
  void pdfRawRecordIdReturnsIdAfterLinkAndEmptyBefore() {
    when(repository.findFirstByInvoiceNumber("INV-1")).thenReturn(Optional.empty());
    assertThat(service.pdfRawRecordId("INV-1")).isEmpty();

    when(repository.findFirstByInvoiceNumber("INV-1"))
        .thenReturn(Optional.of(link("INV-1", PDF, "inv-1.pdf")));
    assertThat(service.pdfRawRecordId("INV-1")).contains(PDF);
  }

  @Test
  void pdfFilenameReturnsFilenameAfterLinkAndEmptyBefore() {
    when(repository.findFirstByInvoiceNumber("INV-1")).thenReturn(Optional.empty());
    assertThat(service.pdfFilename("INV-1")).isEmpty();

    when(repository.findFirstByInvoiceNumber("INV-1"))
        .thenReturn(Optional.of(link("INV-1", PDF, "inv-1.pdf")));
    assertThat(service.pdfFilename("INV-1")).contains("inv-1.pdf");
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
