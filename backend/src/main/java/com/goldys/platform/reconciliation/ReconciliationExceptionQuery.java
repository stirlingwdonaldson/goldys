package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Read-only facade over the daily-sales exception projection, joined back to canonical for the
 * per-source values shown in the exceptions UI.
 */
@Service
public class ReconciliationExceptionQuery {
  private static final String ENTITY_TYPE = "daily_sales";

  private final ReconciliationExceptionRowRepository exceptions;
  private final CanonicalDailySalesQuery dailySales;

  public ReconciliationExceptionQuery(
      ReconciliationExceptionRowRepository exceptions, CanonicalDailySalesQuery dailySales) {
    this.exceptions = exceptions;
    this.dailySales = dailySales;
  }

  public List<DailyException> listDaily() {
    List<ReconciliationExceptionRow> rows =
        exceptions.findByEntityTypeOrderByTradingDateDesc(ENTITY_TYPE);
    if (rows.isEmpty()) {
      return List.of();
    }
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (ReconciliationExceptionRow row : rows) {
      dates.add(row.tradingDate());
    }
    Map<LocalDate, List<SourceTotal>> byDate = new HashMap<>();
    for (DailySalesView v : dailySales.currentDailySalesForDates(dates)) {
      byDate
          .computeIfAbsent(v.tradingDate(), k -> new ArrayList<>())
          .add(
              new SourceTotal(
                  v.sourceSystem(), v.totalSales(), v.gstTotal(), v.netTotal(), v.recordedAt()));
    }
    List<DailyException> out = new ArrayList<>();
    for (ReconciliationExceptionRow row : rows) {
      out.add(
          new DailyException(
              row.tradingDate(), row.status(), byDate.getOrDefault(row.tradingDate(), List.of())));
    }
    return out;
  }

  public record DailyException(LocalDate tradingDate, String status, List<SourceTotal> sources) {}
}
