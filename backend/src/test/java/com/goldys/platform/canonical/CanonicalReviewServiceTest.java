package com.goldys.platform.canonical;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.context.ApplicationEventPublisher;

/** Unit coverage of the canonical review record service and read facade. */
class CanonicalReviewServiceTest {

  private static final UUID RAW = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String SOURCE_SYSTEM = "OPENTABLE";
  private static final String SOURCE = "OpenTable";
  private static final String REVIEW_ID = "RT-1001";
  private static final UUID LOGICAL_ID =
      UUID.nameUUIDFromBytes(("review:" + REVIEW_ID).getBytes(UTF_8));
  private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
  private static final Instant REVIEWED_AT = Instant.parse("2026-09-20T10:00:00Z");
  private static final Instant VISITED_AT = Instant.parse("2026-09-19T19:00:00Z");

  private CanonicalReviewRepository repository;
  private ApplicationEventPublisher publisher;
  private CanonicalReviewService service;

  @BeforeEach
  void setUp() {
    repository = mock(CanonicalReviewRepository.class);
    publisher = mock(ApplicationEventPublisher.class);
    service = new CanonicalReviewService(repository, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void record_unchanged_is_noop() {
    when(repository.lockCurrentReview(REVIEW_ID, SOURCE_SYSTEM)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalReview saved = service.record(input(4));

    when(repository.lockCurrentReview(REVIEW_ID, SOURCE_SYSTEM)).thenReturn(Optional.of(saved));
    CanonicalReview again = service.record(input(4));

    assertThat(again).isSameAs(saved);
    verify(repository, times(1)).save(any(CanonicalReview.class));
    verify(repository, never()).saveAndFlush(any(CanonicalReview.class));
  }

  @Test
  void record_changed_supersedes_and_inserts() {
    CanonicalReview existing = create(4);
    when(repository.lockCurrentReview(REVIEW_ID, SOURCE_SYSTEM)).thenReturn(Optional.of(existing));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalReview successor = service.record(input(5));

    assertThat(existing.supersededAt()).isEqualTo(NOW);
    verify(repository).saveAndFlush(existing);
    assertThat(successor.logicalEntityId()).isEqualTo(existing.logicalEntityId());
    assertThat(successor.overallRating()).isEqualTo(5);
  }

  @Test
  void logical_id_is_deterministic() {
    when(repository.lockCurrentReview(REVIEW_ID, SOURCE_SYSTEM)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CanonicalReview first = service.record(input(4));
    when(repository.lockCurrentReview(REVIEW_ID, SOURCE_SYSTEM)).thenReturn(Optional.of(first));
    CanonicalReview second = service.record(input(5));

    assertThat(first.logicalEntityId()).isEqualTo(second.logicalEntityId());
    assertThat(first.logicalEntityId()).isEqualTo(LOGICAL_ID);
  }

  @Test
  void publishes_review_recorded() {
    when(repository.lockCurrentReview(REVIEW_ID, SOURCE_SYSTEM)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.record(input(4));

    ArgumentCaptor<ReviewRecorded> captor = ArgumentCaptor.forClass(ReviewRecorded.class);
    verify(publisher).publishEvent(captor.capture());
    ReviewRecorded event = captor.getValue();
    assertThat(event.reviewId()).isEqualTo(REVIEW_ID);
    assertThat(event.source()).isEqualTo(SOURCE);
    assertThat(event.overallRating()).isEqualTo(4);
    assertThat(event.comment()).isEqualTo("Great food");
    assertThat(event.tags()).containsExactly("Great food", "Friendly");
  }

  @Test
  void query_filters_by_reviewed_at() {
    CanonicalReviewRepository queryRepository = mock(CanonicalReviewRepository.class);
    CanonicalReviewQuery query = new CanonicalReviewQuery(queryRepository);
    Instant from = Instant.parse("2026-09-01T00:00:00Z");
    Instant to = Instant.parse("2026-10-01T00:00:00Z");

    CanonicalReview inRange = create(4);
    when(queryRepository.findCurrentByReviewedAtBetween(from, to)).thenReturn(List.of(inRange));

    List<ReviewFact> facts = query.currentReviews(from, to);

    assertThat(facts).hasSize(1);
    ReviewFact fact = facts.get(0);
    assertThat(fact.reviewId()).isEqualTo(REVIEW_ID);
    assertThat(fact.source()).isEqualTo(SOURCE);
    assertThat(fact.reviewedAt()).isEqualTo(REVIEWED_AT);
    assertThat(fact.overallRating()).isEqualTo(4);
  }

  private static ReviewInput input(Integer overallRating) {
    return new ReviewInput(
        SOURCE_SYSTEM,
        REVIEW_ID,
        SOURCE,
        REVIEWED_AT,
        VISITED_AT,
        overallRating,
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
        "guest-42",
        "Ada Lovelace",
        RAW);
  }

  private static CanonicalReview create(Integer overallRating) {
    return CanonicalReview.create(
        LOGICAL_ID, SOURCE_SYSTEM, REVIEW_ID, RAW, NOW, NOW, input(overallRating));
  }
}
