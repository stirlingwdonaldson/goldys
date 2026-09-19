package com.goldys.platform.ingestion;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ConnectorRunnerTest {
  @Autowired ConnectorRunner runner;
  @Autowired IngestionRunRepository runs;
  @Autowired RawRecordRepository rawRecords;
  @Autowired IngestionFailureRepository failures;

  private static FetchedPayload page(String html) {
    return new FetchedPayload(
        FetchMethod.SCRAPE, "text/html", html.getBytes(UTF_8), "UTF-8", "fixture");
  }

  @Test
  void payloadAcceptedBeforeConnectorFailureRemainsPersisted() {
    SourceConnector connector =
        new SourceConnector() {
          @Override
          public String sourceSystem() {
            return "LIGHTSPEED";
          }

          @Override
          public String connectorName() {
            return "fixture";
          }

          @Override
          public void fetch(String watermark, IngestionSink sink) {
            sink.accept(page("<table>first</table>"));
            throw new ConnectorFetchException("SCHEMA_MISMATCH", "second page changed");
          }
        };

    UUID runId = runner.run(connector, null);

    assertThat(rawRecords.findByIngestionRunId(runId)).hasSize(1);
    assertThat(runs.findById(runId).orElseThrow().status()).isEqualTo(IngestionStatus.PARTIAL);
    assertThat(failures.findByIngestionRunIdOrderByOccurredAtAsc(runId))
        .extracting(IngestionFailure::failureType)
        .containsExactly("SCHEMA_MISMATCH");
  }

  @Test
  void connectorThatDeliversEverythingCompletesAsSuccess() {
    SourceConnector connector = fixture(sink -> sink.accept(page("<table>only</table>")));

    UUID runId = runner.run(connector, "page-1");

    IngestionRun run = runs.findById(runId).orElseThrow();
    assertThat(run.status()).isEqualTo(IngestionStatus.SUCCESS);
    assertThat(run.fetchedCount()).isEqualTo(1);
    assertThat(run.persistedCount()).isEqualTo(1);
    assertThat(rawRecords.findByIngestionRunId(runId)).hasSize(1);
  }

  @Test
  void connectorWithNothingNewCompletesAsNoNewData() {
    SourceConnector connector = fixture(sink -> {});

    UUID runId = runner.run(connector, "week-1");

    IngestionRun run = runs.findById(runId).orElseThrow();
    assertThat(run.status()).isEqualTo(IngestionStatus.NO_NEW_DATA);
    assertThat(run.outputWatermark()).isEqualTo("week-1");
    assertThat(failures.findByIngestionRunIdOrderByOccurredAtAsc(runId)).isEmpty();
  }

  @Test
  void unexpectedFailureIsRecordedWithoutLeakingItsMessageAndIsRethrown() {
    SourceConnector connector =
        fixture(
            sink -> {
              throw new IllegalStateException("token=super-secret-value");
            });

    assertThatThrownBy(() -> runner.run(connector, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("super-secret-value");

    UUID runId =
        runs.findAll().stream().map(IngestionRun::id).reduce((first, last) -> last).orElseThrow();
    assertThat(runs.findById(runId).orElseThrow().status()).isEqualTo(IngestionStatus.FAILED);
    assertThat(failures.findByIngestionRunIdOrderByOccurredAtAsc(runId))
        .singleElement()
        .satisfies(
            failure -> {
              assertThat(failure.failureType()).isEqualTo("UNEXPECTED");
              assertThat(failure.detail()).contains("IllegalStateException");
              assertThat(failure.detail()).doesNotContain("super-secret-value");
            });
  }

  @Test
  void fetchedPayloadCopiesItsBytes() {
    byte[] bytes = {1, 2, 3};
    FetchedPayload payload =
        new FetchedPayload(FetchMethod.API, "application/json", bytes, "UTF-8", "fixture");

    bytes[0] = 9;
    payload.bytes()[1] = 9;

    assertThat(payload.bytes()).isEqualTo(new byte[] {1, 2, 3});
  }

  private static SourceConnector fixture(java.util.function.Consumer<IngestionSink> body) {
    return new SourceConnector() {
      @Override
      public String sourceSystem() {
        return "LIGHTSPEED";
      }

      @Override
      public String connectorName() {
        return "fixture";
      }

      @Override
      public void fetch(String watermark, IngestionSink sink) {
        body.accept(sink);
      }
    };
  }
}
