package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class IngestionFailureDetailTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired IngestionRunService runs;
  @Autowired IngestionService ingestion;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table ingestion_run, ingestion_failure, raw_record cascade");
  }

  @Test
  void latestRunPerSourceCarriesTheLatestFailureDetail() {
    UUID runId = runs.start("CTB", "ctb-revenue", null, Instant.now());
    runs.recordFailure(runId, "AUTH_FAILED", "OAuth token rejected", null, Instant.now());
    runs.complete(runId, null, Instant.now());

    IngestionRunSummary summary = ingestion.latestRunPerSource().get(0);

    assertThat(summary.failure()).isNotNull();
    assertThat(summary.failure().type()).isEqualTo("AUTH_FAILED");
    assertThat(summary.failure().message()).isEqualTo("OAuth token rejected");
    assertThat(summary.failure().at()).isNotNull();
  }

  @Test
  void latestRunPerSourceHasNullFailureForASuccessfulRun() {
    UUID runId = runs.start("CTB", "ctb-revenue", null, Instant.now());
    runs.recordFetched(runId);
    runs.complete(runId, null, Instant.now());

    IngestionRunSummary summary = ingestion.latestRunPerSource().get(0);

    assertThat(summary.failure()).isNull();
  }
}
