package com.goldys.platform.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CtbSftpPullTest {

  @Mock SftpDrop drop;
  @Mock IngestionService ingestion;

  @Test
  void pullsAndStoresRawCsvAndPdf() {
    SftpDrop.SftpFile csv = new SftpDrop.SftpFile("in/inv.csv", "inv.csv");
    SftpDrop.SftpFile pdf = new SftpDrop.SftpFile("in/bruno.pdf", "bruno.pdf");
    when(drop.list()).thenReturn(List.of(csv, pdf));
    when(drop.download("in/inv.csv")).thenReturn(new byte[] {1});
    when(drop.download("in/bruno.pdf")).thenReturn(new byte[] {2});

    new CtbSftpPull(drop, ingestion).pull();

    verify(ingestion)
        .ingestPush(
            eq("CTB"),
            eq("ctb-invoices"),
            eq(FetchMethod.FILE_EXPORT),
            eq("text/csv"),
            any(byte[].class),
            eq("UTF-8"),
            eq("ctb-sftp"));
    verify(ingestion)
        .ingestPush(
            eq("CTB"),
            eq("ctb-invoice-pdf"),
            eq(FetchMethod.FILE_EXPORT),
            eq("application/pdf"),
            any(byte[].class),
            isNull(),
            eq("ctb-sftp"));
  }
}
