package com.goldys.platform.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled pull for the CTB connector — the only pull connector on a schedule today. The "Run now"
 * action ({@code POST /api/connectors/CTB/run}) remains available regardless of this task.
 *
 * <p>{@link IngestionService#runConnector} refuses to stack a second run while one is already in
 * flight, so a scheduled tick that overlaps a manual run is a no-op rather than a double fetch.
 *
 * <p>Disabled with {@code app.scheduling.ctb.enabled=false}; the cron is overridden with {@code
 * app.scheduling.ctb.cron} and is evaluated in venue time ({@code Australia/Melbourne}).
 */
@Component
@ConditionalOnProperty(
    name = "app.scheduling.ctb.enabled",
    havingValue = "true",
    matchIfMissing = true)
class CtbScheduledPull {
  private static final Logger log = LoggerFactory.getLogger(CtbScheduledPull.class);

  private final IngestionService ingestion;

  CtbScheduledPull(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @Scheduled(cron = "${app.scheduling.ctb.cron:0 0 4 * * *}", zone = "Australia/Melbourne")
  void pullCtb() {
    try {
      ingestion.runConnector("CTB");
    } catch (RuntimeException e) {
      // A connector failure is already recorded in the ingestion ledger and surfaced on the
      // connectors screen; this catch only keeps an unexpected scheduling error from taking down
      // the task thread.
      log.warn("Scheduled CTB pull failed", e);
    }
  }
}
