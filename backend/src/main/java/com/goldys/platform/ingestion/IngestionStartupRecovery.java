package com.goldys.platform.ingestion;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On boot, closes any ingestion run left {@code RUNNING} by a previous shutdown. Because the fetch
 * now runs on a background thread, a process exit mid-fetch would otherwise leave a run that no one
 * will ever complete.
 *
 * <p>Listens to {@link ApplicationStartedEvent}, which fires after the context is refreshed but
 * <em>before</em> any {@code ApplicationRunner}/{@code CommandLineRunner} runs. Startup runners
 * (e.g. {@code CtbDetailBackfillRunner}) begin their asynchronous fetches in {@code run(...)}, so
 * on {@link ApplicationReadyEvent} (the later event) this recovery would see their freshly-started
 * {@code RUNNING} rows and wrongly close them as dangling. Recovering on the earlier event means
 * only runs left by a genuinely previous process are touched.
 */
@Component
class IngestionStartupRecovery {
  private static final Logger log = LoggerFactory.getLogger(IngestionStartupRecovery.class);
  private static final Clock CLOCK = Clock.systemUTC();

  private final IngestionRunService runs;

  IngestionStartupRecovery(IngestionRunService runs) {
    this.runs = runs;
  }

  @EventListener(ApplicationStartedEvent.class)
  void recover() {
    int recovered = runs.recoverDanglingRuns(CLOCK.instant());
    if (recovered > 0) {
      log.warn(
          "Recovered {} dangling ingestion run(s) left RUNNING by a previous shutdown", recovered);
    }
  }
}
