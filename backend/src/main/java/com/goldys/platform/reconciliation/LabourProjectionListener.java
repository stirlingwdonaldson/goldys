package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.LabourRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Projects a date's resolved labour values whenever a canonical labour fact is recorded. */
@Component
public class LabourProjectionListener {
  private final LabourProjector projector;

  public LabourProjectionListener(LabourProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(LabourRecorded event) {
    projector.recompute(event.labourDate());
  }
}
