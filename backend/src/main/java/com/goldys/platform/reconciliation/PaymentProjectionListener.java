package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.PaymentRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Projects a date's resolved payment totals whenever a canonical payment fact is recorded. Runs
 * synchronously in the publisher's transaction, so canonical and projection stay atomic.
 */
@Component
public class PaymentProjectionListener {
  private final PaymentProjector projector;

  public PaymentProjectionListener(PaymentProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(PaymentRecorded event) {
    projector.recompute(event.tradingDate());
  }
}
