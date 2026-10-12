package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical recipe-ingredient facts, locking the current source fact so concurrent
 * corrections cannot leave two current versions. The logical identity is a deterministic UUID of
 * {@code (parentRecipeId, recipeDetailId, stockId)} — the recipe detail id is OWNER-scoped, so the
 * parent recipe must anchor it.
 */
@Service
class CanonicalRecipeIngredientService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalRecipeIngredientRepository repository;
  private final ApplicationEventPublisher publisher;

  CanonicalRecipeIngredientService(
      CanonicalRecipeIngredientRepository repository, ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.publisher = publisher;
  }

  @Transactional
  CanonicalRecipeIngredient record(RecipeIngredientInput input) {
    CanonicalRecipeIngredient saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new RecipeIngredientRecorded(input.parentRecipeId()));
    return saved;
  }

  /**
   * Record a batch of facts in one transaction, publishing a single event so the projector rebuilds
   * once instead of once per line (the projector's rebuild is whole-table).
   */
  @Transactional
  void recordAll(List<RecipeIngredientInput> inputs) {
    for (RecipeIngredientInput input : inputs) {
      recordAt(input, CLOCK.instant());
    }
    if (!inputs.isEmpty()) {
      publisher.publishEvent(new RecipeIngredientRecorded(inputs.get(0).parentRecipeId()));
    }
  }

  @Transactional
  CanonicalRecipeIngredient recordAt(RecipeIngredientInput input, Instant recordedAt) {
    String ref = sourceRef(input.parentRecipeId(), input.recipeDetailId(), input.stockId());
    UUID logicalId = logicalIdFor(input.parentRecipeId(), input.recipeDetailId(), input.stockId());
    Optional<CanonicalRecipeIngredient> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), ref);

    if (current.isPresent()) {
      CanonicalRecipeIngredient existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalRecipeIngredient.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.parentRecipeId(),
            input.lineRecipeId(),
            input.lineRecipeName(),
            input.isSubRecipe(),
            input.recipeDetailId(),
            input.stockId(),
            input.stockCode(),
            input.stockDescription(),
            input.ingredientDescription(),
            input.supplierName(),
            input.supplierStockCode(),
            input.portionWeight(),
            input.unitOfMeasurement(),
            input.stockUomShortname(),
            input.costPerQty(),
            input.costPerUnit(),
            input.stockUsable(),
            input.stockWastage(),
            input.isBrokenLink()));
  }

  private static String sourceRef(int parentRecipeId, int recipeDetailId, Integer stockId) {
    return "recipe-ingredient:" + parentRecipeId + ":" + recipeDetailId + ":" + stockId;
  }

  private static UUID logicalIdFor(int parentRecipeId, int recipeDetailId, Integer stockId) {
    return UUID.nameUUIDFromBytes(
        sourceRef(parentRecipeId, recipeDetailId, stockId).getBytes(StandardCharsets.UTF_8));
  }
}
