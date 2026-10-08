package com.goldys.platform.ingestion;

import io.sentry.CheckIn;
import io.sentry.CheckInStatus;
import io.sentry.MonitorConfig;
import io.sentry.MonitorSchedule;
import io.sentry.Sentry;
import io.sentry.protocol.SentryId;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>Each tick reports to a Sentry cron monitor ({@value #MONITOR_SLUG}): IN_PROGRESS when it
 * starts, then OK or ERROR when the <em>asynchronous</em> run finishes. That is what lets Sentry
 * alert on a pull that failed, ran too long, or never ran at all, which the ingestion ledger cannot
 * show because a run that never started writes nothing. The monitor is created/updated from the
 * config sent with each check-in, so it needs no manual setup. All of this is a no-op without a
 * DSN.
 */
@Component
@ConditionalOnProperty(
    name = "app.scheduling.ctb.enabled",
    havingValue = "true",
    matchIfMissing = true)
class CtbScheduledPull {
  static final String MONITOR_SLUG = "ctb-scheduled-pull";
  private static final String VENUE_ZONE = "Australia/Melbourne";
  private static final Logger log = LoggerFactory.getLogger(CtbScheduledPull.class);

  private final IngestionService ingestion;
  private final MonitorConfig monitorConfig;

  CtbScheduledPull(
      IngestionService ingestion,
      @Value("${app.scheduling.ctb.cron:0 0 4 * * *}") String springCron) {
    this.ingestion = ingestion;
    this.monitorConfig = monitorConfig(springCron);
  }

  @Scheduled(cron = "${app.scheduling.ctb.cron:0 0 4 * * *}", zone = VENUE_ZONE)
  void pullCtb() {
    SentryId checkInId = checkIn(null, CheckInStatus.IN_PROGRESS);
    try {
      ingestion.runConnector(
          "CTB",
          status -> checkIn(checkInId, isFailure(status) ? CheckInStatus.ERROR : CheckInStatus.OK));
    } catch (RuntimeException e) {
      // A connector failure is already recorded in the ingestion ledger and surfaced on the
      // connectors screen; this catch only keeps an unexpected scheduling error from taking down
      // the task thread. The run never started, so the monitor is told it failed.
      checkIn(checkInId, CheckInStatus.ERROR);
      log.warn("Scheduled CTB pull failed", e);
    }
  }

  /**
   * FAILED and PARTIAL are failures. SUCCESS and NO_NEW_DATA are fine, and so is {@code null}: the
   * tick was skipped because a manual run was already fetching the same window.
   */
  static boolean isFailure(IngestionStatus status) {
    return status == IngestionStatus.FAILED || status == IngestionStatus.PARTIAL;
  }

  private SentryId checkIn(SentryId existing, CheckInStatus status) {
    try {
      CheckIn checkIn =
          existing == null
              ? new CheckIn(MONITOR_SLUG, status)
              : new CheckIn(existing, MONITOR_SLUG, status);
      checkIn.setMonitorConfig(monitorConfig);
      return Sentry.captureCheckIn(checkIn);
    } catch (RuntimeException e) {
      log.debug("Sentry check-in failed", e);
      return existing;
    }
  }

  /**
   * Spring cron has a leading seconds field ({@code 0 0 4 * * *}); Sentry's crontab is the classic
   * five-field form ({@code 0 4 * * *}), so the seconds field is dropped.
   */
  static MonitorConfig monitorConfig(String springCron) {
    String[] fields = springCron.trim().split("\\s+");
    String crontab =
        fields.length == 6 ? String.join(" ", Arrays.copyOfRange(fields, 1, 6)) : springCron;
    MonitorConfig config = new MonitorConfig(MonitorSchedule.crontab(crontab));
    config.setTimezone(VENUE_ZONE);
    config.setCheckinMargin(10L); // minutes late before "missed"
    config.setMaxRuntime(30L); // minutes before "timed out"
    return config;
  }
}
