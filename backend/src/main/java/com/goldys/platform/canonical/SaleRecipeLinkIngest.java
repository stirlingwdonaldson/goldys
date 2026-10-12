package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Public write facade for canonical sale-item→recipe links, so connectors can record parsed facts.
 */
@Service
public class SaleRecipeLinkIngest {
  private final CanonicalSaleRecipeLinkService service;

  public SaleRecipeLinkIngest(CanonicalSaleRecipeLinkService service) {
    this.service = service;
  }

  public void record(SaleRecipeLinkInput input) {
    service.record(input);
  }

  public void recordAll(List<SaleRecipeLinkInput> inputs) {
    service.recordAll(inputs);
  }
}
