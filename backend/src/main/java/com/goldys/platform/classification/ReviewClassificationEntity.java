package com.goldys.platform.classification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The persisted, read-only ML classification of one review, keyed directly on the logical review id
 * (the {@code Review ID} string). Derived state, not bitemporal: re-classification upserts the
 * single row for a review (latest-wins), so there is no valid_from/valid_to/superseded_at here.
 *
 * <p>The {@code aspects} list is serialized to the {@code aspects jsonb} column as raw JSON text;
 * the {@code String aspectsJson} field holds that text (see {@link #aspectsJson()}).
 */
@Entity
@Table(name = "review_classification")
class ReviewClassificationEntity {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Id
  @Column(name = "review_id", nullable = false, length = 512)
  private String reviewId;

  @Column(name = "sentiment", nullable = false, length = 16)
  private String sentiment;

  @Column(name = "sentiment_confidence", nullable = false, precision = 6, scale = 4)
  private BigDecimal sentimentConfidence;

  @Column(name = "predicted_rating", precision = 3, scale = 2)
  private BigDecimal predictedRating;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "aspects", nullable = false, columnDefinition = "jsonb")
  private String aspectsJson;

  @Column(name = "classified_at", nullable = false)
  private Instant classifiedAt;

  protected ReviewClassificationEntity() {}

  private ReviewClassificationEntity(String reviewId, ReviewClassification c, Instant now) {
    this.reviewId = Objects.requireNonNull(reviewId, "reviewId");
    Sentiment s = Objects.requireNonNull(c.sentiment(), "sentiment");
    this.sentiment = Objects.requireNonNull(s.label(), "sentiment.label");
    this.sentimentConfidence = Objects.requireNonNull(s.confidence(), "sentiment.confidence");
    this.predictedRating = c.predictedRating();
    this.aspectsJson = serializeAspects(c.aspects());
    this.classifiedAt = Objects.requireNonNull(now, "now");
  }

  public static ReviewClassificationEntity create(
      String reviewId, ReviewClassification c, Instant now) {
    return new ReviewClassificationEntity(reviewId, c, now);
  }

  private static String serializeAspects(List<AspectSentiment> aspects) {
    try {
      return MAPPER.writeValueAsString(aspects == null ? List.of() : aspects);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize review aspects", e);
    }
  }

  public String reviewId() {
    return reviewId;
  }

  public String sentiment() {
    return sentiment;
  }

  public BigDecimal sentimentConfidence() {
    return sentimentConfidence;
  }

  public BigDecimal predictedRating() {
    return predictedRating;
  }

  public String aspectsJson() {
    return aspectsJson;
  }

  public Instant classifiedAt() {
    return classifiedAt;
  }
}
