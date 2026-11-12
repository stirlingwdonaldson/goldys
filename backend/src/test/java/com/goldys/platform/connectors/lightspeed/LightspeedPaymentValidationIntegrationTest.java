package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.reconciliation.PaymentProjector;
import com.goldys.platform.semantic.PaymentMetricsQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class LightspeedPaymentValidationIntegrationTest {
  @Autowired LightspeedPaymentIngestService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired RawLedgerQuery ledger;
  @Autowired PaymentProjector projector;
  @Autowired PaymentMetricsQuery metrics;

  @BeforeEach
  void clean() {
    jdbc.update("truncate canonical_payment,resolved_payment_day");
  }

  @Test
  void realGroupedSplitTendersRemainIdempotentAndReachResolvedTotals() throws Exception {
    byte[] body =
        envelope(
            Files.readString(
                Path.of("src/test/resources/fixtures/lightspeed/payments_sample.csv")));
    service.ingest(body);
    service.ingest(body);
    assertThat(jdbc.queryForObject("select count(*) from canonical_payment", Long.class))
        .isEqualTo(3);
    projector.recomputeAll();
    var days = metrics.dailyMix(LocalDate.parse("2026-09-19"), LocalDate.parse("2026-09-20"));
    assertThat(days).hasSize(3);
    assertThat(days.stream().map(d -> d.amount()).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("295.50");
    assertThat(days.stream().mapToLong(d -> d.count()).sum()).isEqualTo(6);
    assertThat(days.stream().map(d -> d.tip()).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("1");
  }

  @Test
  void ambiguousPaymentModeRowsRetainRawFailureAndCannotReplaceExistingFacts() throws Exception {
    String csv =
        Files.readString(Path.of("src/test/resources/fixtures/lightspeed/payments_sample.csv"));
    service.ingest(envelope(csv));
    String[] rows = csv.split("\n");
    byte[] bad = envelope(csv + rows[1].replace(",,4,Tyro", ",Tap to Pay,4,Tyro") + "\n");
    assertThatThrownBy(() -> service.ingest(bad))
        .hasFieldOrPropertyWithValue("failureType", "UNSAFE_PAYMENT_GRAIN");
    UUID raw = latestRaw();
    assertThat(ledger.payloadBytes(raw)).containsExactly(bad);
    assertThat(jdbc.queryForObject("select count(*) from canonical_payment", Long.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForList(
                "select stage || ':' || outcome from ingestion_stage where ingestion_run_id=(select ingestion_run_id from raw_record where id=?)",
                String.class,
                raw))
        .containsExactly("PARSED:FAILED");
  }

  @Test
  void missingLateAmountCannotPartiallyPublishTheEarlierValidRow() throws Exception {
    byte[] bad =
        envelope(
            "Payment Data Created Date,Payments Sale Number,Payment Data Amount\n"
                + "2026-11-11,valid,10\n2026-11-11,missing,\n");
    assertThatThrownBy(() -> service.ingest(bad))
        .hasFieldOrPropertyWithValue("failureType", "CONNECTOR_SCHEMA_MISMATCH");
    assertThat(ledger.payloadBytes(latestRaw())).containsExactly(bad);
    assertThat(jdbc.queryForObject("select count(*) from canonical_payment", Long.class)).isZero();
  }

  private byte[] envelope(String csv) throws Exception {
    return new ObjectMapper().writeValueAsBytes(Map.of("attachment", Map.of("data", csv)));
  }

  private UUID latestRaw() {
    return jdbc.queryForObject(
        "select id from raw_record where fetcher_identity='lightspeed-payments' order by fetched_at desc limit 1",
        UUID.class);
  }
}
