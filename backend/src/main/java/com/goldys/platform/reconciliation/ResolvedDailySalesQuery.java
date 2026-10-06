package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** {@link SalesMetricsQuery} backed by the resolved daily-sales projection. */
@Service
public class ResolvedDailySalesQuery implements SalesMetricsQuery {
  private final ResolvedDailySalesRepository repository;

  public ResolvedDailySalesQuery(ResolvedDailySalesRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<DailySalesMetric> dailySales(LocalDate from, LocalDate to) {
    return repository.findByTradingDateBetweenOrderByTradingDateAsc(from, to).stream()
        .map(this::toMetric)
        .toList();
  }

  @Override
  public Optional<DailySalesMetric> latestTradingDay() {
    return repository.findTopByOrderByTradingDateDesc().map(this::toMetric);
  }

  @Override
  public long openConflicts() {
    return repository.countByHasConflictTrue();
  }

  private DailySalesMetric toMetric(ResolvedDailySales r) {
    return new DailySalesMetric(
        r.tradingDate(), r.totalSales(), r.authoritativeSource(), r.hasConflict());
  }
}
