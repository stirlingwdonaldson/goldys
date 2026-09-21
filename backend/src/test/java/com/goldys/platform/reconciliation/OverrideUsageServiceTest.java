package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OverrideUsageServiceTest {

  @Test
  void countsBothOverrideSurfacesInTheWindow() {
    DailySalesOverrideRepository daily = mock(DailySalesOverrideRepository.class);
    ProductSalesOverrideRepository product = mock(ProductSalesOverrideRepository.class);
    when(daily.countByRecordedAtAfter(any(Instant.class))).thenReturn(2L);
    when(product.countByRecordedAtAfter(any(Instant.class))).thenReturn(3L);

    OverrideUsage usage = new OverrideUsageService(daily, product).usage();

    assertThat(usage.count()).isEqualTo(5);
    assertThat(usage.period()).isEqualTo("last 7 days");
  }

  @Test
  void zeroOverridesReportsZeroNotAbsent() {
    DailySalesOverrideRepository daily = mock(DailySalesOverrideRepository.class);
    ProductSalesOverrideRepository product = mock(ProductSalesOverrideRepository.class);
    when(daily.countByRecordedAtAfter(any(Instant.class))).thenReturn(0L);
    when(product.countByRecordedAtAfter(any(Instant.class))).thenReturn(0L);

    OverrideUsage usage = new OverrideUsageService(daily, product).usage();

    assertThat(usage.count()).isZero();
    assertThat(usage.period()).isEqualTo("last 7 days");
  }
}
