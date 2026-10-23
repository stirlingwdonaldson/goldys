package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical sale items, so connectors can record parsed facts. */
@Service
public class CanonicalSaleItemIngest {
  private final CanonicalSaleItemService service;

  public CanonicalSaleItemIngest(CanonicalSaleItemService service) {
    this.service = service;
  }

  /** Insert-only recovery, returning whether a missing source key was recorded. */
  public boolean recordMissing(SaleItemInput input) {
    return service.recordMissing(input);
  }

  public void record(SaleItemInput input) {
    service.record(input);
  }
}
