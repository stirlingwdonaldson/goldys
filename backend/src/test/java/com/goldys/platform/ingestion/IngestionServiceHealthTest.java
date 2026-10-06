package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.ingestion.port.SourceConnector;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    // Derive the aggregates the real repository now computes in SQL.
    Map<IngestionStatus, Long> counts =
        runs.stream().collect(Collectors.groupingBy(IngestionRun::status, Collectors.counting()));
    List<StatusCount> statusCounts =
        counts.entrySet().stream().map(e -> new StatusCount(e.getKey(), e.getValue())).toList();
    when(repository.statusCounts()).thenReturn(statusCounts);

    double avgSeconds =
        runs.stream()
            .filter(
                r -> r.status() == IngestionStatus.FAILED || r.status() == IngestionStatus.PARTIAL)
            .mapToDouble(r -> Duration.between(r.startedAt(), r.completedAt()).toMillis() / 1000.0)
            .average()
            .orElse(0.0);
    when(repository.avgFailedDurationSeconds()).thenReturn(avgSeconds > 0 ? avgSeconds : null);

    return new IngestionService(
        mock(IngestionRunService.class),
        mock(RawPayloadService.class),
        repository,
        mock(IngestionFailureRepository.class),
        mock(ConnectorRunner.class),
        List.<SourceConnector>of());
  }
}
