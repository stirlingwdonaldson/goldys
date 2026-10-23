package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeField;
import jakarta.persistence.*;

@Embeddable
class RecipeFieldFact {
  @Column(name = "field_path", nullable = false)
  private String path;

  @Column(name = "field_value", columnDefinition = "text")
  private String value;

  @Column(name = "field_type", nullable = false)
  private String type;

  protected RecipeFieldFact() {}

  RecipeFieldFact(RecipeField f) {
    path = f.path();
    value = f.value();
    type = f.type();
  }

  RecipeField toValue() {
    return new RecipeField(path, value, type);
  }
}
