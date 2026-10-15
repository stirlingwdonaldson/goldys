package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalReservationQuery;
import com.goldys.platform.canonical.ReservationView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReservationProjectorTest {

  private static final LocalDate SEP_20 = LocalDate.of(2026, 9, 20);

  private final CanonicalReservationQuery reservations = mock(CanonicalReservationQuery.class);
  private final ReservationOverrideRepository overrides = mock(ReservationOverrideRepository.class);
  private final ResolvedReservationDayRepository resolved =
      mock(ResolvedReservationDayRepository.class);
  private final ReservationProjector projector =
      new ReservationProjector(reservations, overrides, resolved, "Australia/Melbourne", "15:00");

  @Test
  void bookedCoversIsSumOfPartySizeExcludingCancelledAndWalkIns() {
    when(reservations.currentReservationsForDates(Set.of(SEP_20)))
        .thenReturn(
            List.of(
                view(SEP_20, "2026-09-20T06:00:00Z", 4, "SEATED"),
                view(SEP_20, "2026-09-20T07:00:00Z", 6, "BOOKED"),
                view(SEP_20, "2026-09-20T08:00:00Z", 8, "CANCELLED"),
                view(SEP_20, "2026-09-20T09:00:00Z", 2, "WALK_IN")));
    when(overrides.findAllCurrent()).thenReturn(List.of());

    projector.recompute(SEP_20);

    ResolvedReservationDay row = captured().get(0);
    assertThat(row.bookedCovers()).isEqualTo(10);
  }

  @SuppressWarnings("unchecked")
  private List<ResolvedReservationDay> captured() {
    ArgumentCaptor<List<ResolvedReservationDay>> captor =
        ArgumentCaptor.forClass((Class) List.class);
    verify(resolved).saveAll(captor.capture());
    return captor.getValue();
  }

  private static ReservationView view(LocalDate date, String at, int partySize, String status) {
    return new ReservationView(date, "OPENTABLE", Instant.parse(at), partySize, status);
  }
}
