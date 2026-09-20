package com.goldys.platform.reconciliation;

import static com.goldys.platform.reconciliation.DailySalesReconciliationService.classify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailySalesReconciliationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  @Test
  void classifiesAgreedConflictAndMissing() {
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00"), st("CTB", "10.00"))))
        .isEqualTo("agreed");
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00"), st("CTB", "10.005"))))
        .isEqualTo("agreed"); // within the one-cent rule
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00"), st("CTB", "12.00"))))
        .isEqualTo("conflict");
    assertThat(classify(List.of(st("LIGHTSPEED", "10.00")))).isEqualTo("missing");
  }

  @Test
  void conflictsSurfacesTheKnownGap() {
    CanonicalDailySalesQuery query = mock(CanonicalDailySalesQuery.class);
    when(query.currentDailySales())
        .thenReturn(
            List.of(
                view("LIGHTSPEED", SEP_13, "27650.66"),
                view("CTB", SEP_13, "20990.83"),
                view("LIGHTSPEED", LocalDate.of(2026, 9, 14), "9694.80"),
                view(
                    "CTB",
                    LocalDate.of(2026, 9, 14),
                    "9694.80"))); // agree exactly -> not a conflict

    DailySalesReconciliationService service =
        new DailySalesReconciliationService(query, mock(DailySalesOverrideRepository.class));

    List<DailySalesConflict> conflicts = service.conflicts();

    assertThat(conflicts).hasSize(1);
    assertThat(conflicts.get(0).tradingDate()).isEqualTo(SEP_13);
    assertThat(conflicts.get(0).status()).isEqualTo("conflict");
    assertThat(conflicts.get(0).sources()).hasSize(2);
  }

  @Test
  void resolvedReturnsTheOverrideWhenPresent() {
    CanonicalDailySalesQuery query = mock(CanonicalDailySalesQuery.class);
    when(query.currentDailySalesForDate(SEP_13))
        .thenReturn(
            List.of(view("LIGHTSPEED", SEP_13, "27650.66"), view("CTB", SEP_13, "20990.83")));
    DailySalesOverrideRepository overrides = mock(DailySalesOverrideRepository.class);
    DailySalesOverride override =
        DailySalesOverride.create(
            SEP_13, "LIGHTSPEED", null, "iss", "sub", java.time.Instant.now());
    when(overrides.findCurrent(SEP_13)).thenReturn(Optional.of(override));

    DailySalesReconciliationService service = new DailySalesReconciliationService(query, overrides);

    Optional<DailySalesResolved> resolved = service.resolved(SEP_13);
    assertThat(resolved).isPresent();
    assertThat(resolved.get().authoritativeSource()).isEqualTo("override:LIGHTSPEED");
    assertThat(resolved.get().resolvedTotal()).isEqualByComparingTo("27650.66");
  }

  private static SourceTotal st(String source, String total) {
    return new SourceTotal(source, new BigDecimal(total));
  }

  private static DailySalesView view(String source, LocalDate date, String total) {
    return new DailySalesView(source, date, new BigDecimal(total), null, null);
  }
}
