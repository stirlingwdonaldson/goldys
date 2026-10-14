package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure-JUnit coverage of the {@link ReviewClassificationEntity} factory and accessors. */
class ReviewClassificationEntityTest {

  @Test
  void createSerializesAspectsToJson() {
    Instant now = Instant.parse("2026-10-12T12:36:59Z");
    ReviewClassification classification =
        new ReviewClassification(
            new Sentiment("NEGATIVE", new BigDecimal("0.94")),
            new BigDecimal("1.8"),
            List.of(new AspectSentiment("service", "NEGATIVE", new BigDecimal("0.97"))));

    ReviewClassificationEntity entity =
        ReviewClassificationEntity.create("OT-140126-39943-230066535913", classification, now);

    assertThat(entity.reviewId()).isEqualTo("OT-140126-39943-230066535913");
    assertThat(entity.sentiment()).isEqualTo("NEGATIVE");
    assertThat(entity.sentimentConfidence()).isEqualByComparingTo(new BigDecimal("0.94"));
    assertThat(entity.predictedRating()).isEqualByComparingTo(new BigDecimal("1.8"));
    assertThat(entity.aspectsJson()).contains("\"aspect\":\"service\"");
    assertThat(entity.classifiedAt()).isEqualTo(now);
  }

  @Test
  void predictedRatingMayBeNull() {
    Instant now = Instant.parse("2026-10-12T12:36:59Z");
    ReviewClassification classification =
        new ReviewClassification(
            new Sentiment("POSITIVE", new BigDecimal("0.90")), null, List.of());

    ReviewClassificationEntity entity =
        ReviewClassificationEntity.create("OT-140126-39943-230066535913", classification, now);

    assertThat(entity.sentiment()).isEqualTo("POSITIVE");
    assertThat(entity.predictedRating()).isNull();
  }
}
