package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Validates the {@code review_classification.aspects jsonb} bind path end-to-end: the entity
 * serializes {@link AspectSentiment}s to a JSON string on write, Hibernate stores it in a {@code
 * jsonb} column, and reading it back yields the same aspects. Closes the gap left by the pure-JUnit
 * entity test (which never exercises the Hibernate bind).
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ReviewClassificationPersistenceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-10-12T12:36:59Z");

  @Autowired JdbcTemplate jdbc;
  @Autowired ReviewClassificationRepository repository;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table review_classification");
  }

  @Test
  void aspectsJsonRoundTripsThroughJsonb() throws Exception {
    List<AspectSentiment> aspects =
        List.of(
            new AspectSentiment("service", "POSITIVE", new BigDecimal("0.95")),
            new AspectSentiment("food", "NEUTRAL", new BigDecimal("0.60")));
    ReviewClassification classification =
        new ReviewClassification(
            new Sentiment("POSITIVE", new BigDecimal("0.92")), new BigDecimal("4.2"), aspects);

    repository.save(ReviewClassificationEntity.create("OT-1", classification, NOW));

    ReviewClassificationEntity reloaded = repository.findById("OT-1").orElseThrow();
    assertThat(reloaded.sentiment()).isEqualTo("POSITIVE");
    assertThat(reloaded.sentimentConfidence()).isEqualByComparingTo(new BigDecimal("0.92"));
    assertThat(reloaded.predictedRating()).isEqualByComparingTo(new BigDecimal("4.2"));
    assertThat(reloaded.classifiedAt()).isEqualTo(NOW);

    List<AspectSentiment> parsed =
        MAPPER.readValue(reloaded.aspectsJson(), new TypeReference<List<AspectSentiment>>() {});
    assertThat(parsed).extracting(AspectSentiment::aspect).containsExactly("service", "food");
    assertThat(parsed).extracting(AspectSentiment::label).containsExactly("POSITIVE", "NEUTRAL");
    assertThat(parsed.get(0).confidence()).isEqualByComparingTo(new BigDecimal("0.95"));
    assertThat(parsed.get(1).confidence()).isEqualByComparingTo(new BigDecimal("0.60"));
  }
}
