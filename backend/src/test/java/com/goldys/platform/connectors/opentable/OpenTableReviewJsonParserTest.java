package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.ReviewInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenTableReviewJsonParserTest {

  private final OpenTableReviewJsonParser parser =
      new OpenTableReviewJsonParser(new ObjectMapper());

  @Test
  void parsesEachReviewIntoCanonicalInput() throws Exception {
    UUID rawId = UUID.randomUUID();
    List<ReviewInput> reviews = parser.parse(fixture(), rawId);

    assertThat(reviews).hasSize(3);
    assertThat(reviews).allSatisfy(r -> assertThat(r.sourceSystem()).isEqualTo("OPENTABLE"));
    assertThat(reviews).allSatisfy(r -> assertThat(r.rawRecordId()).isEqualTo(rawId));

    ReviewInput openTable = reviews.get(0);
    assertThat(openTable.reviewId()).isEqualTo("OT-140126-39400-210054652671");
    assertThat(openTable.source()).isEqualTo("OpenTable");
    assertThat(openTable.reviewedAt()).isEqualTo(Instant.parse("2026-10-13T02:06:40Z"));
    assertThat(openTable.visitedAt()).isEqualTo(Instant.parse("2026-10-12T18:30:00+11:00"));
    assertThat(openTable.overallRating()).isEqualTo(5);
    assertThat(openTable.foodRating()).isEqualTo(5);
    assertThat(openTable.serviceRating()).isEqualTo(5);
    assertThat(openTable.ambienceRating()).isEqualTo(5);
    assertThat(openTable.valueRating()).isEqualTo(5);
    assertThat(openTable.noiseLabel()).isEqualTo("Energetic");
    assertThat(openTable.wouldRecommend()).isTrue();
    assertThat(openTable.comment())
        .isEqualTo("We went to pub trivia, the food and trivia was fab!");
    assertThat(openTable.privateNote()).isNull();
    assertThat(openTable.tags()).containsExactly("Neighbourhood gem", "Hot spot", "Lively");
    assertThat(openTable.guestId()).isEqualTo("68f9d1af3720e3c69efd313c");
    assertThat(openTable.guestName()).isEqualTo("Mackenzie");
  }

  @Test
  void normalisesWholeFloatRatingsAndZeroSubRatingsToNull() throws Exception {
    List<ReviewInput> reviews = parser.parse(fixture(), UUID.randomUUID());

    ReviewInput google = reviews.get(1);
    assertThat(google.overallRating()).isEqualTo(5);
    // Google review carries no sub-ratings (0.0): all normalise to null, not 0.
    assertThat(google.foodRating()).isNull();
    assertThat(google.serviceRating()).isNull();
    assertThat(google.ambienceRating()).isNull();
    assertThat(google.valueRating()).isNull();
    assertThat(google.wouldRecommend()).isNull();
    assertThat(google.guestId()).isNull();
    assertThat(google.keywords()).containsExactly("parma");

    // Absent sub-ratings (null) also map to null.
    ReviewInput facebook = reviews.get(2);
    assertThat(facebook.source()).isEqualTo("Facebook");
    assertThat(facebook.overallRating()).isEqualTo(4);
    assertThat(facebook.serviceRating()).isNull();
    assertThat(facebook.valueRating()).isNull();
    assertThat(facebook.ambienceRating()).isEqualTo(5);
    assertThat(facebook.privateNote()).isNull();
  }

  @Test
  void missingDataArrayFailsLoudly() {
    assertThatThrownBy(
            () -> parser.parse("{\"meta\":{\"totalCount\":0}}".getBytes(), UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }

  @Test
  void missingIdentityFailsLoudly() {
    byte[] body =
        ("{\"meta\":{\"totalCount\":1},\"data\":[{\"source\":\"Google\","
                + "\"reviewDate\":\"2026-10-13T00:54:05Z\"}]}")
            .getBytes();
    assertThatThrownBy(() -> parser.parse(body, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }

  @Test
  void unparseableReviewDateFailsLoudly() {
    byte[] body =
        ("{\"meta\":{\"totalCount\":1},\"data\":[{\"id\":\"OT-1\",\"source\":\"OpenTable\","
                + "\"reviewDate\":\"not-a-date\"}]}")
            .getBytes();
    assertThatThrownBy(() -> parser.parse(body, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }

  private static byte[] fixture() throws Exception {
    return Files.readAllBytes(Path.of("src/test/resources/fixtures/opentable/reviews_sample.json"));
  }
}
