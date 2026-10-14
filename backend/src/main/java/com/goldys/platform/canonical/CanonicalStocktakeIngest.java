package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical stocktake headers, so connectors can record parsed facts. */
@Service
public class CanonicalStocktakeIngest {
  private final CanonicalStocktakeService service;

  public CanonicalStocktakeIngest(CanonicalStocktakeService service) {
    this.service = service;
  }

  public void record(StocktakeInput input) {
    service.record(input);
  }
}
