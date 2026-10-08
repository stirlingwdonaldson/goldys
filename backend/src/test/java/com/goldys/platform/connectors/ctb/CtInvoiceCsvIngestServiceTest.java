package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInvoiceIngest;
import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.InvoiceInput;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.ingestion.IngestionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
  @Mock InvoiceIngestFlagService flags;

  private CtInvoiceCsvIngestService service() {
    return new CtInvoiceCsvIngestService(
        ingestion, new CtInvoiceCsvParser(), canonical, lineCanonical, flags);
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
    verify(canonical).record(inv.capture());
    assertThat(inv.getValue().invoiceNumber()).isEqualTo("INV-1");
    assertThat(inv.getValue().supplierName()).isEqualTo("Bruno's");

    ArgumentCaptor<InvoiceLineInput> line = ArgumentCaptor.forClass(InvoiceLineInput.class);
    verify(lineCanonical, times(2)).record(line.capture());
    assertThat(line.getAllValues().get(0).productNameKey()).isEqualTo("beer");
    assertThat(line.getAllValues().get(0).lineTotal())
        .isEqualByComparingTo(new BigDecimal("120.00"));
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

    verify(canonical, times(2)).record(any(InvoiceInput.class));
    verify(lineCanonical, times(2)).record(any(InvoiceLineInput.class));
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
    verify(canonical).record(inv.capture());
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
}
