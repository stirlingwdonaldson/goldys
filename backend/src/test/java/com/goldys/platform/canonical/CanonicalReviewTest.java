package com.goldys.platform.canonical;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure-JUnit coverage of the {@link CanonicalReview} factory and fact-equality check. */
class CanonicalReviewTest {

  private static final UUID RAW_RECORD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID LOGICAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final Instant REVIEWED_AT = Instant.parse("2026-09-20T10:00:00Z");
  private static final Instant VISITED_AT = Instant.parse("2026-09-19T19:00:00Z");

  @Test
  void sameFact_detects_equal_and_changed_inputs() {
    CanonicalReview review = create(review(4, "guest-1", "Ada Lovelace"));

    assertThat(review.sameFact(review(4, "guest-1", "Ada Lovelace"))).isTrue();
    assertThat(review.sameFact(review(5, "guest-1", "Ada Lovelace"))).isFalse();
  }

  @Test
  void create_computes_guest_uuid_from_guest_id() {
    CanonicalReview withGuest = create(review(4, "guest-42", "Ada Lovelace"));

    assertThat(withGuest.guestId())
        .isEqualTo(UUID.nameUUIDFromBytes("guest:guest-42".getBytes(UTF_8)));

    CanonicalReview withoutGuest = create(review(4, null, "Ada Lovelace"));

    assertThat(withoutGuest.guestId()).isNull();
  }

  @Test
  void create_sets_ratings_and_source() {
    CanonicalReview review = create(review(4, "guest-1", "Ada Lovelace"));

    assertThat(review.source()).isEqualTo("OpenTable");
    assertThat(review.reviewedAt()).isEqualTo(REVIEWED_AT);
    assertThat(review.visitedAt()).isEqualTo(VISITED_AT);
    assertThat(review.overallRating()).isEqualTo(4);
    assertThat(review.foodRating()).isEqualTo(5);
    assertThat(review.serviceRating()).isEqualTo(4);
    assertThat(review.ambienceRating()).isEqualTo(4);
    assertThat(review.valueRating()).isEqualTo(5);
    assertThat(review.noiseLabel()).isEqualTo("Quiet");
    assertThat(review.wouldRecommend()).isTrue();
    assertThat(review.comment()).isEqualTo("Great food");
    assertThat(review.privateNote()).isEqualTo("Regular");
    assertThat(review.guestName()).isEqualTo("Ada Lovelace");
    assertThat(review.tags()).containsExactly("Great food", "Friendly");
    assertThat(review.keywords()).containsExactly("steak", "fries");
  }

  private static ReviewInput review(Integer overallRating, String guestId, String guestName) {
    return new ReviewInput(
        "OPENTABLE",
        "RT-1001",
        "OpenTable",
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
        guestId,
        guestName,
        RAW_RECORD_ID);
  }

  private static CanonicalReview create(ReviewInput input) {
    return CanonicalReview.create(
        LOGICAL_ID, "OPENTABLE", "RT-1001", RAW_RECORD_ID, REVIEWED_AT, REVIEWED_AT, input);
  }
}
