package com.goldys.platform.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Refresh after startup projections finish, then hourly; the runner prevents overlap. */
@Component
@ConditionalOnProperty(name = "tanda.sync-enabled", havingValue = "true")
class TandaScheduledPull {
  private final IngestionService ingestion;
  private volatile boolean ready;

  TandaScheduledPull(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @EventListener(ApplicationReadyEvent.class)
  void onReady() {
    ready = true;
    refresh();
  }

  @Scheduled(
      cron = "${tanda.sync-cron:0 15 * * * *}",
      zone = "${app.venue.timezone:Australia/Melbourne}")
  void refresh() {
    if (ready) ingestion.runConnector("TANDA");
  }
}
