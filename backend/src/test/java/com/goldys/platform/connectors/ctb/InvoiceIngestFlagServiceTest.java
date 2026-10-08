package com.goldys.platform.connectors.ctb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class InvoiceIngestFlagServiceTest {

  @Test
  void recordsAFlagWhenNoEquivalentExists() {
    InvoiceIngestFlagRepository repository = mock(InvoiceIngestFlagRepository.class);
    when(repository.existsEquivalent(any(), any(), any(), any())).thenReturn(false);

    new InvoiceIngestFlagService(repository)
        .flag(InvoiceIngestFlagType.MISSING_PDF, "INV-1", "a.pdf", null, "missing");

    verify(repository).save(any(InvoiceIngestFlag.class));
  }

  @Test
  void skipsAnAlreadyRecordedFlag() {
    InvoiceIngestFlagRepository repository = mock(InvoiceIngestFlagRepository.class);
    when(repository.existsEquivalent(any(), any(), any(), any())).thenReturn(true);

    new InvoiceIngestFlagService(repository)
        .flag(InvoiceIngestFlagType.MISSING_PDF, "INV-1", "a.pdf", null, "missing");

    verify(repository, never()).save(any(InvoiceIngestFlag.class));
  }
}
