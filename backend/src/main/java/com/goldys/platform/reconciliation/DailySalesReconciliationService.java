package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Reconciles the per-source daily sales totals keyed by trading date.
 *
 * <p>Conflict detection compares totals with a one-cent rounding rule; a missing source is surfaced
 * explicitly rather than silently omitted.
 */
@Service
public class DailySalesReconciliationService {
  private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

  private final CanonicalDailySalesQuery dailySales;
  private final DailySalesOverrideRepository overrides;

  public DailySalesReconciliationService(
      CanonicalDailySalesQuery dailySales, DailySalesOverrideRepository overrides) {
    this.dailySales = dailySales;
    this.overrides = overrides;
  }

  public List<DailySalesConflict> conflicts() {
    Map<LocalDate, List<SourceTotal>> byDate = new LinkedHashMap<>();
    for (DailySalesView view : dailySales.currentDailySales()) {
      byDate
          .computeIfAbsent(view.tradingDate(), k -> new ArrayList<>())
          .add(new SourceTotal(view.sourceSystem(), view.totalSales()));
    }

    List<DailySalesConflict> out = new ArrayList<>();
    for (Map.Entry<LocalDate, List<SourceTotal>> entry : byDate.entrySet()) {
      String status = classify(entry.getValue());
      if (!"agreed".equals(status)) {
        out.add(new DailySalesConflict(entry.getKey(), entry.getValue(), status));
      }
    }
    out.sort(Comparator.comparing(DailySalesConflict::tradingDate));
    return out;
  }

  public Optional<DailySalesResolved> resolved(LocalDate date) {
    List<DailySalesView> sales = dailySales.currentDailySalesForDate(date);

    Optional<DailySalesOverride> override = overrides.findCurrent(date);
    if (override.isPresent()) {
      String source = override.get().authoritativeSource();
      return sales.stream()
          .filter(s -> s.sourceSystem().equals(source))
          .findFirst()
          .map(s -> new DailySalesResolved(date, s.totalSales(), "override:" + source));
    }

    if (sales.isEmpty()) {
      return Optional.empty();
    }
    List<SourceTotal> sources =
        sales.stream().map(s -> new SourceTotal(s.sourceSystem(), s.totalSales())).toList();
    if ("agreed".equals(classify(sources))) {
      return Optional.of(new DailySalesResolved(date, sales.get(0).totalSales(), "agreed"));
    }
    return Optional.of(new DailySalesResolved(date, null, null));
  }

  /** Classifies a set of source totals: "missing" (<2 sources), "agreed", or "conflict". */
  static String classify(List<SourceTotal> sources) {
    if (sources.size() < 2) {
      return "missing";
    }
    BigDecimal first = sources.get(0).totalSales();
    boolean allAgree = sources.stream().allMatch(s -> withinCent(first, s.totalSales()));
    return allAgree ? "agreed" : "conflict";
  }

  static boolean withinCent(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.subtract(b).abs().compareTo(ONE_CENT) <= 0;
  }
}
