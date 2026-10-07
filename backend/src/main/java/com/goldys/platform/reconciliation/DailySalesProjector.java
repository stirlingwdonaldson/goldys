package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import com.goldys.platform.metrics.OperationalMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the daily-sales read model ({@code resolved_daily_sales} and {@code
 * reconciliation_exception}) from canonical rows, overrides, and rules.
 *
 * <p>Injects repositories rather than the services that trigger it ({@link
 * DailySalesOverrideService}, {@link ResolutionRuleService}) to avoid a Spring bean cycle.
 */
@Service
public class DailySalesProjector {
  private static final String ENTITY_TYPE = "daily_sales";
  private static final String FIELD_KEY = "daily_sales";
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalDailySalesQuery dailySales;
  private final DailySalesOverrideRepository overrides;
  private final ResolutionRuleRepository rules;
  private final ResolvedDailySalesRepository resolved;
  private final ReconciliationExceptionRowRepository exceptions;
  private final OperationalMetrics metrics;

  public DailySalesProjector(
      CanonicalDailySalesQuery dailySales,
      DailySalesOverrideRepository overrides,
      ResolutionRuleRepository rules,
      ResolvedDailySalesRepository resolved,
      ReconciliationExceptionRowRepository exceptions,
      OperationalMetrics metrics) {
    this.dailySales = dailySales;
    this.overrides = overrides;
    this.rules = rules;
    this.resolved = resolved;
    this.exceptions = exceptions;
    this.metrics = metrics;
  }

  /**
   * Rebuild the whole read model from canonical + overrides + rules. Used on rule changes and
   * deploy backfill.
   */
  @Transactional
  public void recomputeAll() {
    Timer.Sample sample = metrics.start();
    try {
      resolved.deleteAllInBatch();
      exceptions.deleteAllInBatch();
      Set<LocalDate> dates = new LinkedHashSet<>();
      for (DailySalesView v : dailySales.currentDailySales()) {
        dates.add(v.tradingDate());
      }
      recompute(dates);
    } finally {
      metrics.stopProjection(sample, ENTITY_TYPE);
    }
  }

  /** Recompute just the given dates (bulk). */
  @Transactional
  public void recompute(LocalDate... dates) {
    recompute(Set.of(dates));
  }

  private void recompute(Collection<LocalDate> dates) {
    if (dates.isEmpty()) {
      return;
    }
    Map<LocalDate, List<SourceTotal>> byDate = new HashMap<>();
    for (DailySalesView v : dailySales.currentDailySalesForDates(dates)) {
      byDate
          .computeIfAbsent(v.tradingDate(), k -> new ArrayList<>())
          .add(
              new SourceTotal(
                  v.sourceSystem(), v.totalSales(), v.gstTotal(), v.netTotal(), v.recordedAt()));
    }
    Map<LocalDate, String> overrideByDate = new HashMap<>();
    for (DailySalesOverride o : overrides.findCurrentByDates(dates)) {
      overrideByDate.put(o.tradingDate(), o.authoritativeSource());
    }
    Optional<ResolutionRule> rule = rules.findCurrent(ENTITY_TYPE, FIELD_KEY);

    Instant now = CLOCK.instant();
    Map<LocalDate, DailySalesResolver.Result> results = new HashMap<>();
    for (LocalDate date : dates) {
      DailySalesResolver.resolve(
              byDate.getOrDefault(date, List.of()),
              Optional.ofNullable(overrideByDate.get(date)),
              rule)
          .ifPresent(r -> results.put(date, r));
    }

    List<ResolvedDailySales> rows = new ArrayList<>();
    for (Map.Entry<LocalDate, DailySalesResolver.Result> e : results.entrySet()) {
      DailySalesResolver.Result r = e.getValue();
      rows.add(
          new ResolvedDailySales(
              e.getKey(),
              r.totalSales(),
              r.net(),
              r.gst(),
              r.resolutionType(),
              r.authoritativeSource(),
              r.hasConflict(),
              now));
    }
    resolved.saveAll(rows);

    reconcileExceptions(results, now);
  }

  private void reconcileExceptions(Map<LocalDate, DailySalesResolver.Result> results, Instant now) {
    List<ReconciliationExceptionRow> existing =
        exceptions.findByEntityTypeAndTradingDateIn(ENTITY_TYPE, results.keySet());
    Map<String, ReconciliationExceptionRow> byKey = new HashMap<>();
    for (ReconciliationExceptionRow e : existing) {
      byKey.put(e.entityKey(), e);
    }

    List<ReconciliationExceptionRow> toInsert = new ArrayList<>();
    List<ReconciliationExceptionRow> toDelete = new ArrayList<>();
    for (Map.Entry<LocalDate, DailySalesResolver.Result> e : results.entrySet()) {
      LocalDate date = e.getKey();
      DailySalesResolver.Result r = e.getValue();
      String key = date.toString();
      ReconciliationExceptionRow existingRow = byKey.remove(key);
      if (r.hasConflict()) {
        if (existingRow == null) {
          toInsert.add(
              new ReconciliationExceptionRow(
                  ENTITY_TYPE, key, FIELD_KEY, date, r.resolutionType(), now));
        } else if (!r.resolutionType().equals(existingRow.status())) {
          existingRow.changeStatus(r.resolutionType());
        }
        // else: unchanged — keep the original detectedAt
      } else if (existingRow != null) {
        toDelete.add(existingRow);
      }
    }
    toDelete.addAll(byKey.values());
    exceptions.deleteAll(toDelete);
    exceptions.saveAll(toInsert);
  }
}
