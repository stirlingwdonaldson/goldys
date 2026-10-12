package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;

/** Public write facade for canonical recipe ingredients, so connectors can record parsed facts. */
@Service
public class RecipeIngredientIngest {
  private final CanonicalRecipeIngredientService service;

  public RecipeIngredientIngest(CanonicalRecipeIngredientService service) {
    this.service = service;
  }

  public void record(RecipeIngredientInput input) {
    service.record(input);
  }

  public void recordAll(List<RecipeIngredientInput> inputs) {
    service.recordAll(inputs);
  }
}
