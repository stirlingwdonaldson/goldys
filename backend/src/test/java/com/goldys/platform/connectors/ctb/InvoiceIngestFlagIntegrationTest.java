package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class InvoiceIngestFlagIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired InvoiceIngestFlagService flags;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table invoice_ingest_flag");
  }

  @Test
  void persistsAFlagWithNullableIdentityColumns() {
    flags.flag(InvoiceIngestFlagType.PDF_UNPARSEABLE, null, null, null, "no text");

    assertThat(
            jdbc.queryForObject(
                "select flag_type || '|' || coalesce(invoice_number, '') || '|' || coalesce(pdf_filename, '') || '|' || detail "
                    + "from invoice_ingest_flag",
                String.class))
        .isEqualTo("PDF_UNPARSEABLE|||no text");
  }

  @Test
  void doesNotDuplicateAnEquivalentFlag() {
    flags.flag(InvoiceIngestFlagType.MISSING_PDF, "INV-1", "a.pdf", null, "missing");
    flags.flag(InvoiceIngestFlagType.MISSING_PDF, "INV-1", "a.pdf", null, "missing");

    assertThat(jdbc.queryForObject("select count(*) from invoice_ingest_flag", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void treatsNullAndNonNullIdentityValuesAsDistinct() {
    flags.flag(InvoiceIngestFlagType.PDF_ONLY_LINE, "INV-2", null, "CODE-A", "desc");
    flags.flag(InvoiceIngestFlagType.PDF_ONLY_LINE, "INV-2", null, "CODE-B", "desc");

    assertThat(jdbc.queryForObject("select count(*) from invoice_ingest_flag", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void resolvedFlagsLeaveTheOpenListButRemainRecorded() {
    flags.flag(InvoiceIngestFlagType.PDF_ONLY_LINE, "INV-3", "a.pdf", "CODE", "desc");
    assertThat(flags.listOpenFlags()).hasSize(1);

    flags.resolve(jdbc.queryForObject("select id from invoice_ingest_flag", java.util.UUID.class));

    assertThat(flags.listOpenFlags()).isEmpty();
    assertThat(flags.listFlags()).hasSize(1); // retained as history
  }

  @Test
  void openPdfOnlyLineFlagsExposeIdentityForReconciliation() {
    flags.flag(InvoiceIngestFlagType.PDF_ONLY_LINE, "INV-4", "a.pdf", "CODE", "lima beans");
    flags.flag(InvoiceIngestFlagType.PDF_ONLY_LINE, "INV-4", "a.pdf", "CODE2", "other");
    flags.flag(InvoiceIngestFlagType.MISSING_PDF, "INV-5", null, null, "missing");

    assertThat(flags.openPdfOnlyLineFlags())
        .extracting(
            InvoiceIngestFlagService.OpenFlag::invoiceNumber,
            InvoiceIngestFlagService.OpenFlag::stockCode,
            InvoiceIngestFlagService.OpenFlag::detail)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("INV-4", "CODE", "lima beans"),
            org.assertj.core.groups.Tuple.tuple("INV-4", "CODE2", "other"));
  }
}
