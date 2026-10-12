package com.goldys.platform.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ResolvedRecipeIngredientRepository extends JpaRepository<ResolvedRecipeIngredient, UUID> {

  /** One recipe's lines, main lines before sub-recipe lines, then by recipe then detail id. */
  @Query(
      "select i from ResolvedRecipeIngredient i where i.parentRecipeId = :recipeId "
          + "order by i.isSubRecipe asc, i.lineRecipeName asc, i.recipeDetailId asc")
  List<ResolvedRecipeIngredient> findByParentRecipeId(@Param("recipeId") int recipeId);
}
