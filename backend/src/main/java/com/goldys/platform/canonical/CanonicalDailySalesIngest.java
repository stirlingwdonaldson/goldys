package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/**
 * Public write facade for canonical daily sales, so connectors can record parsed daily totals
 * without reaching the package-private entity/service.
 */
@Service
public class CanonicalDailySalesIngest {
  private final CanonicalDailySalesService service;

  public CanonicalDailySalesIngest(CanonicalDailySalesService service) {
    this.service = service;
  }

  public void record(DailySalesInput input) {
    service.record(input);
  }
}
