package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical recipe costs, so modules outside this package never touch
 * the package-private entity or repository directly.
 */
@Service
public class CanonicalRecipeCostQuery {
  private final CanonicalRecipeCostRepository repository;

  @org.springframework.beans.factory.annotation.Autowired
  private com.goldys.platform.ingestion.RawLedgerQuery raw;

  public CanonicalRecipeCostQuery(CanonicalRecipeCostRepository repository) {
    this.repository = repository;
  }

  /** All current recipe costs, mapped to views. */
  public List<RecipeCostView> currentRecipeCosts() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  private RecipeCostView toView(CanonicalRecipeCost r) {
    return new RecipeCostView(
        r.recipeId(),
        r.recipeName(),
        r.costPerPortion(),
        r.costPerPortionInclLabour(),
        r.totalCost(),
        r.portionSize(),
        r.portionSizeValue(),
        raw.recipeEvidence(r.rawRecordId(), "legacy:ctb-recipe-cost"));
  }
}
