package com.goldys.platform.ingestion;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On boot, closes any ingestion run left {@code RUNNING} by a previous shutdown. Because the fetch
 * now runs on a background thread, a process exit mid-fetch would otherwise leave a run that no one
 * will ever complete.
 */
@Component
class IngestionStartupRecovery {
  private static final Logger log = LoggerFactory.getLogger(IngestionStartupRecovery.class);
  private static final Clock CLOCK = Clock.systemUTC();

  private final IngestionRunService runs;

  IngestionStartupRecovery(IngestionRunService runs) {
    this.runs = runs;
  }

  @EventListener(ApplicationReadyEvent.class)
  void recover() {
    int recovered = runs.recoverDanglingRuns(CLOCK.instant());
    if (recovered > 0) {
      log.warn(
          "Recovered {} dangling ingestion run(s) left RUNNING by a previous shutdown", recovered);
    }
  }
}
