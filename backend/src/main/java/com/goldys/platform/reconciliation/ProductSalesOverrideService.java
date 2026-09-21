package com.goldys.platform.reconciliation;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records permission-gated, append-only per-product overrides. */
@Service
public class ProductSalesOverrideService {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");
  private static final Clock CLOCK = Clock.systemUTC();

  private final ProductSalesOverrideRepository repository;
  private final PermissionService permissions;
  private final CanonicalProductSalesQuery productSales;

  public ProductSalesOverrideService(
      ProductSalesOverrideRepository repository,
      PermissionService permissions,
      CanonicalProductSalesQuery productSales) {
    this.repository = repository;
    this.permissions = permissions;
    this.productSales = productSales;
  }

  @Transactional
  public ProductSalesOverride save(
      UserRole actor,
      String actorEmail,
      LocalDate date,
      String productNameKey,
      String source,
      String reason) {
    permissions.require(actor, RESOURCE, PermissionAction.WRITE);

    boolean sourceKnown =
        productSales.currentProductSalesForDate(date).stream()
            .anyMatch(
                s -> s.sourceSystem().equals(source) && s.productNameKey().equals(productNameKey));
    if (!sourceKnown) {
      throw new IllegalArgumentException(
          "No data from source '" + source + "' for product '" + productNameKey + "' on " + date);
    }

    Instant now = CLOCK.instant();
    Optional<ProductSalesOverride> current = repository.lockCurrent(productNameKey, date);
    if (current.isPresent()) {
      current.get().supersede(now);
      repository.saveAndFlush(current.get());
    }
    return repository.save(
        ProductSalesOverride.create(date, productNameKey, source, reason, actorEmail, now));
  }

  public Optional<String> currentAuthoritativeSource(LocalDate date, String productNameKey) {
    return repository
        .findCurrent(productNameKey, date)
        .map(ProductSalesOverride::authoritativeSource);
  }
}
