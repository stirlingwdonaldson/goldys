package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceIngest;
import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.InvoiceIdentity;
import com.goldys.platform.canonical.InvoiceInput;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.canonical.StockItemIngest;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.IngestionStageKind;
import com.goldys.platform.ingestion.IngestionStageOutcome;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CtInvoiceCsvIngestServiceTest {

  @Mock IngestionService ingestion;
  @Mock CanonicalInvoiceIngest canonical;
  @Mock CanonicalInvoiceLineIngest lineCanonical;
  @Mock StockItemIngest stockItems;
  @Mock InvoiceIngestFlagService flags;
  @Mock CanonicalInvoiceQuery invoiceQuery;
  @Mock CanonicalInventoryQuery inventoryQuery;

  private static final UUID INVOICE_ID = UUID.randomUUID();

  @BeforeEach
  void stubInvoiceIdentity() {
    when(canonical.recordCsv(any(InvoiceInput.class), anyList(), eq(false)))
        .thenReturn(new InvoiceIdentity(INVOICE_ID, "BRUNO::TEST", UUID.randomUUID(), "BRUNO"));
  }

  private CtInvoiceCsvIngestService service() {
    return new CtInvoiceCsvIngestService(
        ingestion,
        new CtInvoiceCsvParser(),
        canonical,
        lineCanonical,
        stockItems,
        flags,
        invoiceQuery,
        inventoryQuery);
  }

  @Test
  void discountedSourceAmountsStayUnchangedAndFlaggedWithoutPruningPdfEvidence() throws Exception {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    service()
        .ingest(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of(
                    "src/test/resources/fixtures/ctb/invoice-semantics-evidence.csv")));
    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.INVOICE_AMOUNT_BASIS_UNVERIFIED),
            eq("INV8365286"),
            any(),
            isNull(),
            any());
    assertThat(recordedLines())
        .anySatisfy(
            line -> {
              assertThat(line.invoiceNumber()).isEqualTo("INV8365286");
              assertThat(line.lineTotal()).isEqualByComparingTo("459.77");
            });
    verify(lineCanonical, never()).supersedePdfOriginLines(any(UUID.class));
    verify(lineCanonical, never()).prunePdfOriginLines(any(UUID.class), any(), any());
  }

  @Test
  void canonicalizesHeaderAndEveryLine() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    byte[] csv =
        ("Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "INV-1,Bruno's,2026-09-20,STK-7,Beer,1 EACH,120.00\n"
                + "INV-1,Bruno's,2026-09-20,STK-8,Chips,2 EACH,10.00\n")
            .getBytes(StandardCharsets.UTF_8);

    service().ingest(csv);

    ArgumentCaptor<InvoiceInput> inv = ArgumentCaptor.forClass(InvoiceInput.class);
    verify(canonical).recordCsv(inv.capture(), anyList(), eq(false));
    assertThat(inv.getValue().invoiceNumber()).isEqualTo("INV-1");
    assertThat(inv.getValue().supplierName()).isEqualTo("Bruno's");

    var lines = recordedLines();
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).productNameKey()).isEqualTo("beer");
    assertThat(lines.get(0).lineTotal()).isEqualByComparingTo(new BigDecimal("120.00"));
  }

  @Test
  void canonicalizesMultipleInvoices() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    byte[] csv =
        ("Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "INV-1,Bruno's,2026-09-20,STK-7,Beer,1 EACH,120.00\n"
                + "INV-2,Bruno's,2026-09-21,STK-8,Chips,2 EACH,10.00\n")
            .getBytes(StandardCharsets.UTF_8);

    service().ingest(csv);

    verify(canonical, times(2)).recordCsv(any(InvoiceInput.class), anyList(), eq(false));
    assertThat(recordedLines()).hasSize(2);
  }

  @Test
  void persistsThePdfFilename() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    byte[] csv =
        ("Invoice,Supplier,Date,PDF,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "INV-1,Bruno's,2026-09-20,bruno-1.pdf,STK-7,Beer,1 EACH,120.00\n")
            .getBytes(StandardCharsets.UTF_8);

    service().ingest(csv);

    ArgumentCaptor<InvoiceInput> inv = ArgumentCaptor.forClass(InvoiceInput.class);
    verify(canonical).recordCsv(inv.capture(), anyList(), eq(false));
    assertThat(inv.getValue().pdfFilename()).isEqualTo("bruno-1.pdf");
  }

  @Test
  void flagsAnInvoiceWithNoPdfFilename() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    byte[] csv =
        ("Invoice,Supplier,Date,PDF,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "INV-1,Bruno's,2026-09-20,,STK-7,Beer,1 EACH,120.00\n")
            .getBytes(StandardCharsets.UTF_8);

    service().ingest(csv);

    verify(flags)
        .flag(eq(InvoiceIngestFlagType.MISSING_PDF), eq("INV-1"), isNull(), isNull(), any());
  }

  @Test
  void recordsParsedAndCanonicalizedStagesOnSuccess() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    byte[] csv =
        ("Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "INV-1,Bruno's,2026-09-20,STK-7,Beer,1 EACH,120.00\n")
            .getBytes(StandardCharsets.UTF_8);

    service().ingest(csv);

    verify(ingestion)
        .recordStage(any(), eq(IngestionStageKind.PARSED), eq(IngestionStageOutcome.SUCCESS));
    verify(ingestion)
        .recordStage(
            any(), eq(IngestionStageKind.CANONICALIZED), eq(IngestionStageOutcome.SUCCESS));
  }

  @Test
  void flagsAnInvoiceWithMultiplePdfFilenames() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    byte[] csv =
        ("Invoice,Supplier,Date,PDF,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "INV-1,Bruno's,2026-09-20,bruno-1.pdf,STK-7,Beer,1 EACH,120.00\n"
                + "INV-1,Bruno's,2026-09-20,bruno-2.pdf,STK-8,Chips,2 EACH,10.00\n")
            .getBytes(StandardCharsets.UTF_8);

    service().ingest(csv);

    verify(flags)
        .flag(eq(InvoiceIngestFlagType.AMBIGUOUS_PDF), eq("INV-1"), isNull(), isNull(), any());
  }

  @Test
  void supersedesPdfLinesWhenTheCsvNowReconciles() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    when(invoiceQuery.amountExTaxForInvoiceId(INVOICE_ID))
        .thenReturn(Optional.of(new BigDecimal("130.00")));
    when(inventoryQuery.currentCsvLineTotalForInvoiceId(INVOICE_ID))
        .thenReturn(new BigDecimal("130.00"));
    byte[] csv =
        ("Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "INV-1,Bruno's,2026-09-20,STK-7,Beer,1 EACH,120.00\n"
                + "INV-1,Bruno's,2026-09-20,STK-8,Chips,2 EACH,10.00\n")
            .getBytes(StandardCharsets.UTF_8);

    service().ingest(csv);

    verify(lineCanonical).supersedePdfOriginLines(INVOICE_ID);
  }

  @Test
  void prunesOnlyTheExcessPdfLinesWhenTheCsvPartiallyCatchesUp() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    when(invoiceQuery.amountExTaxForInvoiceId(INVOICE_ID))
        .thenReturn(Optional.of(new BigDecimal("100.00")));
    when(inventoryQuery.currentCsvLineTotalForInvoiceId(INVOICE_ID))
        .thenReturn(new BigDecimal("70.00"));
    when(lineCanonical.prunePdfOriginLines(eq(INVOICE_ID), any(), any())).thenReturn(1);
    byte[] csv =
        ("Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "INV-1,Bruno's,2026-09-20,STK-7,Beer,1 EACH,70.00\n")
            .getBytes(StandardCharsets.UTF_8);

    service().ingest(csv);

    verify(lineCanonical, never()).supersedePdfOriginLines(any(UUID.class));
    verify(lineCanonical)
        .prunePdfOriginLines(
            eq(INVOICE_ID),
            argThat(v -> v.compareTo(new BigDecimal("30.00")) == 0),
            argThat(v -> v.compareTo(new BigDecimal("0.02")) == 0));
    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.INVOICE_EXPORT_INCOMPLETE),
            eq("INV-1"),
            isNull(),
            isNull(),
            any());
  }

  @SuppressWarnings("unchecked")
  private java.util.List<InvoiceLineInput> recordedLines() {
    return org.mockito.Mockito.mockingDetails(canonical).getInvocations().stream()
        .filter(invocation -> invocation.getMethod().getName().equals("recordCsv"))
        .flatMap(
            invocation -> ((java.util.List<InvoiceLineInput>) invocation.getArgument(1)).stream())
        .toList();
  }
}
