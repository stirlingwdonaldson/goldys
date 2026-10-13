package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure-JUnit coverage of the {@link ProductCategory} factory and curation mutators. */
class ProductCategoryTest {

  @Test
  void createSetsNameGroupDisplayOrderAndDescription() {
    ProductCategory category =
        ProductCategory.create("Cocktails", "Drinks", 2, "Shaken and stirred drinks");

    assertThat(category.id()).isNotNull();
    assertThat(category.name()).isEqualTo("Cocktails");
    assertThat(category.groupName()).isEqualTo("Drinks");
    assertThat(category.displayOrder()).isEqualTo(2);
    assertThat(category.description()).isEqualTo("Shaken and stirred drinks");
  }

  @Test
  void renameUpdatesName() {
    ProductCategory category = ProductCategory.create("Cocktails", "Drinks", 0, null);

    category.rename("House Cocktails");

    assertThat(category.name()).isEqualTo("House Cocktails");
  }

  @Test
  void regroupSetsGroupName() {
    ProductCategory category = ProductCategory.create("Cocktails", null, 0, null);

    category.regroup("Drinks");

    assertThat(category.groupName()).isEqualTo("Drinks");
  }

  @Test
  void regroupClearsGroupName() {
    ProductCategory category = ProductCategory.create("Cocktails", "Drinks", 0, null);

    category.regroup(null);

    assertThat(category.groupName()).isNull();
  }
}
