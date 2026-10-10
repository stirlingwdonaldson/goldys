package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.PaymentRecorded;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Projects a date's resolved payment totals whenever a canonical payment fact is recorded.
 *
 * <p>Recompute is debounced: bulk ingestion records thousands of facts in a burst, so recomputing
 * per fact is quadratic and OOMs the heap. Instead the affected dates are collected and each is
 * recomputed once, shortly after the burst settles.
 */
@Component
public class PaymentProjectionListener {
  private final PaymentProjector projector;
  private final Set<LocalDate> pendingDates = ConcurrentHashMap.newKeySet();

  public PaymentProjectionListener(PaymentProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(PaymentRecorded event) {
    pendingDates.add(event.tradingDate());
  }

  @Scheduled(fixedDelay = 2000)
  public void flushPendingDates() {
    if (pendingDates.isEmpty()) {
      return;
    }
    List<LocalDate> dates = new ArrayList<>(pendingDates);
    pendingDates.clear();
    for (LocalDate date : dates) {
      projector.recompute(date);
    }
  }
}
