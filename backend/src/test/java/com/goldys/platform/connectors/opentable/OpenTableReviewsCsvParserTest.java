package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenTableReviewsCsvParserTest {

  private final OpenTableReviewsCsvParser parser =
      new OpenTableReviewsCsvParser("Australia/Melbourne");

  private byte[] fixture() throws Exception {
    return Files.readAllBytes(Path.of("src/test/resources/fixtures/opentable/reviews_sample.csv"));
  }

  @Test
  void mapsReviewIdentity() throws Exception {
    List<OpenTableReview> rows = parser.parse(fixture());

    assertThat(rows).hasSize(3);

    OpenTableReview first = rows.get(0);
    assertThat(first.reviewId()).isEqualTo("OT-140126-39320-270054226027");
    assertThat(first.source()).isEqualTo("OpenTable");
    assertThat(first.reviewDate()).isEqualTo(Instant.parse("2026-10-12T10:56:54Z"));
    assertThat(first.overallRating()).isEqualTo(5);
    assertThat(first.comment())
        .isEqualTo("Love how the place has evolved from the early days of Covid to today.");

    OpenTableReview google = rows.get(2);
    assertThat(google.reviewId()).isEqualTo("RT-140126-6acb76fff799b70001c54866");
    assertThat(google.source()).isEqualTo("Google");
    assertThat(google.reviewDate()).isEqualTo(Instant.parse("2026-10-11T11:30:42Z"));
    assertThat(google.overallRating()).isEqualTo(5);
  }

  @Test
  void handlesMultilineComment() throws Exception {
    List<OpenTableReview> rows = parser.parse(fixture());

    OpenTableReview mary = rows.get(1);
    assertThat(mary.reviewId()).isEqualTo("OT-140126-39943-230066535913");
    assertThat(mary.comment()).contains("Disinterested wait staff");
    assertThat(mary.comment()).contains("Won’t be back.");
    assertThat(mary.comment()).contains("Girl serving at the bar friendly");
    assertThat(mary.comment()).contains("\n");
  }

  @Test
  void missingColumnThrows() {
    String csv = "Review ID,Source,Review Date,Comment\nOT-1,OpenTable,2026-10-12 21:56:54,nice\n";

    assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }

  @Test
  void blankRatingAndCommentToNull() {
    String csv =
        "Review ID,Source,Review Date,Overall Rating,Comment\n"
            + "OT-1,OpenTable,2026-10-12 21:56:54,,nice\n"
            + "OT-2,Google,2026-10-11 22:30:42,5,\n";

    List<OpenTableReview> rows = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(rows.get(0).overallRating()).isNull();
    assertThat(rows.get(0).comment()).isEqualTo("nice");
    assertThat(rows.get(1).overallRating()).isEqualTo(5);
    assertThat(rows.get(1).comment()).isNull();
  }
}
