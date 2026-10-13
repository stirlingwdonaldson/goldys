package com.goldys.platform.canonical;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Read-only alias lookup facade for Lightspeed menu matching. */
@Service
public class ProductAliasQuery {
  private final ProductAliasRepository repository;

  public ProductAliasQuery(ProductAliasRepository repository) {
    this.repository = repository;
  }

  public Optional<String> lookupCanonical(String normalizedAlias) {
    String alias = ProductNameKey.normalize(normalizedAlias);
    if (alias.isEmpty()) {
      return Optional.empty();
    }
    return repository.findByAlias(alias).map(ProductAlias::canonicalName);
  }

  public List<ProductAliasView> all() {
    return repository.findAll().stream()
        .map(alias -> new ProductAliasView(alias.canonicalName(), alias.alias()))
        .toList();
  }
}
