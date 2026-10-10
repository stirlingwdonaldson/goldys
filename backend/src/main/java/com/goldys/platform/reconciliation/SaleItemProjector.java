package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalSaleItemQuery;
import com.goldys.platform.canonical.SaleItemView;
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
 * Maintains the sale-item read model ({@code resolved_sale_item_day}) from canonical sale items.
 * Single-source (Lightspeed), so rows are tagged {@code single}. Aggregates quantity and amount by
 * day × category.
 */
@Service
public class SaleItemProjector {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalSaleItemQuery saleItems;
  private final ResolvedSaleItemDayRepository resolved;

  public SaleItemProjector(
      CanonicalSaleItemQuery saleItems, ResolvedSaleItemDayRepository resolved) {
    this.saleItems = saleItems;
    this.resolved = resolved;
  }

  /** Rebuild the whole read model from canonical. */
  @Transactional
  public void recomputeAll() {
    resolved.deleteAllInBatch();
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (SaleItemView v : saleItems.currentSaleItems()) {
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

    Map<Key, Mutable> buckets = new HashMap<>();
    for (SaleItemView v : saleItems.currentSaleItemsForDates(dates)) {
      Key key = new Key(v.tradingDate(), nz(v.categoryName()));
      buckets.computeIfAbsent(key, k -> new Mutable()).add(v);
    }

    Instant now = CLOCK.instant();
    List<ResolvedSaleItemDay> rows = new ArrayList<>();
    for (Map.Entry<Key, Mutable> e : buckets.entrySet()) {
      Key key = e.getKey();
      Mutable m = e.getValue();
      rows.add(
          new ResolvedSaleItemDay(
              key.date(),
              key.category(),
              m.quantity,
              m.amount,
              "single",
              "LIGHTSPEED",
              false,
              now));
    }
    resolved.saveAll(rows);
  }

  private static String nz(String v) {
    return v == null || v.isBlank() ? "Uncategorised" : v;
  }

  private record Key(LocalDate date, String category) {}

  private static final class Mutable {
    BigDecimal quantity = BigDecimal.ZERO;
    BigDecimal amount = BigDecimal.ZERO;

    void add(SaleItemView v) {
      quantity =
          quantity.add(
              v.quantitySold() == null ? BigDecimal.ZERO : BigDecimal.valueOf(v.quantitySold()));
      amount = amount.add(v.amount() == null ? BigDecimal.ZERO : v.amount());
    }
  }
}
