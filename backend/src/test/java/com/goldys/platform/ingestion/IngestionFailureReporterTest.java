package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionFailureReporterTest {

  @Test
  void sanitizedEventKeepsFramesButNeverTheOriginalMessageOrCause() {
    RuntimeException original =
        new IllegalStateException("GET https://vendor.example/api?token=SECRET failed");

    var sanitized =
        new IngestionFailureReporter.SanitizedIngestionFailure(
            "Ingestion failure [CTB] " + original.getClass().getName(), original.getStackTrace());

    assertThat(sanitized.getMessage()).doesNotContain("SECRET").contains("IllegalStateException");
    assertThat(sanitized.getCause()).isNull();
    assertThat(sanitized.getStackTrace()).isEqualTo(original.getStackTrace());
  }

  @Test
  void reportingWithoutAConfiguredSentryIsANoOp() {
    // No DSN in tests: the reporter must neither throw nor interfere with ingestion.
    assertThatCode(
            () -> {
              IngestionFailureReporter.reportUnexpected(
                  UUID.randomUUID(), "CTB", new IllegalStateException("boom"));
              IngestionFailureReporter.reportClassified(
                  UUID.randomUUID(),
                  "CTB",
                  "AUTH_FAILED",
                  "login rejected",
                  new RuntimeException());
            })
        .doesNotThrowAnyException();
  }
}
