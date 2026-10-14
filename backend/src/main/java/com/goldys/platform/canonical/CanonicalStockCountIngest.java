package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;

/** Public write facade for canonical stock counts, so connectors can record parsed facts. */
@Service
public class CanonicalStockCountIngest {
  private final CanonicalStockCountService service;

  public CanonicalStockCountIngest(CanonicalStockCountService service) {
    this.service = service;
  }

  public void record(StockCountInput input) {
    service.record(input);
  }

  public void recordAll(List<StockCountInput> inputs) {
    service.recordAll(inputs);
  }
}
