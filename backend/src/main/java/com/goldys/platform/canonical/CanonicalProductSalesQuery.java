package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Read-only query facade for canonical per-product sales. */
@Service
public class CanonicalProductSalesQuery {
  private final CanonicalProductSalesRepository repository;

  public CanonicalProductSalesQuery(CanonicalProductSalesRepository repository) {
    this.repository = repository;
  }

  public List<ProductSalesView> currentProductSales() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  public List<ProductSalesView> currentProductSalesForDate(LocalDate date) {
    return repository.findCurrentByDate(date).stream().map(this::toView).toList();
  }

  public List<ProductSalesView> currentProductSalesForDateAndProduct(LocalDate date, String key) {
    return repository.findCurrentByDateAndProduct(date, key).stream().map(this::toView).toList();
  }

  public List<ProductSalesView> currentProductSalesForDates(Collection<LocalDate> dates) {
    return repository.findCurrentByDates(dates).stream().map(this::toView).toList();
  }

  /** The distinct, sorted product name keys that currently have canonical product sales. */
  public List<String> distinctProductNameKeys() {
    return repository.findDistinctCurrentProductNameKeys();
  }

  /** Top products by summed amount since {@code since}, descending. */
  public List<ProductSalesTotal> topProductsByAmount(LocalDate since, int limit) {
    return repository.topProductsByAmountSince(since, PageRequest.of(0, limit));
  }

  private ProductSalesView toView(CanonicalProductSales s) {
    return new ProductSalesView(
        s.sourceSystem(),
        s.tradingDate(),
        s.productNameKey(),
        s.quantitySold(),
        s.amount(),
        s.recordedAt());
  }
}
