package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resolved_recipe_definition")
class ResolvedRecipeDefinition {
  @Id
  @Column(name = "recipe_id")
  private int recipeId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private RecipeDefinition definition;

  protected ResolvedRecipeDefinition() {}

  ResolvedRecipeDefinition(RecipeDefinition definition) {
    this.recipeId = definition.header().recipeId();
    this.definition = definition;
  }

  RecipeDefinition definition() {
    return definition;
  }
}
