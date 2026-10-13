package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.ProductRecorded;
import com.goldys.platform.canonical.RecipeCostRecorded;
import com.goldys.platform.canonical.SaleRecipeLinkRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the recipe read model whenever a canonical recipe-cost or sale-recipe-link fact is
 * recorded, so canonical and projection stay atomic.
 */
@Component
public class RecipeProjectionListener {
  private final RecipeProjector projector;

  public RecipeProjectionListener(RecipeProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(RecipeCostRecorded event) {
    projector.recomputeAll();
  }

  @EventListener
  public void on(SaleRecipeLinkRecorded event) {
    projector.recomputeAll();
  }

  @EventListener
  public void on(ProductRecorded event) {
    projector.recomputeAll();
  }
}
