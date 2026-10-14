package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalGuestIngest;
import com.goldys.platform.canonical.CanonicalReviewIngest;
import com.goldys.platform.canonical.ReviewInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenTableReviewsCsvIngestServiceTest {

  private final IngestionService ingestion = mock(IngestionService.class);
  private final OpenTableReviewsCsvParser parser =
      new OpenTableReviewsCsvParser("Australia/Melbourne");
  private final CanonicalReviewIngest reviewIngest = mock(CanonicalReviewIngest.class);
  private final CanonicalGuestIngest guestIngest = mock(CanonicalGuestIngest.class);
  private final OpenTableReviewsCsvIngestService service =
      new OpenTableReviewsCsvIngestService(ingestion, parser, reviewIngest, guestIngest);

  @Test
  void storesRawBytesAndRejectsMalformedHeader() throws Exception {
    byte[] csv = fixture();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());

    service.ingest(csv);

    ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
    verify(ingestion)
        .ingestPush(
            eq("OPENTABLE"),
            eq("opentable-reviews"),
            eq(FetchMethod.FILE_EXPORT),
            eq("text/csv"),
            bytes.capture(),
            eq(StandardCharsets.UTF_8.name()),
            eq("opentable-reviews"));
    assertThat(bytes.getValue()).isEqualTo(csv);

    // Validation only: a malformed header is rejected loudly rather than silently ingested.
    assertThatThrownBy(() -> service.ingest(badHeader()))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }

  @Test
  void ingestCanonicalizesEachReview() throws Exception {
    byte[] csv = fixture();
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);

    service.ingest(csv);

    ArgumentCaptor<ReviewInput> input = ArgumentCaptor.forClass(ReviewInput.class);
    verify(reviewIngest, times(3)).record(input.capture());
    List<ReviewInput> values = input.getAllValues();

    ReviewInput first = values.get(0);
    assertThat(first.sourceSystem()).isEqualTo("OPENTABLE");
    assertThat(first.reviewId()).isEqualTo("OT-140126-39320-270054226027");
    assertThat(first.source()).isEqualTo("OpenTable");
    assertThat(first.overallRating()).isEqualTo(5);
    assertThat(first.rawRecordId()).isEqualTo(rawId);

    ReviewInput google = values.get(2);
    assertThat(google.reviewId()).isEqualTo("RT-140126-6acb76fff799b70001c54866");
    assertThat(google.source()).isEqualTo("Google");
    assertThat(google.guestId()).isNull();

    assertThat(values).allSatisfy(v -> assertThat(v.sourceSystem()).isEqualTo("OPENTABLE"));
    assertThat(values).allSatisfy(v -> assertThat(v.rawRecordId()).isEqualTo(rawId));
  }

  @Test
  void ingestEnsuresGuestBeforeReview() throws Exception {
    byte[] csv = fixture();
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);

    // Capture the interleaving of ensure/record calls to prove ensure precedes its row's record.
    List<String> events = new ArrayList<>();
    doAnswer(
            inv -> {
              events.add("ensure:" + inv.getArgument(0));
              return null;
            })
        .when(guestIngest)
        .ensure(any(String.class), any(String.class), any(UUID.class));
    doAnswer(
            inv -> {
              events.add("record");
              return null;
            })
        .when(reviewIngest)
        .record(any(ReviewInput.class));

    service.ingest(csv);

    assertThat(events)
        .containsExactly(
            "ensure:6a8e7ffcfb049e005767deab",
            "record",
            "ensure:6ac9e28dea382244a46e5053",
            "record",
            // The Google review carries no Guest ID, so its record is not preceded by an ensure.
            "record");
  }

  @Test
  void blankGuestIdSkipsEnsure() throws Exception {
    byte[] csv = fixture();
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);

    service.ingest(csv);

    // Only the two OpenTable rows carry a Guest ID; the Google row must not trigger an ensure.
    verify(guestIngest, times(2)).ensure(any(String.class), any(String.class), any(UUID.class));

    ArgumentCaptor<ReviewInput> input = ArgumentCaptor.forClass(ReviewInput.class);
    verify(reviewIngest, times(3)).record(input.capture());
    assertThat(input.getAllValues().get(2).guestId()).isNull();
  }

  private static byte[] fixture() throws Exception {
    return Files.readAllBytes(Path.of("src/test/resources/fixtures/opentable/reviews_sample.csv"));
  }

  private static byte[] badHeader() {
    // Missing the required "Review Date" column: a shape change must fail loudly.
    return "Review ID,Source\nOT-1,OpenTable\n".getBytes(StandardCharsets.UTF_8);
  }
}
