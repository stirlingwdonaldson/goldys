package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeSectionProjector {
  private final RecipeSectionIngest canonical;
  private final ResolvedRecipeSectionRepository resolved;

  public RecipeSectionProjector(
      RecipeSectionIngest canonical, ResolvedRecipeSectionRepository resolved) {
    this.canonical = canonical;
    this.resolved = resolved;
  }

  @EventListener
  @Transactional
  public void on(RecipeSectionRecorded event) {
    resolved.saveAll(
        canonical.currentFor(event.recipeId()).stream().map(ResolvedRecipeSection::new).toList());
  }

  @Transactional
  public void recomputeAll() {
    resolved.deleteAllInBatch();
    resolved.saveAll(canonical.current().stream().map(ResolvedRecipeSection::new).toList());
  }
}
