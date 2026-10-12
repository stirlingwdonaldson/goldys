package com.goldys.platform.reconciliation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ResolvedRecipeRepository extends JpaRepository<ResolvedRecipe, Integer> {
  List<ResolvedRecipe> findAllByOrderByRecipeNameAsc();
}
