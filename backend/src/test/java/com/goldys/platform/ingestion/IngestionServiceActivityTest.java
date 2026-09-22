package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestionServiceActivityTest {

  private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

  @Test
  void zeroFillsEveryDayInTheWindow() {
    List<IngestionActivityPoint> points = IngestionService.activityWindow(List.of(), NOW, 14);

    assertThat(points).hasSize(14);
    assertThat(points)
        .extracting(IngestionActivityPoint::date)
        .startsWith("2026-09-09")
        .endsWith("2026-09-22");
    assertThat(points)
        .allSatisfy(
            p -> {
              assertThat(p.clean()).isZero();
              assertThat(p.failed()).isZero();
            });
  }

  @Test
  void countsSuccessAndNoNewDataAsClean() {
    List<IngestionRun> runs =
        List.of(
            completed("CTB", IngestionStatus.SUCCESS, "2026-09-15T09:00:00Z"),
            completed("LIGHTSPEED", IngestionStatus.NO_NEW_DATA, "2026-09-15T10:00:00Z"));

    List<IngestionActivityPoint> points = IngestionService.activityWindow(runs, NOW, 14);

    assertThat(pointAt(points, "2026-09-15"))
        .isEqualTo(new IngestionActivityPoint("2026-09-15", 2, 0));
  }

  @Test
  void countsFailedAndPartialAsFailed() {
    List<IngestionRun> runs =
        List.of(
            completed("CTB", IngestionStatus.FAILED, "2026-09-15T09:00:00Z"),
            completed("LIGHTSPEED", IngestionStatus.PARTIAL, "2026-09-15T10:00:00Z"));

    List<IngestionActivityPoint> points = IngestionService.activityWindow(runs, NOW, 14);

    assertThat(pointAt(points, "2026-09-15"))
        .isEqualTo(new IngestionActivityPoint("2026-09-15", 0, 2));
  }

  @Test
  void ignoresRunningRuns() {
    IngestionRun dangling =
        IngestionRun.start("CTB", "ctb-export", null, Instant.parse("2026-09-15T09:00:00Z"));
    List<IngestionRun> runs =
        List.of(completed("CTB", IngestionStatus.SUCCESS, "2026-09-15T10:00:00Z"), dangling);

    List<IngestionActivityPoint> points = IngestionService.activityWindow(runs, NOW, 14);

    assertThat(pointAt(points, "2026-09-15"))
        .isEqualTo(new IngestionActivityPoint("2026-09-15", 1, 0));
  }

  @Test
  void ignoresRunsOlderThanTheWindow() {
    List<IngestionRun> runs =
        List.of(completed("CTB", IngestionStatus.SUCCESS, "2026-09-01T09:00:00Z"));

    List<IngestionActivityPoint> points = IngestionService.activityWindow(runs, NOW, 14);

    assertThat(points)
        .allSatisfy(
            p -> {
              assertThat(p.clean()).isZero();
              assertThat(p.failed()).isZero();
            });
  }

  @Test
  void returnsPointsInChronologicalOrder() {
    List<IngestionRun> runs =
        List.of(
            completed("CTB", IngestionStatus.FAILED, "2026-09-22T08:00:00Z"),
            completed("CTB", IngestionStatus.SUCCESS, "2026-09-09T08:00:00Z"));

    List<IngestionActivityPoint> points = IngestionService.activityWindow(runs, NOW, 14);

    assertThat(points)
        .extracting(IngestionActivityPoint::date)
        .containsExactly(
            "2026-09-09",
            "2026-09-10",
            "2026-09-11",
            "2026-09-12",
            "2026-09-13",
            "2026-09-14",
            "2026-09-15",
            "2026-09-16",
            "2026-09-17",
            "2026-09-18",
            "2026-09-19",
            "2026-09-20",
            "2026-09-21",
            "2026-09-22");
  }

  private static IngestionActivityPoint pointAt(List<IngestionActivityPoint> points, String date) {
    return points.stream().filter(p -> p.date().equals(date)).findFirst().orElseThrow();
  }

  private static IngestionRun completed(String source, IngestionStatus status, String startedAt) {
    IngestionRun run =
        IngestionRun.start(source, source + "-connector", null, Instant.parse(startedAt));
    run.complete(status, null, null, Instant.parse(startedAt).plusSeconds(30));
    return run;
  }
}
