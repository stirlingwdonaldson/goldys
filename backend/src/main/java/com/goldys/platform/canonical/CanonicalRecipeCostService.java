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
 * Records canonical recipe-cost facts, locking the current source fact so concurrent corrections
 * cannot leave two current versions. The logical identity is a deterministic UUID of the recipe id.
 */
@Service
class CanonicalRecipeCostService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalRecipeCostRepository repository;
  private final ApplicationEventPublisher publisher;

  CanonicalRecipeCostService(
      CanonicalRecipeCostRepository repository, ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.publisher = publisher;
  }

  @Transactional
  CanonicalRecipeCost record(RecipeCostInput input) {
    CanonicalRecipeCost saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new RecipeCostRecorded(input.recipeId()));
    return saved;
  }

  /**
   * Record a batch of facts in one transaction, publishing a single event so the projector rebuilds
   * once instead of once per fact (the projector's rebuild is whole-table, not per-recipe).
   */
  @Transactional
  void recordAll(List<RecipeCostInput> inputs) {
    for (RecipeCostInput input : inputs) {
      recordAt(input, CLOCK.instant());
    }
    if (!inputs.isEmpty()) {
      publisher.publishEvent(new RecipeCostRecorded(inputs.get(0).recipeId()));
    }
  }

  @Transactional
  CanonicalRecipeCost recordAt(RecipeCostInput input, Instant recordedAt) {
    UUID logicalId = logicalIdFor(input.recipeId());
    String ref = "recipe-cost:" + input.recipeId();
    Optional<CanonicalRecipeCost> current =
        repository.lockCurrentRecipeCost(ref, input.sourceSystem());

    if (current.isPresent()) {
      CanonicalRecipeCost existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalRecipeCost.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.recipeId(),
            input.recipeName(),
            input.costPerPortion(),
            input.costPerPortionInclLabour(),
            input.totalCost(),
            input.portionSize(),
            input.portionSizeValue()));
  }

  private static UUID logicalIdFor(int recipeId) {
    return UUID.nameUUIDFromBytes(("recipe:" + recipeId).getBytes(StandardCharsets.UTF_8));
  }
}
