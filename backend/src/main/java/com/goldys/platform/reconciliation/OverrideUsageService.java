package com.goldys.platform.reconciliation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Manual-override usage for the dashboard: how often staff resolved a conflict by hand.
 *
 * <p>Counts every override action recorded in the trailing week across both reconciliation
 * surfaces. Overrides are append-only, so a superseded row still represents a real staff action and
 * is counted; this is a usage-frequency metric, not a count of currently-active overrides.
 */
@Service
public class OverrideUsageService {
  private static final Clock CLOCK = Clock.systemUTC();
  private static final Duration WINDOW = Duration.ofDays(7);

  private final DailySalesOverrideRepository dailyOverrides;
  private final ProductSalesOverrideRepository productOverrides;

  public OverrideUsageService(
      DailySalesOverrideRepository dailyOverrides,
      ProductSalesOverrideRepository productOverrides) {
    this.dailyOverrides = dailyOverrides;
    this.productOverrides = productOverrides;
  }

  /** Total override actions in the trailing 7 days, with a period label for display. */
  public OverrideUsage usage() {
    Instant since = CLOCK.instant().minus(WINDOW);
    int count =
        (int)
            (dailyOverrides.countByRecordedAtAfter(since)
                + productOverrides.countByRecordedAtAfter(since));
    return new OverrideUsage(count, "last 7 days");
  }
}
