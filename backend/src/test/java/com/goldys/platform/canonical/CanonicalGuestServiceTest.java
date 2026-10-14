package com.goldys.platform.canonical;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Unit coverage of the minimal-guest populate service. */
class CanonicalGuestServiceTest {

  private static final UUID RAW = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String SOURCE_SYSTEM = "OPENTABLE";
  private static final String GUEST_ID = "g1";
  private static final String GUEST_NAME = "Mary";
  private static final UUID LOGICAL_ID =
      UUID.nameUUIDFromBytes(("guest:" + GUEST_ID).getBytes(UTF_8));
  private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

  private CanonicalGuestRepository repository;
  private CanonicalGuestService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalGuestRepository.class);
    service = new CanonicalGuestService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void ensure_creates_minimal_guest_once() {
    when(repository.lockCurrentGuest(GUEST_ID, SOURCE_SYSTEM)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.ensure(GUEST_ID, GUEST_NAME, RAW);

    ArgumentCaptor<CanonicalGuest> captor = ArgumentCaptor.forClass(CanonicalGuest.class);
    verify(repository).save(captor.capture());
    CanonicalGuest saved = captor.getValue();
    assertThat(saved.guestName()).isEqualTo(GUEST_NAME);
    assertThat(saved.logicalEntityId()).isEqualTo(LOGICAL_ID);

    when(repository.lockCurrentGuest(GUEST_ID, SOURCE_SYSTEM)).thenReturn(Optional.of(saved));
    service.ensure(GUEST_ID, GUEST_NAME, RAW);

    verify(repository, times(1)).save(any(CanonicalGuest.class));
  }

  @Test
  void ensure_links_to_same_logical_id() {
    when(repository.lockCurrentGuest(GUEST_ID, SOURCE_SYSTEM)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.ensure(GUEST_ID, GUEST_NAME, RAW);

    ArgumentCaptor<CanonicalGuest> captor = ArgumentCaptor.forClass(CanonicalGuest.class);
    verify(repository).save(captor.capture());
    CanonicalGuest saved = captor.getValue();

    CanonicalReview review =
        CanonicalReview.create(
            LOGICAL_ID, SOURCE_SYSTEM, "RT-1001", RAW, NOW, NOW, reviewInput(GUEST_ID, GUEST_NAME));

    assertThat(saved.logicalEntityId()).isEqualTo(review.guestId());
  }

  private static ReviewInput reviewInput(String guestId, String guestName) {
    return new ReviewInput(
        SOURCE_SYSTEM,
        "RT-1001",
        "OpenTable",
        NOW,
        NOW,
        4,
        5,
        4,
        4,
        5,
        "Quiet",
        true,
        "Great food",
        "Regular",
        List.of("Great food", "Friendly"),
        List.of("steak", "fries"),
        guestId,
        guestName,
        RAW);
  }
}
