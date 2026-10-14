package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One version of a source's review fact. Fact fields are immutable; a correction closes this row
 * and inserts a successor sharing the same logical identity (the review id). The source-system
 * Guest ID is denormalized into {@link #guestId} as a {@code nameUUIDFromBytes("guest:" + id)} UUID
 * so the review links to the {@link CanonicalGuest} populated this slice.
 */
@Entity
@Table(name = "canonical_review")
class CanonicalReview extends BitemporalEntity {
  @Column(name = "source", nullable = false, updatable = false, length = 16)
  private String source;

  @Column(name = "guest_id", updatable = false)
  private UUID guestId;

  @Column(name = "guest_name", updatable = false, length = 1024)
  private String guestName;

  @Column(name = "reviewed_at", nullable = false, updatable = false)
  private Instant reviewedAt;

  @Column(name = "visited_at", updatable = false)
  private Instant visitedAt;

  @Column(name = "overall_rating", updatable = false)
  private Integer overallRating;

  @Column(name = "food_rating", updatable = false)
  private Integer foodRating;

  @Column(name = "service_rating", updatable = false)
  private Integer serviceRating;

  @Column(name = "ambience_rating", updatable = false)
  private Integer ambienceRating;

  @Column(name = "value_rating", updatable = false)
  private Integer valueRating;

  @Column(name = "noise_label", updatable = false, length = 1024)
  private String noiseLabel;

  @Column(name = "would_recommend", updatable = false)
  private Boolean wouldRecommend;

  @Column(name = "comment", updatable = false)
  private String comment;

  @Column(name = "private_note", updatable = false)
  private String privateNote;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "tags", updatable = false, columnDefinition = "text[]")
  private String[] tags;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "keywords", updatable = false, columnDefinition = "text[]")
  private String[] keywords;

  protected CanonicalReview() {}

  private CanonicalReview(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      ReviewInput input) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.source = input.source();
    this.guestId = computeGuestId(input.guestId());
    this.guestName = input.guestName();
    this.reviewedAt = input.reviewedAt();
    this.visitedAt = input.visitedAt();
    this.overallRating = input.overallRating();
    this.foodRating = input.foodRating();
    this.serviceRating = input.serviceRating();
    this.ambienceRating = input.ambienceRating();
    this.valueRating = input.valueRating();
    this.noiseLabel = input.noiseLabel();
    this.wouldRecommend = input.wouldRecommend();
    this.comment = input.comment();
    this.privateNote = input.privateNote();
    this.tags = input.tags() == null ? null : input.tags().toArray(String[]::new);
    this.keywords = input.keywords() == null ? null : input.keywords().toArray(String[]::new);
  }

  static CanonicalReview create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      ReviewInput input) {
    return new CanonicalReview(
        logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt, input);
  }

  boolean sameFact(ReviewInput input) {
    return Objects.equals(source, input.source())
        && Objects.equals(guestId, computeGuestId(input.guestId()))
        && Objects.equals(guestName, input.guestName())
        && Objects.equals(reviewedAt, input.reviewedAt())
        && Objects.equals(visitedAt, input.visitedAt())
        && Objects.equals(overallRating, input.overallRating())
        && Objects.equals(foodRating, input.foodRating())
        && Objects.equals(serviceRating, input.serviceRating())
        && Objects.equals(ambienceRating, input.ambienceRating())
        && Objects.equals(valueRating, input.valueRating())
        && Objects.equals(noiseLabel, input.noiseLabel())
        && Objects.equals(wouldRecommend, input.wouldRecommend())
        && Objects.equals(comment, input.comment())
        && Objects.equals(privateNote, input.privateNote())
        && Arrays.equals(tags, toArray(input.tags()))
        && Arrays.equals(keywords, toArray(input.keywords()));
  }

  private static UUID computeGuestId(String guestId) {
    return guestId == null
        ? null
        : UUID.nameUUIDFromBytes(("guest:" + guestId).getBytes(StandardCharsets.UTF_8));
  }

  private static String[] toArray(List<String> values) {
    return values == null ? null : values.toArray(String[]::new);
  }

  String source() {
    return source;
  }

  UUID guestId() {
    return guestId;
  }

  String guestName() {
    return guestName;
  }

  Instant reviewedAt() {
    return reviewedAt;
  }

  Instant visitedAt() {
    return visitedAt;
  }

  Integer overallRating() {
    return overallRating;
  }

  Integer foodRating() {
    return foodRating;
  }

  Integer serviceRating() {
    return serviceRating;
  }

  Integer ambienceRating() {
    return ambienceRating;
  }

  Integer valueRating() {
    return valueRating;
  }

  String noiseLabel() {
    return noiseLabel;
  }

  Boolean wouldRecommend() {
    return wouldRecommend;
  }

  String comment() {
    return comment;
  }

  String privateNote() {
    return privateNote;
  }

  List<String> tags() {
    return tags == null ? null : List.of(tags);
  }

  List<String> keywords() {
    return keywords == null ? null : List.of(keywords);
  }
}
