package com.goldys.platform.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Rebuilds disposable resolved projections from canonical facts and accepted decisions on boot. */
@Component
public class StartupProjectionSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(StartupProjectionSeeder.class);

  private final RecipeSectionProjector recipeSectionProjector;

  private final DailySalesProjector projector;
  private final ProductSalesProjector productProjector;
  private final ReservationProjector reservationProjector;
  private final LabourProjector labourProjector;
  private final InventoryProjector inventoryProjector;
  private final PaymentProjector paymentProjector;
  private final DeletedSaleProjector deletedSaleProjector;
  private final SaleItemProjector saleItemProjector;
  private final RecipeProjector recipeProjector;
  private final RecipeIngredientProjector recipeIngredientProjector;
  private final RecipeDefinitionProjector recipeDefinitionProjector;

  public StartupProjectionSeeder(
      DailySalesProjector projector,
      ProductSalesProjector productProjector,
      ReservationProjector reservationProjector,
      LabourProjector labourProjector,
      InventoryProjector inventoryProjector,
      PaymentProjector paymentProjector,
      DeletedSaleProjector deletedSaleProjector,
      SaleItemProjector saleItemProjector,
      RecipeProjector recipeProjector,
      RecipeIngredientProjector recipeIngredientProjector,
      RecipeDefinitionProjector recipeDefinitionProjector,
      RecipeSectionProjector recipeSectionProjector) {
    this.projector = projector;
    this.recipeSectionProjector = recipeSectionProjector;
    this.productProjector = productProjector;
    this.reservationProjector = reservationProjector;
    this.labourProjector = labourProjector;
    this.inventoryProjector = inventoryProjector;
    this.paymentProjector = paymentProjector;
    this.deletedSaleProjector = deletedSaleProjector;
    this.saleItemProjector = saleItemProjector;
    this.recipeProjector = recipeProjector;
    this.recipeIngredientProjector = recipeIngredientProjector;
    this.recipeDefinitionProjector = recipeDefinitionProjector;
  }

  @Override
  public void run(ApplicationArguments args) {
    // Changed resolver semantics must reach populated history after deployment too. Rebuild only
    // the disposable projection; source facts, raw evidence, overrides and rules are retained.
    projector.recomputeAll();
    // Product, reservation, labour, inventory, payment, deleted-sale, sale-item, recipe and
    // recipe-ingredient projections are backfilled on every boot: "no conflicts" and "never seeded"
    // are indistinguishable without a checkpoint table.
    productProjector.recomputeAll();
    reservationProjector.recomputeAll();
    labourProjector.recomputeAll();
    inventoryProjector.recomputeAll();
    paymentProjector.recomputeAll();
    deletedSaleProjector.recomputeAll();
    saleItemProjector.recomputeAll();
    recipeProjector.recomputeAll();
    recipeIngredientProjector.recomputeAll();
    recipeDefinitionProjector.recomputeAll();
    recipeSectionProjector.recomputeAll();
    log.info("Resolved projections rebuilt from current canonical facts");
  }
}
