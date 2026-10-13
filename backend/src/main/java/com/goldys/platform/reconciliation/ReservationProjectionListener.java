package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.ReservationRecorded;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Projects a date's resolved reservation counts whenever a canonical reservation fact is recorded.
 *
 * <p>Recompute is debounced, exactly like {@link SaleItemProjectionListener}: bulk ingestion
 * records tens of thousands of facts in a burst, so recomputing per fact is quadratic. Instead the
 * affected dates are collected and each is recomputed once, shortly after the burst settles.
 *
 * <p>The event is handled {@link TransactionPhase#AFTER_COMMIT} so a flush never recomputes before
 * the writer's transaction has committed; {@code fallbackExecution} keeps a non-transactional
 * publish working synchronously.
 */
@Component
public class ReservationProjectionListener {
  private final ReservationProjector projector;
  private final DebouncedProjectionFlusher flusher = new DebouncedProjectionFlusher();

  public ReservationProjectionListener(ReservationProjector projector) {
    this.projector = projector;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(ReservationRecorded event) {
    flusher.enqueue(event.tradingDate());
  }

  @Scheduled(fixedDelay = 2000)
  public void flushPendingDates() {
    flusher.flush(projector::recompute);
  }
}
