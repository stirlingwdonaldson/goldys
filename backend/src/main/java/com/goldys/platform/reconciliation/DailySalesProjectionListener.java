package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.DailySalesRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Projects a date's resolved value whenever a canonical daily-sales fact is recorded. Runs
 * synchronously in the publisher's transaction, so canonical and projection stay atomic. */
@Component
public class DailySalesProjectionListener {
  private final DailySalesProjector projector;

  public DailySalesProjectionListener(DailySalesProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(DailySalesRecorded event) {
    projector.recompute(event.tradingDate());
  }
}
