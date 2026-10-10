package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.SaleItemRecorded;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Projects a date's resolved sale-item totals whenever a canonical sale-item fact is recorded.
 *
 * <p>Recompute is debounced: bulk ingestion records thousands of facts in a burst, so recomputing
 * per fact is quadratic and OOMs the heap. Instead the affected dates are collected and each is
 * recomputed once, shortly after the burst settles.
 */
@Component
public class SaleItemProjectionListener {
  private final SaleItemProjector projector;
  private final Set<LocalDate> pendingDates = ConcurrentHashMap.newKeySet();

  public SaleItemProjectionListener(SaleItemProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(SaleItemRecorded event) {
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
