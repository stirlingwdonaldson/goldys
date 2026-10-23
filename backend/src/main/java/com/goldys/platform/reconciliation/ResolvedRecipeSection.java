package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeSection;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resolved_recipe_section")
class ResolvedRecipeSection {
  @Id private String id;
  private int recipeId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private RecipeSection observation;

  protected ResolvedRecipeSection() {}

  ResolvedRecipeSection(RecipeSection v) {
    id = v.recipeId() + ":" + v.section() + ":" + v.successful();
    recipeId = v.recipeId();
    observation = v;
  }

  RecipeSection observation() {
    return observation;
  }
}
