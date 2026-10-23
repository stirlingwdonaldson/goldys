package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeSection;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeSectionIngest {
  private final CanonicalRecipeSectionRepository repository;
  private final ApplicationEventPublisher events;

  public RecipeSectionIngest(
      CanonicalRecipeSectionRepository repository, ApplicationEventPublisher events) {
    this.repository = repository;
    this.events = events;
  }

  @Transactional
  public void record(RecipeSection value) {
    String ref =
        "recipe-section:"
            + value.recipeId()
            + ":"
            + value.section()
            + ":"
            + (value.successful() ? "success" : "failure");
    var old = repository.lockCurrent(ref);
    if (old.isPresent() && !value.evidence().capturedAt().isAfter(old.get().validFrom())) return;
    Instant now = Instant.now();
    old.ifPresent(
        r -> {
          r.supersede(now);
          repository.saveAndFlush(r);
        });
    repository.saveAndFlush(new CanonicalRecipeSection(value, ref, now));
    events.publishEvent(new RecipeSectionRecorded(value.recipeId()));
  }

  @Transactional(readOnly = true)
  public java.util.List<RecipeSection> currentFor(int id) {
    return repository.currentFor(id).stream().map(CanonicalRecipeSection::toValue).toList();
  }

  @Transactional(readOnly = true)
  public java.util.List<RecipeSection> current() {
    return repository.findAllCurrent().stream().map(CanonicalRecipeSection::toValue).toList();
  }
}
