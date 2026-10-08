package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.connectors.ctb.CtInvoiceCsvIngestService;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.AcceptAllPasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the SFTP path end-to-end: a real (in-memory) SFTP server serves a CSV, the pull downloads
 * it, and it lands in the canonical ledger. Uses Apache SSHD — already on the classpath via
 * spring-integration-sftp — so no external server or Docker is needed.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class SftpIngestionIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired IngestionService ingestion;
  @Autowired CtInvoiceCsvIngestService csvIngest;
  @Autowired com.goldys.platform.connectors.ctb.InvoicePdfEnrichmentService pdfEnrichment;

  @TempDir Path dir;

  @Test
  void pullsCsvFromSftpAndCanonicalizes() throws Exception {
    jdbc.update("truncate table canonical_invoice");
    jdbc.update("truncate table canonical_invoice_line");

    Path dropRoot = Files.createDirectories(dir.resolve("drop"));
    Files.write(
        dropRoot.resolve("inv.csv"),
        ("Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax\n"
                + "INV-9001,Bruno's,2026-09-20,STK-7,Beer,1 EACH,120.00\n")
            .getBytes(StandardCharsets.UTF_8));

    SshServer sshd = SshServer.setUpDefaultServer();
    sshd.setPort(0);
    sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(dir.resolve("hostkey.ser")));
    sshd.setPasswordAuthenticator(AcceptAllPasswordAuthenticator.INSTANCE);
    sshd.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
    VirtualFileSystemFactory fs = new VirtualFileSystemFactory();
    fs.setUserHomeDir("testuser", dropRoot);
    sshd.setFileSystemFactory(fs);
    sshd.start();
    try {
      DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory();
      factory.setHost("localhost");
      factory.setPort(sshd.getPort());
      factory.setUser("testuser");
      factory.setPassword("pw");
      factory.setAllowUnknownKeys(true);
      SftpDrop drop = SpringIntegrationSftpDrop.create(new SftpRemoteFileTemplate(factory), "/");

      new CtbSftpPull(drop, ingestion, csvIngest, pdfEnrichment).pull();

      assertThat(
              jdbc.queryForObject(
                  "select count(*) from canonical_invoice where invoice_number = 'INV-9001'",
                  Integer.class))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from canonical_invoice_line where invoice_number = 'INV-9001'",
                  Integer.class))
          .isEqualTo(1);
    } finally {
      sshd.stop();
    }
  }
}
