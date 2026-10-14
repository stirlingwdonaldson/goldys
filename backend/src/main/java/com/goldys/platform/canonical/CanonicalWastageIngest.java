package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;

/** Public write facade for canonical wastage facts, so connectors can record parsed facts. */
@Service
public class CanonicalWastageIngest {
  private final CanonicalWastageService service;

  public CanonicalWastageIngest(CanonicalWastageService service) {
    this.service = service;
  }

  public void record(WastageInput input) {
    service.record(input);
  }

  public void recordAll(List<WastageInput> inputs) {
    service.recordAll(inputs);
  }
}
