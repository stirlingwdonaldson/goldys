package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.SaleItemRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Projects a date's resolved sale-item totals whenever a canonical sale-item fact is recorded. Runs
 * synchronously in the publisher's transaction.
 */
@Component
public class SaleItemProjectionListener {
  private final SaleItemProjector projector;

  public SaleItemProjectionListener(SaleItemProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(SaleItemRecorded event) {
    projector.recompute(event.tradingDate());
  }
}
