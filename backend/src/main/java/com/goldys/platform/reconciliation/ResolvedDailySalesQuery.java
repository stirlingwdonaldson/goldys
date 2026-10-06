package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Read-only facade over the resolved daily-sales projection. */
@Service
public class ResolvedDailySalesQuery {
  private final ResolvedDailySalesRepository repository;

  public ResolvedDailySalesQuery(ResolvedDailySalesRepository repository) {
    this.repository = repository;
  }

  public Optional<ResolvedDailySalesView> latest() {
    return repository.findTopByOrderByTradingDateDesc().map(this::toView);
  }

  public List<ResolvedDailySalesView> between(LocalDate from, LocalDate to) {
    return repository.findByTradingDateBetweenOrderByTradingDateAsc(from, to).stream()
        .map(this::toView)
        .toList();
  }

  public long countOpenConflicts() {
    return repository.countByHasConflictTrue();
  }

  private ResolvedDailySalesView toView(ResolvedDailySales r) {
    return new ResolvedDailySalesView(
        r.tradingDate(),
        r.totalSales(),
        r.resolutionType(),
        r.authoritativeSource(),
        r.hasConflict());
  }

  public record ResolvedDailySalesView(
      LocalDate tradingDate,
      BigDecimal totalSales,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict) {}
}
