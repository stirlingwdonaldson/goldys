package com.goldys.platform.reconciliation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds the resolved projection on first boot of the read-model tables; a no-op once populated. */
@Component
public class StartupProjectionSeeder implements ApplicationRunner {
  private final ResolvedDailySalesRepository resolved;
  private final DailySalesProjector projector;

  public StartupProjectionSeeder(
      ResolvedDailySalesRepository resolved, DailySalesProjector projector) {
    this.resolved = resolved;
    this.projector = projector;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (resolved.count() == 0) {
      projector.recomputeAll();
    }
  }
}
