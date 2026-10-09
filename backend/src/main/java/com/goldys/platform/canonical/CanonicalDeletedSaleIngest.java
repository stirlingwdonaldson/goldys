package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical deleted-sales, so connectors can record parsed facts. */
@Service
public class CanonicalDeletedSaleIngest {
  private final CanonicalDeletedSaleService service;

  public CanonicalDeletedSaleIngest(CanonicalDeletedSaleService service) {
    this.service = service;
  }

  public void record(DeletedSaleInput input) {
    service.record(input);
  }
}
