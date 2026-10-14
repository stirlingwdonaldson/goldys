package com.goldys.platform.canonical;

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
}
