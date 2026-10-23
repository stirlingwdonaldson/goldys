package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeCatalog;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resolved_recipe_catalog")
class ResolvedRecipeCatalog {
  @Id private int id = 1;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private RecipeCatalog catalog;

  protected ResolvedRecipeCatalog() {}

  ResolvedRecipeCatalog(RecipeCatalog catalog) {
    this.catalog = catalog;
  }

  RecipeCatalog catalog() {
    return catalog;
  }
}
