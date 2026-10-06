package com.goldys.platform.reconciliation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds the resolved projection on first boot of the read-model tables; a no-op once populated. */
@Component
public class StartupProjectionSeeder implements ApplicationRunner {
  private final ResolvedDailySalesRepository resolved;
  private final DailySalesProjector projector;
  private final ProductSalesProjector productProjector;

  public StartupProjectionSeeder(
      ResolvedDailySalesRepository resolved,
      DailySalesProjector projector,
      ProductSalesProjector productProjector) {
    this.resolved = resolved;
    this.projector = projector;
    this.productProjector = productProjector;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (resolved.count() == 0) {
      projector.recomputeAll();
    }
    // Product exceptions are backfilled on every boot: "no conflicts" and "never seeded" are
    // indistinguishable without a checkpoint table, and the rebuild is cheap at pub scale.
    productProjector.recomputeAll();
  }
}
