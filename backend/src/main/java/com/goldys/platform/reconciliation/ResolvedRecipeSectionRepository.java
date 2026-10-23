package com.goldys.platform.reconciliation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ResolvedRecipeSectionRepository extends JpaRepository<ResolvedRecipeSection, String> {
  List<ResolvedRecipeSection> findByRecipeId(int recipeId);
}
