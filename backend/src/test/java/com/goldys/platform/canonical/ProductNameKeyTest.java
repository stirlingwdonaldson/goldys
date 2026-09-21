package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductNameKeyTest {

  @Test
  void normalizesCasePunctuationAndWhitespace() {
    assertThat(ProductNameKey.normalize("  Pint  -  Carlton   Draught "))
        .isEqualTo("pint carlton draught");
  }

  @Test
  void foldsTheNewPrefixAlias() {
    assertThat(ProductNameKey.normalize("New Kids Fish & Chippies"))
        .isEqualTo("kids fish chippies");
    assertThat(ProductNameKey.normalize("Kids Fish & Chippies")).isEqualTo("kids fish chippies");
  }

  @Test
  void blankNameIsEmpty() {
    assertThat(ProductNameKey.normalize(null)).isEmpty();
    assertThat(ProductNameKey.normalize("   ")).isEmpty();
  }
}
