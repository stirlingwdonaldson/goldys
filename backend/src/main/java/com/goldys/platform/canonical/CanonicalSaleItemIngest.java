package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical sale items, so connectors can record parsed facts. */
@Service
public class CanonicalSaleItemIngest {
  private final CanonicalSaleItemService service;

  public CanonicalSaleItemIngest(CanonicalSaleItemService service) {
    this.service = service;
  }

  public void record(SaleItemInput input) {
    service.record(input);
  }
}
