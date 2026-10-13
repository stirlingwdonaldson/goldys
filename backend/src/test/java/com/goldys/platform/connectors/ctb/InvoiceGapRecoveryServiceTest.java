package com.goldys.platform.connectors.ctb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.canonical.InvoiceMetadataView;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceGapRecoveryServiceTest {

  private final CanonicalInvoiceQuery invoiceQuery = mock(CanonicalInvoiceQuery.class);
  private final CanonicalInventoryQuery inventoryQuery = mock(CanonicalInventoryQuery.class);
  private final CanonicalInvoiceLineIngest lineIngest = mock(CanonicalInvoiceLineIngest.class);
  private final InvoiceIngestFlagService flags = mock(InvoiceIngestFlagService.class);

  private final UUID rawId = UUID.randomUUID();
  private final LocalDate date = LocalDate.of(2026, 10, 1);
  private final byte[] pdf = "pdf-bytes".getBytes(StandardCharsets.UTF_8);

  private InvoiceGapRecoveryService service() {
    return new InvoiceGapRecoveryService(invoiceQuery, inventoryQuery, lineIngest, flags);
  }

  private void header(String amount) {
    when(invoiceQuery.amountExTaxForInvoiceNumber("INV-7200"))
        .thenReturn(Optional.of(new BigDecimal(amount)));
  }

  private void invoiceWithDate() {
    when(invoiceQuery.currentInvoices())
        .thenReturn(
            List.of(
                new InvoiceMetadataView("Supplier", "INV-7200", date, null, null, null, "a.pdf")));
  }

  @Test
  void createsPdfLinesWhenPdfReconcilesAndCsvIsShort() {
    header("100.00");
    when(inventoryQuery.currentCsvLineTotalForInvoice("INV-7200"))
        .thenReturn(new BigDecimal("40.00"));
    invoiceWithDate();
    PdfExtractedLine recovered =
        new PdfExtractedLine(
            "CODE", "desc", new BigDecimal("2"), "KG", null, null, null, new BigDecimal("60.00"));
    PdfExtractedLine alreadyInCsv =
        new PdfExtractedLine(
            "CODE2", "desc2", BigDecimal.ONE, "KG", null, null, null, new BigDecimal("40.00"));

    service().recover("INV-7200", "a.pdf", pdf, rawId, List.of(recovered, alreadyInCsv), Set.of(0));

    verify(lineIngest)
        .record(
            argThat(
                i ->
                    i.origin().equals("PDF")
                        && i.lineTotal().compareTo(new BigDecimal("60")) == 0
                        && i.sourceRecordRef().equals("INV-7200:pdf:" + sha256(pdf) + ":0")
                        && i.rawRecordId().equals(rawId)
                        && i.invoiceDate().equals(date)));
    verify(flags, times(0)).flag(any(), any(), any(), any(), anyString());
  }

  @Test
  void doesNothingWhenCsvAlreadyReconciles() {
    header("100.00");
    when(inventoryQuery.currentCsvLineTotalForInvoice("INV-7200"))
        .thenReturn(new BigDecimal("100.00"));
    PdfExtractedLine line =
        new PdfExtractedLine(
            "CODE", "desc", BigDecimal.ONE, "KG", null, null, null, BigDecimal.ZERO);

    service().recover("INV-7200", "a.pdf", pdf, rawId, List.of(line), Set.of(0));

    verifyNoInteractions(lineIngest);
    verifyNoInteractions(flags);
  }

  @Test
  void flagsButCreatesNothingWhenNeitherReconciles() {
    header("100.00");
    when(inventoryQuery.currentCsvLineTotalForInvoice("INV-7200"))
        .thenReturn(new BigDecimal("40.00"));
    PdfExtractedLine line =
        new PdfExtractedLine(
            "CODE", "desc", BigDecimal.ONE, "KG", null, null, null, new BigDecimal("50.00"));

    service().recover("INV-7200", "a.pdf", pdf, rawId, List.of(line), Set.of(0));

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.INVOICE_EXPORT_INCOMPLETE),
            eq("INV-7200"),
            eq("a.pdf"),
            eq(null),
            anyString());
    verifyNoInteractions(lineIngest);
  }

  @Test
  void skipsPdfLinesWithoutAnAmount() {
    header("100.00");
    when(inventoryQuery.currentCsvLineTotalForInvoice("INV-7200"))
        .thenReturn(new BigDecimal("40.00"));
    invoiceWithDate();
    // The matched 40.00 line keeps the PDF sum at 100.00 while the null-amount line is unmatched.
    PdfExtractedLine recovered =
        new PdfExtractedLine(
            "CODE", "desc", BigDecimal.ONE, "KG", null, null, null, new BigDecimal("60.00"));
    PdfExtractedLine matched =
        new PdfExtractedLine(
            "CODE2", "desc2", BigDecimal.ONE, "KG", null, null, null, new BigDecimal("40.00"));
    PdfExtractedLine noAmount =
        new PdfExtractedLine("CODE3", "desc3", BigDecimal.ONE, "KG", null, null, null, null);

    service()
        .recover(
            "INV-7200", "a.pdf", pdf, rawId, List.of(recovered, matched, noAmount), Set.of(0, 2));

    verify(lineIngest).record(argThat(i -> i.lineTotal().compareTo(new BigDecimal("60")) == 0));
    verify(lineIngest, times(1)).record(any(InvoiceLineInput.class));
  }

  @Test
  void createsNothingWhenHeaderIsMissing() {
    when(invoiceQuery.amountExTaxForInvoiceNumber("INV-7200")).thenReturn(Optional.empty());
    PdfExtractedLine line =
        new PdfExtractedLine(
            "CODE", "desc", BigDecimal.ONE, "KG", null, null, null, new BigDecimal("60.00"));

    service().recover("INV-7200", "a.pdf", pdf, rawId, List.of(line), Set.of(0));

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.INVOICE_EXPORT_INCOMPLETE),
            eq("INV-7200"),
            eq("a.pdf"),
            eq(null),
            anyString());
    verifyNoInteractions(lineIngest);
  }

  @Test
  void doesNotOverCreateLinesThatExceedTheHeader() {
    // header 100, csv 40, a single unmatched 100 line: the PDF sum (100) reconciles, but the
    // unmatched line (100) over-accounts for the 60 shortfall, so nothing may be created.
    header("100.00");
    when(inventoryQuery.currentCsvLineTotalForInvoice("INV-7200"))
        .thenReturn(new BigDecimal("40.00"));
    PdfExtractedLine line =
        new PdfExtractedLine(
            "CODE", "desc", BigDecimal.ONE, "KG", null, null, null, new BigDecimal("100.00"));

    service().recover("INV-7200", "a.pdf", pdf, rawId, List.of(line), Set.of(0));

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.INVOICE_EXPORT_INCOMPLETE),
            eq("INV-7200"),
            eq("a.pdf"),
            eq(null),
            anyString());
    verifyNoInteractions(lineIngest);
  }

  @Test
  void flagsButCreatesNothingWhenInvoiceDateCannotBeResolved() {
    header("100.00");
    when(inventoryQuery.currentCsvLineTotalForInvoice("INV-7200"))
        .thenReturn(new BigDecimal("40.00"));
    when(invoiceQuery.currentInvoices()).thenReturn(List.of());
    PdfExtractedLine recovered =
        new PdfExtractedLine(
            "CODE", "desc", BigDecimal.ONE, "KG", null, null, null, new BigDecimal("60.00"));
    PdfExtractedLine alreadyInCsv =
        new PdfExtractedLine(
            "CODE2", "desc2", BigDecimal.ONE, "KG", null, null, null, new BigDecimal("40.00"));

    service().recover("INV-7200", "a.pdf", pdf, rawId, List.of(recovered, alreadyInCsv), Set.of(0));

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.INVOICE_EXPORT_INCOMPLETE),
            eq("INV-7200"),
            eq("a.pdf"),
            eq(null),
            anyString());
    verifyNoInteractions(lineIngest);
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
