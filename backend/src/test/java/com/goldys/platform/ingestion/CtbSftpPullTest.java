package com.goldys.platform.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.connectors.ctb.CtInvoiceCsvIngestService;
import com.goldys.platform.connectors.ctb.InvoicePdfEnrichmentService;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CtbSftpPullTest {

  @Mock SftpDrop drop;
  @Mock IngestionService ingestion;
  @Mock CtInvoiceCsvIngestService csvIngest;
  @Mock InvoicePdfEnrichmentService pdfEnrichment;

  @Test
  void partiallyProcessedCsvRemainsAvailableAndDoesNotBlockOtherFiles() {
    var held = new SftpDrop.SftpFile("in/held.csv", "held.csv");
    var next = new SftpDrop.SftpFile("in/next.csv", "next.csv");
    byte[] partial = new byte[] {1};
    byte[] complete = new byte[] {2};
    when(drop.list()).thenReturn(List.of(held, next));
    when(drop.download(held.path())).thenReturn(partial);
    when(drop.download(next.path())).thenReturn(complete);
    doThrow(new ConnectorFetchException("INVOICE_REVISION_WITHHELD", "Invoice revision withheld"))
        .when(csvIngest)
        .ingest(partial);

    new CtbSftpPull(drop, ingestion, csvIngest, pdfEnrichment).pull();

    verify(csvIngest).ingest(complete);
    verify(drop, never()).markProcessed(held.path());
    verify(drop).markProcessed(next.path());
  }

  @Test
  void canonicalizesCsvsAndEnrichesPdfs() {
    SftpDrop.SftpFile csv = new SftpDrop.SftpFile("in/inv.csv", "inv.csv");
    SftpDrop.SftpFile pdf = new SftpDrop.SftpFile("in/bruno.pdf", "bruno.pdf");
    when(drop.list()).thenReturn(List.of(csv, pdf));
    when(drop.download("in/inv.csv")).thenReturn(new byte[] {1});
    when(drop.download("in/bruno.pdf")).thenReturn(new byte[] {2});
    UUID pdfRawId = UUID.randomUUID();
    when(ingestion.ingestPush(
            eq("CTB"),
            eq("ctb-invoice-pdf"),
            eq(FetchMethod.FILE_EXPORT),
            eq("application/pdf"),
            any(byte[].class),
            isNull(),
            eq("ctb-sftp")))
        .thenReturn(pdfRawId);

    new CtbSftpPull(drop, ingestion, csvIngest, pdfEnrichment).pull();

    verify(csvIngest).ingest(any(byte[].class));
    verify(pdfEnrichment).enrich(any(byte[].class), eq("bruno.pdf"), eq(pdfRawId));
    verify(ingestion)
        .ingestPush(
            eq("CTB"),
            eq("ctb-invoice-pdf"),
            eq(FetchMethod.FILE_EXPORT),
            eq("application/pdf"),
            any(byte[].class),
            isNull(),
            eq("ctb-sftp"));
    verify(drop).markProcessed("in/inv.csv");
    verify(drop).markProcessed("in/bruno.pdf");
  }
}
