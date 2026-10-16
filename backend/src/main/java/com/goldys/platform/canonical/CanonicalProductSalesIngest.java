package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical per-product sales. */
@Service
public class CanonicalProductSalesIngest {
  private final CanonicalProductSalesService service;

  public CanonicalProductSalesIngest(CanonicalProductSalesService service) {
    this.service = service;
  }

  @org.springframework.transaction.annotation.Transactional
  public void recordAll(java.util.List<ProductSalesInput> inputs) {
    service.recordAll(inputs);
  }

  public void record(ProductSalesInput input) {
    service.record(input);
  }
}
