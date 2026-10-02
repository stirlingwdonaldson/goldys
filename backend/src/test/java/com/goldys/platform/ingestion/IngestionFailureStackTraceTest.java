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
class IngestionFailureStackTraceTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired IngestionRunService runs;
  @Autowired IngestionFailureRepository failures;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table ingestion_run, ingestion_failure, raw_record cascade");
  }

  @Test
  void recordsTheFullStackTrace() {
    UUID runId = runs.start("CTB", "ctb-revenue", null, Instant.now());
    String trace = "java.lang.RuntimeException: boom\n\tat com.example.Foo.bar(Foo.java:1)\n";
    runs.recordFailure(runId, "UNEXPECTED", "RuntimeException", trace, Instant.now());

    IngestionFailure saved = failures.findByIngestionRunIdOrderByOccurredAtAsc(runId).get(0);

    assertThat(saved.stackTrace()).isEqualTo(trace);
  }
}
