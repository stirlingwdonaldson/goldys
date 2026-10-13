package com.goldys.platform.canonical;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Public write facade for canonical Lightspeed products, so connectors can record parsed facts. */
@Service
public class ProductIngest {
  private final CanonicalProductService service;

  public ProductIngest(CanonicalProductService service) {
    this.service = service;
  }

  public void recordAll(List<ProductInput> inputs) {
    service.recordAll(inputs);
  }

  /** Close (supersede) every current product whose id is absent from the latest snapshot. */
  public void closeAbsent(Set<Integer> presentProductIds) {
    service.closeAbsent(presentProductIds);
  }
}
