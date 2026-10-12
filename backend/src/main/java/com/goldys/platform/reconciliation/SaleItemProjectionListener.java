package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.SaleItemRecorded;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Projects a date's resolved sale-item totals whenever a canonical sale-item fact is recorded.
 *
 * <p>Recompute is debounced: bulk ingestion records thousands of facts in a burst, so recomputing
 * per fact is quadratic and OOMs the heap. Instead the affected dates are collected and each is
 * recomputed once, shortly after the burst settles.
 *
 * <p>The event is handled {@link TransactionPhase#AFTER_COMMIT} so a flush never recomputes before
 * the writer's transaction has committed (a flush that read pre-commit state would never retry);
 * {@code fallbackExecution} keeps a non-transactional publish working synchronously.
 */
@Component
public class SaleItemProjectionListener {
  private final SaleItemProjector projector;
  private final DebouncedProjectionFlusher flusher = new DebouncedProjectionFlusher();

  public SaleItemProjectionListener(SaleItemProjector projector) {
    this.projector = projector;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(SaleItemRecorded event) {
    flusher.enqueue(event.tradingDate());
  }

  @Scheduled(fixedDelay = 2000)
  public void flushPendingDates() {
    flusher.flush(projector::recompute);
  }
}
