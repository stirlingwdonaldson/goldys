package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical daily sales, so modules outside this package never touch the
 * package-private entity or repository directly.
 */
@Service
public class CanonicalDailySalesQuery {
  private final CanonicalDailySalesRepository repository;

  public CanonicalDailySalesQuery(CanonicalDailySalesRepository repository) {
    this.repository = repository;
  }

  public List<DailySalesView> currentDailySales() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  public List<DailySalesView> currentDailySalesForDate(LocalDate date) {
    return repository.findCurrentByDate(date).stream().map(this::toView).toList();
  }

  public List<DailySalesView> currentDailySalesForDates(Collection<LocalDate> dates) {
    return repository.findCurrentByDates(dates).stream().map(this::toView).toList();
  }

  public Optional<LocalDate> latestTradingDate() {
    return repository.findLatestTradingDate();
  }

  private DailySalesView toView(CanonicalDailySales s) {
    return new DailySalesView(
        s.sourceSystem(),
        s.tradingDate(),
        s.totalSales(),
        s.gstTotal(),
        s.netTotal(),
        s.recordedAt());
  }
}
