package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalLabourQuery;
import com.goldys.platform.canonical.LabourView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the labour read model ({@code resolved_labour_day}) from canonical labour facts and
 * manual overrides.
 *
 * <p>Labour is single-source (Deputy), so there is no multi-source conflict; the projector sums
 * hours/cost per day × department and applies any manual override. Cost is null-propagating: if any
 * entry in a day/department has an unknown cost, the day's cost is unknown.
 */
@Service
public class LabourProjector {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalLabourQuery labour;
  private final LabourOverrideRepository overrides;
  private final ResolvedLabourDayRepository resolved;

  public LabourProjector(
      CanonicalLabourQuery labour,
      LabourOverrideRepository overrides,
      ResolvedLabourDayRepository resolved) {
    this.labour = labour;
    this.overrides = overrides;
    this.resolved = resolved;
  }

  @Transactional
  public void recomputeAll() {
    resolved.deleteAllInBatch();
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (LabourView v : labour.currentLabour()) {
      dates.add(v.labourDate());
    }
    project(dates);
  }

  @Transactional
  public void recompute(LocalDate... dates) {
    project(Set.of(dates));
  }

  private void project(Set<LocalDate> dates) {
    if (dates.isEmpty()) {
      return;
    }
    resolved.deleteByTradingDateIn(dates);

    Map<Key, Mutable> buckets = new HashMap<>();
    for (LabourView v : labour.currentLabourForDates(dates)) {
      buckets.computeIfAbsent(new Key(v.labourDate(), v.department()), k -> new Mutable()).add(v);
    }

    Map<Key, BigDecimal> overrideByKey = new HashMap<>();
    for (LabourOverride o : overrides.findAllCurrent()) {
      if (o.overriddenActualHours() != null) {
        overrideByKey.put(new Key(o.tradingDate(), o.department()), o.overriddenActualHours());
      }
    }

    Instant now = CLOCK.instant();
    List<ResolvedLabourDay> rows = new ArrayList<>();
    for (Map.Entry<Key, Mutable> e : buckets.entrySet()) {
      Key key = e.getKey();
      Mutable m = e.getValue();
      BigDecimal overridden = overrideByKey.get(key);
      if (overridden != null) {
        rows.add(
            new ResolvedLabourDay(
                key.date(),
                key.department(),
                m.scheduledHours,
                overridden,
                m.scheduledCost(),
                m.actualCost(),
                "override",
                "manual",
                false,
                now));
      } else {
        rows.add(
            new ResolvedLabourDay(
                key.date(),
                key.department(),
                m.scheduledHours,
                m.actualHours,
                m.scheduledCost(),
                m.actualCost(),
                "single",
                "DEPUTY",
                false,
                now));
      }
    }
    resolved.saveAll(rows);
  }

  private record Key(LocalDate date, String department) {}

  private static final class Mutable {
    BigDecimal scheduledHours = BigDecimal.ZERO;
    BigDecimal actualHours = BigDecimal.ZERO;
    BigDecimal scheduledCost = BigDecimal.ZERO;
    BigDecimal actualCost = BigDecimal.ZERO;
    boolean scheduledCostUnknown;
    boolean actualCostUnknown;

    void add(LabourView v) {
      scheduledHours = scheduledHours.add(v.scheduledHours());
      actualHours = actualHours.add(v.actualHours());
      scheduledCostUnknown |= v.scheduledCost() == null;
      if (v.scheduledCost() != null) {
        scheduledCost = scheduledCost.add(v.scheduledCost());
      }
      actualCostUnknown |= v.actualCost() == null;
      if (v.actualCost() != null) {
        actualCost = actualCost.add(v.actualCost());
      }
    }

    BigDecimal scheduledCost() {
      return scheduledCostUnknown ? null : scheduledCost;
    }

    BigDecimal actualCost() {
      return actualCostUnknown ? null : actualCost;
    }
  }
}
