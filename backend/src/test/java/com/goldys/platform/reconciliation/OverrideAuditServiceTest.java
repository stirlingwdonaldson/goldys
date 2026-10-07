package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverrideAuditServiceTest {

  private final DailySalesOverrideRepository daily = mock(DailySalesOverrideRepository.class);
  private final ProductSalesOverrideRepository product = mock(ProductSalesOverrideRepository.class);
  private final OverrideAuditService service = new OverrideAuditService(daily, product);

  @Test
  void listsDailyAndProductOverridesNewestFirstWithSupersession() {
    LocalDate date = LocalDate.of(2026, 9, 13);
    Instant older = Instant.parse("2026-09-13T09:00:00Z");
    Instant newer = Instant.parse("2026-09-14T09:00:00Z");

    DailySalesOverride active =
        DailySalesOverride.create(date, "LIGHTSPEED", "typo", "a@b.com", newer);
    DailySalesOverride superseded = DailySalesOverride.create(date, "CTB", null, "a@b.com", older);
    superseded.supersede(newer);
    ProductSalesOverride productOverride =
        ProductSalesOverride.create(date, "garlic aioli", "LIGHTSPEED", null, "a@b.com", older);

    when(daily.findAllByOrderByRecordedAtDesc()).thenReturn(List.of(active, superseded));
    when(product.findAllByOrderByRecordedAtDesc()).thenReturn(List.of(productOverride));

    List<OverrideAuditService.OverrideAuditEntry> history = service.history();

    assertThat(history).hasSize(3);
    assertThat(history.get(0))
        .extracting(
            OverrideAuditService.OverrideAuditEntry::entityType,
            OverrideAuditService.OverrideAuditEntry::fieldKey,
            OverrideAuditService.OverrideAuditEntry::authoritativeSource,
            OverrideAuditService.OverrideAuditEntry::supersededAt)
        .containsExactly("daily_sales", "daily_sales", "LIGHTSPEED", null);

    OverrideAuditService.OverrideAuditEntry removed =
        history.stream().filter(e -> e.supersededAt() != null).findFirst().orElseThrow();
    assertThat(removed.authoritativeSource()).isEqualTo("CTB");
    assertThat(removed.supersededAt()).isEqualTo(newer);
  }
}
