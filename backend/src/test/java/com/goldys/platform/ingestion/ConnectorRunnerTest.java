package com.goldys.platform.ingestion;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(awaitTerminal(runId)).isEqualTo(IngestionStatus.PARTIAL);
    assertThat(rawRecords.findByIngestionRunId(runId)).hasSize(1);
    assertThat(failures.findByIngestionRunIdOrderByOccurredAtAsc(runId))
        .extracting(IngestionFailure::failureType)
        .containsExactly("SCHEMA_MISMATCH");
  }

  @Test
  void connectorThatDeliversEverythingCompletesAsSuccess() {
    SourceConnector connector = fixture(sink -> sink.accept(page("<table>only</table>")));

    UUID runId = runner.run(connector, "page-1");

    assertThat(awaitTerminal(runId)).isEqualTo(IngestionStatus.SUCCESS);
    IngestionRun run = runs.findById(runId).orElseThrow();
    assertThat(run.fetchedCount()).isEqualTo(1);
    assertThat(run.persistedCount()).isEqualTo(1);
    assertThat(rawRecords.findByIngestionRunId(runId)).hasSize(1);
  }

  @Test
  void connectorWithNothingNewCompletesAsNoNewData() {
    SourceConnector connector = fixture(sink -> {});

    UUID runId = runner.run(connector, "week-1");

    assertThat(awaitTerminal(runId)).isEqualTo(IngestionStatus.NO_NEW_DATA);
    assertThat(runs.findById(runId).orElseThrow().outputWatermark()).isEqualTo("week-1");
    assertThat(failures.findByIngestionRunIdOrderByOccurredAtAsc(runId)).isEmpty();
  }

  @Test
  void unexpectedFailureIsRecordedWithoutLeakingItsMessage() {
    SourceConnector connector =
        fixture(
            sink -> {
              throw new IllegalStateException("token=super-secret-value");
            });

    UUID runId = runner.run(connector, null);

    assertThat(awaitTerminal(runId)).isEqualTo(IngestionStatus.FAILED);
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

  /** Polls the run until it leaves RUNNING, then returns its terminal status. */
  private IngestionStatus awaitTerminal(UUID runId) {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      IngestionRun run = runs.findById(runId).orElseThrow();
      if (run.status() != IngestionStatus.RUNNING) {
        return run.status();
      }
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("interrupted while awaiting run completion", e);
      }
    }
    throw new AssertionError("run " + runId + " did not reach a terminal state");
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
