package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class IngestionProcessingIntegrationTest {
  @Autowired IngestionService ingestion;
  @Autowired CanonicalDailySalesIngest canonical;
  @Autowired RawRecordRepository rawRecords;
  @Autowired IngestionRunRepository runs;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;

  @Test
  void canonicalRollbackPreservesRawPayloadAndFailedStage() {
    byte[] body = "original bytes".getBytes(StandardCharsets.UTF_8);
    var rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "stage-regression",
            FetchMethod.FILE_EXPORT,
            "text/csv",
            body,
            "UTF-8",
            "stage-regression");
    var processing = new IngestionProcessing(ingestion);
    var rows = processing.parse(rawId, () -> List.of(LocalDate.of(2026, 1, 17)));
    assertThatThrownBy(
            () ->
                processing.canonicalize(
                    rawId,
                    rows,
                    date ->
                        new TransactionTemplate(transactions)
                            .executeWithoutResult(
                                status -> {
                                  canonical.record(
                                      new DailySalesInput(
                                          "LIGHTSPEED",
                                          date,
                                          BigDecimal.TEN,
                                          BigDecimal.ONE,
                                          new BigDecimal("9"),
                                          rawId));
                                  throw new IllegalStateException("rollback canonical write");
                                })))
        .hasMessage("rollback canonical write");
    var raw = rawRecords.findById(rawId).orElseThrow();
    assertThat(raw.payloadBytes()).isEqualTo(body);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_daily_sales where raw_record_id = ?",
                Integer.class,
                rawId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ingestion_stage where ingestion_run_id = ? and stage = 'CANONICALIZED' and outcome = 'FAILED'",
                Integer.class,
                raw.ingestionRunId()))
        .isEqualTo(1);
    assertThat(runs.findById(raw.ingestionRunId()).orElseThrow().status())
        .isEqualTo(IngestionStatus.PARTIAL);
  }
}
