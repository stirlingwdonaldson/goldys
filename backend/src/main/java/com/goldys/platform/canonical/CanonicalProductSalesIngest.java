package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical per-product sales. */
@Service
public class CanonicalProductSalesIngest {
  private final CanonicalProductSalesService service;

  public CanonicalProductSalesIngest(CanonicalProductSalesService service) {
    this.service = service;
  }

  public void record(ProductSalesInput input) {
    service.record(input);
  }
}
