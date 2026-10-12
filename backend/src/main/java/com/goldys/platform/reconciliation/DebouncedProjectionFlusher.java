package com.goldys.platform.reconciliation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debounced collector of dates to recompute, shared by the projection listeners.
 *
 * <p>Each date is removed from the set <em>before</em> its recompute and re-added only if the
 * recompute throws, so a single failing date never drops the other pending dates (the earlier
 * clear-then-recompute order lost the whole remainder on the first throw). The failure is logged
 * and the date is retried on the next flush.
 */
final class DebouncedProjectionFlusher {
  private static final Logger log = LoggerFactory.getLogger(DebouncedProjectionFlusher.class);

  private final Set<LocalDate> pendingDates = ConcurrentHashMap.newKeySet();

  void enqueue(LocalDate date) {
    pendingDates.add(date);
  }

  void flush(Consumer<LocalDate> recompute) {
    if (pendingDates.isEmpty()) {
      return;
    }
    for (LocalDate date : new ArrayList<>(pendingDates)) {
      pendingDates.remove(date);
      try {
        recompute.accept(date);
      } catch (RuntimeException e) {
        pendingDates.add(date);
        log.error("Projection recompute failed for {}; re-queued for retry", date, e);
      }
    }
  }
}
