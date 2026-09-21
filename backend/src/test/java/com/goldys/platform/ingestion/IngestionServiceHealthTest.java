package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.ingestion.port.SourceConnector;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestionServiceHealthTest {

  private static final Instant T0 = Instant.parse("2026-09-19T10:00:00Z");

  @Test
  void completenessIsShareOfCompletedRunsThatRanCleanly() {
    IngestionService service =
        service(
            List.of(
                completed("CTB", IngestionStatus.SUCCESS, T0, T0.plusSeconds(60)),
                completed("LIGHTSPEED", IngestionStatus.NO_NEW_DATA, T0, T0.plusSeconds(60)),
                completed("CTB", IngestionStatus.FAILED, T0, T0.plusSeconds(60))));

    assertThat(service.health().completenessPercent()).isEqualTo(67);
  }

  @Test
  void emptyLedgerYieldsNullMetrics() {
    IngestionService service = service(List.of());

    IngestionHealth health = service.health();

    assertThat(health.completenessPercent()).isNull();
    assertThat(health.timeToDetectFailure()).isNull();
  }

  @Test
  void danglingRunningRowIsExcludedFromBothMetrics() {
    IngestionService service =
        service(
            List.of(
                completed("CTB", IngestionStatus.SUCCESS, T0, T0.plusSeconds(60)),
                IngestionRun.start("CTB", "ctb-revenue", null, T0)));

    IngestionHealth health = service.health();

    assertThat(health.completenessPercent()).isEqualTo(100);
    assertThat(health.timeToDetectFailure()).isNull();
  }

  @Test
  void timeToDetectAveragesFailedRunDurations() {
    IngestionService service =
        service(
            List.of(
                completed("CTB", IngestionStatus.FAILED, T0, T0.plusSeconds(40 * 60)),
                completed("CTB", IngestionStatus.PARTIAL, T0, T0.plusSeconds(44 * 60))));

    assertThat(service.health().timeToDetectFailure()).isEqualTo("42m avg");
  }

  @Test
  void durationsFormatAcrossUnits() {
    IngestionService service =
        service(List.of(completed("CTB", IngestionStatus.FAILED, T0, T0.plusSeconds(90))));

    assertThat(service.health().timeToDetectFailure()).isEqualTo("1m avg");
  }

  private static IngestionRun completed(
      String source, IngestionStatus status, Instant start, Instant end) {
    IngestionRun run = IngestionRun.start(source, source + "-connector", null, start);
    run.complete(status, null, null, end);
    return run;
  }

  private static IngestionService service(List<IngestionRun> runs) {
    IngestionRunRepository repository = mock(IngestionRunRepository.class);
    when(repository.findAllByOrderByStartedAtDesc()).thenReturn(runs);
    return new IngestionService(
        mock(IngestionRunService.class),
        mock(RawPayloadService.class),
        repository,
        mock(ConnectorRunner.class),
        List.<SourceConnector>of());
  }
}
