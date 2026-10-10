package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.DeletedSaleRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Projects a date's resolved deleted-sale totals whenever a canonical deleted-sale fact is
 * recorded. Runs synchronously in the publisher's transaction.
 */
@Component
public class DeletedSaleProjectionListener {
  private final DeletedSaleProjector projector;

  public DeletedSaleProjectionListener(DeletedSaleProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(DeletedSaleRecorded event) {
    projector.recompute(event.tradingDate());
  }
}
