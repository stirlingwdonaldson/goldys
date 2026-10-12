package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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

  @Test
  void dedupesOnDescriptionNotStockCode() {
    InvoiceIngestFlagRepository repository = mock(InvoiceIngestFlagRepository.class);
    when(repository.existsEquivalent(any(), any(), any(), any())).thenReturn(false);

    new InvoiceIngestFlagService(repository)
        .flag(
            InvoiceIngestFlagType.PDF_ONLY_LINE, "INV-1", "a.pdf", "STK-1", "Fuel Levy (GST INC)");

    // The identity key is the stable description, not the volatile stock code.
    verify(repository)
        .existsEquivalent(
            InvoiceIngestFlagType.PDF_ONLY_LINE, "INV-1", "a.pdf", "Fuel Levy (GST INC)");
  }

  @Test
  void retiresDuplicateFlagsKeepingOnePerLine() {
    InvoiceIngestFlagRepository repository = mock(InvoiceIngestFlagRepository.class);
    Instant t1 = Instant.parse("2026-10-13T00:00:00Z");
    Instant t2 = Instant.parse("2026-10-13T00:01:00Z");
    InvoiceIngestFlag older =
        InvoiceIngestFlag.create(
            InvoiceIngestFlagType.PDF_ONLY_LINE,
            "INV-1",
            "a.pdf",
            "FUEL",
            "Fuel Levy (GST INC)",
            t1);
    InvoiceIngestFlag newer =
        InvoiceIngestFlag.create(
            InvoiceIngestFlagType.PDF_ONLY_LINE,
            "INV-1",
            "a.pdf",
            "FUEL LEVY",
            "Fuel Levy (GST INC)",
            t2);
    InvoiceIngestFlag distinct =
        InvoiceIngestFlag.create(
            InvoiceIngestFlagType.PDF_ONLY_LINE, "INV-1", "a.pdf", "STK-7", "Some other line", t1);
    when(repository.findByFlagTypeAndResolvedAtIsNull(InvoiceIngestFlagType.PDF_ONLY_LINE))
        .thenReturn(List.of(older, newer, distinct));

    int retired = new InvoiceIngestFlagService(repository).retireDuplicatePdfOnlyLineFlags();

    assertThat(retired).isEqualTo(1);
    verify(repository).save(older); // the older duplicate is retired
    verify(repository, never()).save(newer); // the most recent is kept
    verify(repository, never()).save(distinct); // the distinct line is untouched
  }
}
