package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;

/** Public write facade for source-native product-day measures. */
@Service
public class SourceProductSaleFactIngest {
  private final SourceProductSaleFactService service;

  public SourceProductSaleFactIngest(SourceProductSaleFactService service) {
    this.service = service;
  }

  public void record(SourceProductSaleFactInput input) {
    service.record(input);
  }

  /** Batch record for historical replay. */
  public void recordAll(List<SourceProductSaleFactInput> inputs) {
    service.recordAll(inputs);
  }
}
