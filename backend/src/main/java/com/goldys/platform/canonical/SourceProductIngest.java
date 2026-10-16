package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for source-product identities, so connectors can record parsed facts. */
@Service
public class SourceProductIngest {
  private final SourceProductService service;

  public SourceProductIngest(SourceProductService service) {
    this.service = service;
  }

  public SourceProductIdentity record(SourceProductInput input) {
    return service.record(input);
  }
}
