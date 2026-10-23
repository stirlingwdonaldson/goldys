package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.connectors.lightspeed.LightspeedSaleItemIngestService;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class PushProcessingIntegrationTest {
  @Autowired IngestionService ingestion;
  @Autowired LightspeedSaleItemIngestService saleItems;
  @Autowired RawRecordRepository raw;
  @Autowired IngestionRunRepository runs;
  @Autowired IngestionFailureRepository failures;

  @Test
  void failedWebhookParseKeepsExactBytesAndCannotRemainSuccessful() {
    byte[] bytes = "incomplete-json".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> saleItems.ingest(bytes)).isInstanceOf(RuntimeException.class);
    var run =
        runs.latestPerConnector().stream()
            .filter(r -> r.connectorName().equals("lightspeed-sale-items"))
            .findFirst()
            .orElseThrow();
    assertThat(run.status()).isEqualTo(IngestionStatus.PARTIAL);
    assertThat(raw.findByIngestionRunId(run.id()))
        .singleElement()
        .satisfies(record -> assertThat(record.payloadBytes()).isEqualTo(bytes));
    assertThat(failures.findByIngestionRunIdOrderByOccurredAtAsc(run.id()))
        .extracting(IngestionFailure::failureType)
        .containsExactly("PROCESSING_PARSED_FAILED");
  }

  @Test
  void laterSuccessfulStageDoesNotEraseTheFailureHistory() {
    var rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "processing-test",
            FetchMethod.FILE_EXPORT,
            "application/json",
            new byte[] {1},
            null,
            "processing-test");
    var runId = raw.findById(rawId).orElseThrow().ingestionRunId();
    ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
    ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.SUCCESS);
    assertThat(runs.findById(runId).orElseThrow().status()).isEqualTo(IngestionStatus.PARTIAL);
    assertThat(failures.findByIngestionRunIdOrderByOccurredAtAsc(runId)).hasSize(1);
  }
}
