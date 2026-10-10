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
  private final ReservationProjector reservationProjector;
  private final LabourProjector labourProjector;
  private final InventoryProjector inventoryProjector;
  private final PaymentProjector paymentProjector;
  private final DeletedSaleProjector deletedSaleProjector;
  private final SaleItemProjector saleItemProjector;

  public StartupProjectionSeeder(
      ResolvedDailySalesRepository resolved,
      DailySalesProjector projector,
      ProductSalesProjector productProjector,
      ReservationProjector reservationProjector,
      LabourProjector labourProjector,
      InventoryProjector inventoryProjector,
      PaymentProjector paymentProjector,
      DeletedSaleProjector deletedSaleProjector,
      SaleItemProjector saleItemProjector) {
    this.resolved = resolved;
    this.projector = projector;
    this.productProjector = productProjector;
    this.reservationProjector = reservationProjector;
    this.labourProjector = labourProjector;
    this.inventoryProjector = inventoryProjector;
    this.paymentProjector = paymentProjector;
    this.deletedSaleProjector = deletedSaleProjector;
    this.saleItemProjector = saleItemProjector;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (resolved.count() == 0) {
      projector.recomputeAll();
    }
    // Product, reservation, labour, inventory, payment, deleted-sale and sale-item projections are
    // backfilled on every boot: "no conflicts" and "never seeded" are indistinguishable without a
    // checkpoint table, and the rebuild is cheap at pub scale.
    productProjector.recomputeAll();
    reservationProjector.recomputeAll();
    labourProjector.recomputeAll();
    inventoryProjector.recomputeAll();
    paymentProjector.recomputeAll();
    deletedSaleProjector.recomputeAll();
    saleItemProjector.recomputeAll();
  }
}
