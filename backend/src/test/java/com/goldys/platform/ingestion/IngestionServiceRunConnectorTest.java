package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.ingestion.port.SourceConnector;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionServiceRunConnectorTest {

  @Test
  void unknownSourceIsRejected() {
    IngestionService service = service(List.of());

    assertThatThrownBy(() -> service.runConnector("CTB"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CTB");
  }

  @Test
  void runsConnectorAndReturnsItsSummary() {
    SourceConnector connector = mock(SourceConnector.class);
    when(connector.sourceSystem()).thenReturn("CTB");
    when(connector.connectorName()).thenReturn("ctb-revenue");

    ConnectorRunner runner = mock(ConnectorRunner.class);
    UUID runId = UUID.randomUUID();
    when(runner.run(connector, null)).thenReturn(runId);

    IngestionRun run = mock(IngestionRun.class);
    when(run.sourceSystem()).thenReturn("CTB");
    when(run.connectorName()).thenReturn("ctb-revenue");
    when(run.status()).thenReturn(IngestionStatus.SUCCESS);
    when(run.startedAt()).thenReturn(Instant.EPOCH);
    when(run.failureSummary()).thenReturn(null);

    IngestionRunRepository repository = mock(IngestionRunRepository.class);
    when(repository.findById(runId)).thenReturn(Optional.of(run));

    IngestionService service = service(List.of(connector), runner, repository);

    IngestionRunSummary summary = service.runConnector("ctb");

    assertThat(summary.sourceSystem()).isEqualTo("CTB");
    assertThat(summary.status()).isEqualTo("SUCCESS");
  }

  private static IngestionService service(List<SourceConnector> connectors) {
    return service(connectors, mock(ConnectorRunner.class), mock(IngestionRunRepository.class));
  }

  private static IngestionService service(
      List<SourceConnector> connectors, ConnectorRunner runner, IngestionRunRepository repository) {
    return new IngestionService(
        mock(IngestionRunService.class),
        mock(RawPayloadService.class),
        repository,
        runner,
        connectors);
  }
}
