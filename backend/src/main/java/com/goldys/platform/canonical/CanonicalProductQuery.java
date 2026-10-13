package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical Lightspeed products, so modules outside this package never
 * touch the package-private entity or repository directly.
 */
@Service
public class CanonicalProductQuery {
  private final CanonicalProductRepository repository;

  public CanonicalProductQuery(CanonicalProductRepository repository) {
    this.repository = repository;
  }

  /** All current products, mapped to views. */
  public List<ProductView> currentProducts() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  private ProductView toView(CanonicalProduct p) {
    return new ProductView(
        p.productId(),
        p.productName(),
        p.category(),
        p.sellPriceIncTax(),
        p.isModifier(),
        p.reportingGroup());
  }
}
