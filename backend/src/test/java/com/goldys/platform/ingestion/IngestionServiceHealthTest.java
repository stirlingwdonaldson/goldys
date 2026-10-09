package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

  @Test
  void pdfEnrichmentWritesAreExcludedFromCompleteness() {
    IngestionService service =
        service(
            List.of(
                completed("CTB", "ctb-revenue", IngestionStatus.SUCCESS, T0, T0.plusSeconds(60)),
                completed(
                    "CTB", "ctb-invoice-pdf", IngestionStatus.SUCCESS, T0, T0.plusSeconds(60)),
                completed(
                    "CTB", "ctb-invoice-pdf", IngestionStatus.SUCCESS, T0, T0.plusSeconds(60)),
                completed(
                    "CTB", "ctb-invoice-pdf", IngestionStatus.SUCCESS, T0, T0.plusSeconds(60)),
                completed("CTB", "ctb-invoices", IngestionStatus.FAILED, T0, T0.plusSeconds(60))));

    // Excluding the three PDF writes leaves 1 clean of 2 data-delivery runs -> 50%.
    assertThat(service.health().completenessPercent()).isEqualTo(50);
  }

  private static IngestionRun completed(
      String source, IngestionStatus status, Instant start, Instant end) {
    return completed(source, source + "-connector", status, start, end);
  }

  private static IngestionRun completed(
      String source, String connector, IngestionStatus status, Instant start, Instant end) {
    IngestionRun run = IngestionRun.start(source, connector, null, start);
    run.complete(status, null, null, end);
    return run;
  }

  private static IngestionService service(List<IngestionRun> runs) {
    IngestionRunRepository repository = mock(IngestionRunRepository.class);

    // Derive the aggregates the real repository now computes in SQL, excluding the raw-only PDF
    // enrichment connector that completeness must ignore.
    Map<IngestionStatus, Long> counts =
        runs.stream()
            .filter(r -> !"ctb-invoice-pdf".equals(r.connectorName()))
            .collect(Collectors.groupingBy(IngestionRun::status, Collectors.counting()));
    List<StatusCount> statusCounts =
        counts.entrySet().stream().map(e -> new StatusCount(e.getKey(), e.getValue())).toList();
    when(repository.statusCountsExcludingConnector(any())).thenReturn(statusCounts);

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
        mock(RawRecordRepository.class),
        mock(IngestionStageRepository.class),
        mock(ConnectorRunner.class),
        List.<SourceConnector>of());
  }
}
