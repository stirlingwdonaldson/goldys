package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.InvoiceLineRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Projects a date's resolved inventory value whenever a canonical invoice line is recorded. */
@Component
public class InventoryProjectionListener {
  private final InventoryProjector projector;

  public InventoryProjectionListener(InventoryProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(InvoiceLineRecorded event) {
    projector.recompute(event.invoiceDate());
  }
}
