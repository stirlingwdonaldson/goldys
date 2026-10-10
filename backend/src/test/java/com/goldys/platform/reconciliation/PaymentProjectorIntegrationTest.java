package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalPaymentIngest;
import com.goldys.platform.canonical.PaymentInput;
import com.goldys.platform.semantic.PaymentMetricsQuery;
import com.goldys.platform.semantic.PaymentMix;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class PaymentProjectorIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalPaymentIngest ingest;
  @Autowired PaymentProjector projector;
  @Autowired PaymentMetricsQuery query;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_payment");
    jdbc.update("truncate table resolved_payment_day");
  }

  @Test
  void aggregatesAmountsAndCountsPerDayAndType() {
    LocalDate date = LocalDate.of(2026, 9, 20);
    recordPayment(date, "SP-1", "Tyro", "222", "0", 4);
    recordPayment(date, "SP-2", "Tyro", "37", "1", 1);
    recordPayment(date, "SP-3", "Cash", "50", "0", 1);

    projector.recompute(date);

    List<PaymentMix> mix = query.dailyMix(date, date);
    assertThat(mix).hasSize(2);

    PaymentMix tyro =
        mix.stream().filter(m -> m.paymentTypeName().equals("Tyro")).findFirst().get();
    assertThat(tyro.amount()).isEqualByComparingTo("259");
    assertThat(tyro.tip()).isEqualByComparingTo("1");
    assertThat(tyro.count()).isEqualTo(5);
    assertThat(tyro.hasConflict()).isFalse();

    PaymentMix cash =
        mix.stream().filter(m -> m.paymentTypeName().equals("Cash")).findFirst().get();
    assertThat(cash.amount()).isEqualByComparingTo("50");
    assertThat(cash.count()).isEqualTo(1);
  }

  private void recordPayment(
      LocalDate date, String sale, String type, String amount, String tip, int count) {
    ingest.record(
        new PaymentInput(
            "LIGHTSPEED",
            date,
            sale,
            type,
            type,
            "4",
            null,
            null,
            new BigDecimal(amount),
            new BigDecimal(tip),
            new BigDecimal(amount),
            BigDecimal.ZERO,
            count,
            0,
            "Reconciled",
            null,
            null,
            null,
            null,
            "96181",
            null,
            rawRecord()));
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'LIGHTSPEED', 'test', 'SUCCESS', now(), 1, 1)",
        runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'LIGHTSPEED', 'FILE_EXPORT', 'application/json', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }
}
