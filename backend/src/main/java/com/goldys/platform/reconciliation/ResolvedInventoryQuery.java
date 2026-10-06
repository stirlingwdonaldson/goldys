package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.InventoryMetric;
import com.goldys.platform.semantic.InventoryMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link InventoryMetricsQuery} backed by the resolved inventory projection. */
@Service
public class ResolvedInventoryQuery implements InventoryMetricsQuery {
  private final ResolvedInventoryDayRepository repository;

  public ResolvedInventoryQuery(ResolvedInventoryDayRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<InventoryMetric> dailyInventory(LocalDate from, LocalDate to) {
    return rows(from, to).stream().map(this::toMetric).toList();
  }

  @Override
  public BigDecimal purchases(LocalDate from, LocalDate to) {
    BigDecimal total = BigDecimal.ZERO;
    for (ResolvedInventoryDay r : rows(from, to)) {
      if (r.purchases() != null) {
        total = total.add(r.purchases());
      }
    }
    return total;
  }

  @Override
  public BigDecimal wastage(LocalDate from, LocalDate to) {
    BigDecimal total = BigDecimal.ZERO;
    boolean any = false;
    for (ResolvedInventoryDay r : rows(from, to)) {
      if (r.wastage() != null) {
        total = total.add(r.wastage());
        any = true;
      }
    }
    return any ? total : null;
  }

  private List<ResolvedInventoryDay> rows(LocalDate from, LocalDate to) {
    return repository.findByTradingDateBetweenOrderByTradingDateAsc(from, to);
  }

  private InventoryMetric toMetric(ResolvedInventoryDay r) {
    return new InventoryMetric(
        r.tradingDate(),
        r.purchases(),
        r.wastage(),
        r.stockOnHand(),
        r.authoritativeSource(),
        r.hasConflict());
  }
}
