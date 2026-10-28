package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IngestionRunQualificationTest {
  @Test
  void qualifiesSuccessfulRunWithoutMaskingAnExistingProcessingFailure() {
    var now = Instant.parse("2026-10-27T00:00:00Z");
    var run = IngestionRun.start("LIGHTSPEED", "sale-items", null, now);
    run.recordPersisted();
    run.complete(IngestionStatus.SUCCESS, null, null, now);
    run.qualify("Metadata unobserved");
    assertThat(run.status()).isEqualTo(IngestionStatus.PARTIAL);
    assertThat(run.failureSummary()).isEqualTo("Metadata unobserved");
    run.processingFailed(IngestionStageKind.CANONICALIZED);
    run.qualify("Another qualification");
    assertThat(run.failureSummary()).isEqualTo("Processing failed at CANONICALIZED");
    assertThat(run.status()).isEqualTo(IngestionStatus.PARTIAL);
  }
}
