package com.goldys.platform.ingestion;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pulls files from the CTB SFTP drop and stores them byte-faithfully in the raw ledger (CSV and PDF
 * alike). Raw-only by design: canonicalization is a separate, later step so the shapes can be
 * checked before anything is interpreted. Disabled until {@code
 * app.scheduling.ctb-sftp.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.ctb-sftp.enabled", havingValue = "true")
public class CtbSftpPull {
  private static final Logger log = LoggerFactory.getLogger(CtbSftpPull.class);

  private final SftpDrop drop;
  private final IngestionService ingestion;

  public CtbSftpPull(SftpDrop drop, IngestionService ingestion) {
    this.drop = drop;
    this.ingestion = ingestion;
  }

  @Scheduled(cron = "${app.scheduling.ctb-sftp.cron:0 0 4 * * *}", zone = "Australia/Melbourne")
  public void pull() {
    for (SftpDrop.SftpFile file : drop.list()) {
      try {
        byte[] bytes = drop.download(file.path());
        boolean csv = file.filename().toLowerCase().endsWith(".csv");
        ingestion.ingestPush(
            "CTB",
            csv ? "ctb-invoices" : "ctb-invoice-pdf",
            FetchMethod.FILE_EXPORT,
            csv ? "text/csv" : "application/pdf",
            bytes,
            csv ? StandardCharsets.UTF_8.name() : null,
            "ctb-sftp");
        log.info("SFTP drop stored {}", file.filename());
      } catch (RuntimeException e) {
        // A failure is already recorded in the ingestion ledger; keep pulling the rest.
        log.warn("SFTP drop pull failed for {}", file.path(), e);
      }
    }
  }
}
