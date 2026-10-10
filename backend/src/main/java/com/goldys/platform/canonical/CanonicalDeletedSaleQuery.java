package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical deleted sales, so modules outside this package never touch
 * the package-private entity or repository directly.
 */
@Service
public class CanonicalDeletedSaleQuery {
  private final CanonicalDeletedSaleRepository repository;

  public CanonicalDeletedSaleQuery(CanonicalDeletedSaleRepository repository) {
    this.repository = repository;
  }

  /** All current deleted sales, mapped to views. */
  public List<DeletedSaleView> currentDeletedSales() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  /** Current deleted sales whose trading date falls in {@code dates}. */
  public List<DeletedSaleView> currentDeletedSalesForDates(Collection<LocalDate> dates) {
    return currentDeletedSales().stream().filter(v -> dates.contains(v.tradingDate())).toList();
  }

  private DeletedSaleView toView(CanonicalDeletedSale d) {
    return new DeletedSaleView(d.tradingDate(), d.totalIncTax(), d.totalTax());
  }
}
