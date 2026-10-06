package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.ReservationRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Projects a date's resolved reservation counts whenever a canonical reservation fact is recorded.
 * Runs synchronously in the publisher's transaction, so canonical and projection stay atomic.
 */
@Component
public class ReservationProjectionListener {
  private final ReservationProjector projector;

  public ReservationProjectionListener(ReservationProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(ReservationRecorded event) {
    projector.recompute(event.tradingDate());
  }
}
