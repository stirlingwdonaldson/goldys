package com.goldys.platform.ingestion;

import com.goldys.platform.connectors.ctb.CtInvoiceCsvIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the CTB SFTP drop for invoice files. CSVs are fed through the CSV pipeline (raw +
 * canonicalize); PDFs are stored byte-faithfully raw-only (PDF enrichment is a later phase).
 * Disabled until {@code ctb.sftp.enabled=true}; cron in Australia/Melbourne.
 */
@Component
@ConditionalOnProperty(name = "ctb.sftp.enabled", havingValue = "true")
public class CtbSftpPull {
  private static final Logger log = LoggerFactory.getLogger(CtbSftpPull.class);

  private final SftpDrop drop;
  private final IngestionService ingestion;
  private final CtInvoiceCsvIngestService csvIngest;

  public CtbSftpPull(
      SftpDrop drop, IngestionService ingestion, CtInvoiceCsvIngestService csvIngest) {
    this.drop = drop;
    this.ingestion = ingestion;
    this.csvIngest = csvIngest;
  }

  @Scheduled(cron = "${ctb.sftp.cron:0 15 4 * * *}", zone = "Australia/Melbourne")
  public void pull() {
    try {
      for (SftpDrop.SftpFile file : drop.list()) {
        try {
          byte[] bytes = drop.download(file.path());
          if (file.filename().toLowerCase().endsWith(".csv")) {
            csvIngest.ingest(bytes);
          } else {
            ingestion.ingestPush(
                "CTB",
                "ctb-invoice-pdf",
                FetchMethod.FILE_EXPORT,
                "application/pdf",
                bytes,
                null,
                "ctb-sftp");
          }
          log.info("SFTP drop processed {}", file.filename());
        } catch (RuntimeException e) {
          // Keep pulling the rest; a single bad file must not stop the poll.
          log.warn("SFTP drop pull failed for {}", file.path(), e);
        }
      }
    } catch (RuntimeException e) {
      // list() itself failed (e.g. unreachable host); surface it without killing the task thread.
      log.warn("SFTP drop poll failed", e);
    }
  }
}
