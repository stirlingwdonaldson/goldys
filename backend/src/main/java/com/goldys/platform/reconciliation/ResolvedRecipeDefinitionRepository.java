package com.goldys.platform.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

interface ResolvedRecipeDefinitionRepository
    extends JpaRepository<ResolvedRecipeDefinition, Integer> {
  @org.springframework.data.jpa.repository.Query(
      value =
          "select * from resolved_recipe_definition where definition->'usages' @> jsonb_build_array(jsonb_build_object('recipeId',cast(:recipeId as integer)))",
      nativeQuery = true)
  java.util.List<ResolvedRecipeDefinition> findParents(int recipeId);
}
