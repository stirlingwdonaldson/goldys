package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalInvoiceIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalInvoiceService invoiceService;
  @Autowired CanonicalInvoiceLineService lineService;
  @Autowired CanonicalInvoiceLineRepository lineRepository;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_invoice");
    jdbc.update("truncate table canonical_invoice_line");
  }

  @Test
  void unchangedInvoiceRetryDoesNotAppendAVersion() {
    InvoiceInput input = invoice("INV-1001", "Supplier A", "2026-09-20", "120.00");

    CanonicalInvoice first = invoiceService.record(input);
    CanonicalInvoice retry = invoiceService.record(input);

    assertThat(retry.id()).isEqualTo(first.id());
  }

  @Test
  void changedLineSupersedesThePriorVersion() {
    InvoiceLineInput before = line("INV-1001", "1", "2026-09-20", "potatoes", "25.00");
    CanonicalInvoiceLine first = lineService.record(before);
    InvoiceLineInput after =
        new InvoiceLineInput(
            "CTB",
            "INV-1001:1",
            "INV-1001",
            LocalDate.of(2026, 9, 20),
            "potatoes",
            new BigDecimal("2"),
            new BigDecimal("12.50"),
            new BigDecimal("27.00"),
            null,
            before.rawRecordId());

    CanonicalInvoiceLine corrected = lineService.record(after);

    assertThat(corrected.id()).isNotEqualTo(first.id());
    assertThat(corrected.logicalEntityId()).isEqualTo(first.logicalEntityId());
    assertThat(lineRepository.findAllCurrent()).hasSize(1);
    assertThat(lineRepository.findAllCurrent().get(0).lineTotal())
        .isEqualByComparingTo(new BigDecimal("27.00"));
  }

  private InvoiceInput invoice(String number, String supplier, String date, String total) {
    return new InvoiceInput(
        "CTB", number, supplier, LocalDate.parse(date), null, new BigDecimal(total), rawRecord());
  }

  private InvoiceLineInput line(
      String number, String seq, String date, String product, String lineTotal) {
    return new InvoiceLineInput(
        "CTB",
        number + ":" + seq,
        number,
        LocalDate.parse(date),
        product,
        new BigDecimal("2"),
        new BigDecimal("12.50"),
        new BigDecimal(lineTotal),
        null,
        rawRecord());
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'CTB', 'test', 'SUCCESS', now(), 1, 1)",
        runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'CTB', 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }
}
