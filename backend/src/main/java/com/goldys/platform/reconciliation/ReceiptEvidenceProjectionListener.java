package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.ReceiptEvidenceRecorded;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReceiptEvidenceProjectionListener {
  private final ReceiptEvidenceProjector projector;

  public ReceiptEvidenceProjectionListener(ReceiptEvidenceProjector projector) {
    this.projector = projector;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ReceiptEvidenceRecorded event) {
    projector.recomputeAll();
  }
}
