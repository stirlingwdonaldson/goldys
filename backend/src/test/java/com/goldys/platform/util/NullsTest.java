package com.goldys.platform.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NullsTest {
  @Test
  void zeroIfNullBigDecimalReturnsZeroForNullAndValueOtherwise() {
    assertThat(Nulls.zeroIfNull((BigDecimal) null)).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(Nulls.zeroIfNull(new BigDecimal("1.5"))).isEqualByComparingTo("1.5");
  }

  @Test
  void zeroIfNullIntegerReturnsZeroForNullAndValueOtherwise() {
    assertThat(Nulls.zeroIfNull((Integer) null)).isZero();
    assertThat(Nulls.zeroIfNull(7)).isEqualTo(7);
  }

  @Test
  void valueIfBlankReturnsFallbackForNullBlankAndValueOtherwise() {
    assertThat(Nulls.valueIfBlank(null, "Unspecified")).isEqualTo("Unspecified");
    assertThat(Nulls.valueIfBlank("  ", "Unspecified")).isEqualTo("Unspecified");
    assertThat(Nulls.valueIfBlank("Cash", "Unspecified")).isEqualTo("Cash");
  }
}
