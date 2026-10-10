package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical sale items, so modules outside this package never touch the
 * package-private entity or repository directly.
 */
@Service
public class CanonicalSaleItemQuery {
  private final CanonicalSaleItemRepository repository;

  public CanonicalSaleItemQuery(CanonicalSaleItemRepository repository) {
    this.repository = repository;
  }

  /** All current sale items, mapped to views. */
  public List<SaleItemView> currentSaleItems() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  /** Current sale items whose trading date falls in {@code dates}. */
  public List<SaleItemView> currentSaleItemsForDates(Collection<LocalDate> dates) {
    return currentSaleItems().stream().filter(v -> dates.contains(v.tradingDate())).toList();
  }

  private SaleItemView toView(CanonicalSaleItem s) {
    return new SaleItemView(s.tradingDate(), s.categoryName(), s.quantitySold(), s.amount());
  }
}
