package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.DeletedSaleDay;
import com.goldys.platform.semantic.DeletedSaleMetricsQuery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link DeletedSaleMetricsQuery} backed by the resolved deleted-sale projection. */
@Service
public class ResolvedDeletedSaleQuery implements DeletedSaleMetricsQuery {
  private final ResolvedDeletedSaleDayRepository repository;

  public ResolvedDeletedSaleQuery(ResolvedDeletedSaleDayRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<DeletedSaleDay> dailyTotals(LocalDate from, LocalDate to) {
    return repository.findByTradingDateBetweenOrderByTradingDateAsc(from, to).stream()
        .map(
            r ->
                new DeletedSaleDay(
                    r.tradingDate(),
                    r.deletedCount(),
                    r.totalIncTax(),
                    r.totalTax(),
                    r.hasConflict()))
        .toList();
  }
}
