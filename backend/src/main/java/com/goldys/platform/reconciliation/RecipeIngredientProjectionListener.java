package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.RecipeIngredientRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the recipe-ingredient read model whenever a canonical recipe-ingredient fact is
 * recorded, so canonical and projection stay atomic.
 */
@Component
public class RecipeIngredientProjectionListener {
  private final RecipeIngredientProjector projector;

  public RecipeIngredientProjectionListener(RecipeIngredientProjector projector) {
    this.projector = projector;
  }

  @EventListener
  public void on(RecipeIngredientRecorded event) {
    projector.recomputeAll();
  }
}
