package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.sentry.MonitorConfig;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CtbScheduledPullTest {
  private static final String CRON = "0 0 4 * * *";

  @Test
  @SuppressWarnings("unchecked")
  void delegatesToRunConnectorForCtb() {
    IngestionService ingestion = mock(IngestionService.class);
    CtbScheduledPull pull = new CtbScheduledPull(ingestion, CRON);

    pull.pullCtb();

    verify(ingestion).runConnector(eq("CTB"), any(Consumer.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void swallowsAnUnexpectedSchedulingError() {
    IngestionService ingestion = mock(IngestionService.class);
    doThrow(new IllegalStateException("boom"))
        .when(ingestion)
        .runConnector(eq("CTB"), any(Consumer.class));
    CtbScheduledPull pull = new CtbScheduledPull(ingestion, CRON);

    pull.pullCtb(); // must not throw — a connector failure is already ledgered by the runner
  }

  @Test
  void onlyFailedAndPartialRunsFailTheMonitor() {
    assertThat(CtbScheduledPull.isFailure(IngestionStatus.FAILED)).isTrue();
    assertThat(CtbScheduledPull.isFailure(IngestionStatus.PARTIAL)).isTrue();
    assertThat(CtbScheduledPull.isFailure(IngestionStatus.SUCCESS)).isFalse();
    assertThat(CtbScheduledPull.isFailure(IngestionStatus.NO_NEW_DATA)).isFalse();
    // Skipped because a manual run was already in flight.
    assertThat(CtbScheduledPull.isFailure(null)).isFalse();
  }

  @Test
  void convertsSpringCronToSentryCrontabInVenueTime() {
    MonitorConfig config = CtbScheduledPull.monitorConfig(CRON);

    assertThat(config.getSchedule().getValue()).isEqualTo("0 4 * * *");
    assertThat(config.getTimezone()).isEqualTo("Australia/Melbourne");
  }
}
