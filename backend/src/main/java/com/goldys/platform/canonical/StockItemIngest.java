package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for supplier-scoped stock items. */
@Service
public class StockItemIngest {
  private final StockItemService service;

  public StockItemIngest(StockItemService service) {
    this.service = service;
  }

  public void record(StockItemInput input) {
    service.record(input);
  }
}
