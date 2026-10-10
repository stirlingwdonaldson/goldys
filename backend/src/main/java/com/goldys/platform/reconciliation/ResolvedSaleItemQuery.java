package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.SaleItemMetricsQuery;
import com.goldys.platform.semantic.SaleItemMix;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link SaleItemMetricsQuery} backed by the resolved sale-item projection. */
@Service
public class ResolvedSaleItemQuery implements SaleItemMetricsQuery {
  private final ResolvedSaleItemDayRepository repository;

  public ResolvedSaleItemQuery(ResolvedSaleItemDayRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<SaleItemMix> dailyByCategory(LocalDate from, LocalDate to) {
    return repository
        .findByTradingDateBetweenOrderByTradingDateAscCategoryNameAsc(from, to)
        .stream()
        .map(
            r ->
                new SaleItemMix(
                    r.tradingDate(), r.categoryName(), r.quantity(), r.amount(), r.hasConflict()))
        .toList();
  }
}
