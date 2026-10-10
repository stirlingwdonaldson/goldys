package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalDeletedSaleQuery;
import com.goldys.platform.canonical.DeletedSaleView;
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
 * Maintains the deleted-sales read model ({@code resolved_deleted_sale_day}) from canonical deleted
 * sales. Single-source (Lightspeed), so rows are tagged {@code single}.
 */
@Service
public class DeletedSaleProjector {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalDeletedSaleQuery deletedSales;
  private final ResolvedDeletedSaleDayRepository resolved;

  public DeletedSaleProjector(
      CanonicalDeletedSaleQuery deletedSales, ResolvedDeletedSaleDayRepository resolved) {
    this.deletedSales = deletedSales;
    this.resolved = resolved;
  }

  /** Rebuild the whole read model from canonical. */
  @Transactional
  public void recomputeAll() {
    resolved.deleteAllInBatch();
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (DeletedSaleView v : deletedSales.currentDeletedSales()) {
      dates.add(v.tradingDate());
    }
    project(dates);
  }

  /** Recompute just the given dates. */
  @Transactional
  public void recompute(LocalDate... dates) {
    project(Set.of(dates));
  }

  private void project(Set<LocalDate> dates) {
    if (dates.isEmpty()) {
      return;
    }
    resolved.deleteByTradingDateIn(dates);

    Map<LocalDate, Mutable> buckets = new HashMap<>();
    for (DeletedSaleView v : deletedSales.currentDeletedSalesForDates(dates)) {
      buckets.computeIfAbsent(v.tradingDate(), k -> new Mutable()).add(v);
    }

    Instant now = CLOCK.instant();
    List<ResolvedDeletedSaleDay> rows = new ArrayList<>();
    for (Map.Entry<LocalDate, Mutable> e : buckets.entrySet()) {
      Mutable m = e.getValue();
      rows.add(
          new ResolvedDeletedSaleDay(
              e.getKey(), m.count, m.totalIncTax, m.totalTax, "single", "LIGHTSPEED", false, now));
    }
    resolved.saveAll(rows);
  }

  private static final class Mutable {
    long count = 0;
    BigDecimal totalIncTax = BigDecimal.ZERO;
    BigDecimal totalTax = BigDecimal.ZERO;

    void add(DeletedSaleView v) {
      count++;
      totalIncTax = totalIncTax.add(nz(v.totalIncTax()));
      totalTax = totalTax.add(nz(v.totalTax()));
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
