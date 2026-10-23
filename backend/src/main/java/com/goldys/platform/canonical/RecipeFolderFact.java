package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeFolder;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
record RecipeFolderFact(
    @Column(name = "folder_id") int folderId,
    @Column(name = "parent_id") Integer parentId,
    @Column(name = "name", columnDefinition = "text") String name) {
  static RecipeFolderFact from(RecipeFolder value) {
    return new RecipeFolderFact(value.folderId(), value.parentId(), value.name());
  }

  RecipeFolder toValue() {
    return new RecipeFolder(folderId, parentId, name);
  }
}
