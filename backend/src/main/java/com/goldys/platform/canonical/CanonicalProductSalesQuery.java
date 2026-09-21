package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.List;
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

  private ProductSalesView toView(CanonicalProductSales s) {
    return new ProductSalesView(
        s.sourceSystem(), s.tradingDate(), s.productNameKey(), s.quantitySold(), s.amount());
  }
}
