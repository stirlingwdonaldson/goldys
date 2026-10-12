package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;

/** Public write facade for canonical recipe costs, so connectors can record parsed facts. */
@Service
public class RecipeCostIngest {
  private final CanonicalRecipeCostService service;

  public RecipeCostIngest(CanonicalRecipeCostService service) {
    this.service = service;
  }

  public void record(RecipeCostInput input) {
    service.record(input);
  }

  public void recordAll(List<RecipeCostInput> inputs) {
    service.recordAll(inputs);
  }
}
