package com.goldys.platform.canonical;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the internal business-product hub by canonical name. The hub is found by name or created
 * on first sight; its {@code id} is the stable business-product identity. The name is the lookup
 * key, never the identity — the mapping decisions live in {@link ProductMappingService}.
 */
@Service
class ProductService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final ProductRepository repository;

  ProductService(ProductRepository repository) {
    this.repository = repository;
  }

  @Transactional
  UUID resolve(String name) {
    Optional<Product> existing = repository.findByName(name);
    if (existing.isPresent()) {
      return existing.get().id();
    }
    Product product = Product.create(UUID.randomUUID(), name, CLOCK.instant());
    return repository.save(product).id();
  }
}
