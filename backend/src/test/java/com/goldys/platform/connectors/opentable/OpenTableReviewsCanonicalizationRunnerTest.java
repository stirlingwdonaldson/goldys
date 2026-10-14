package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalGuestIngest;
import com.goldys.platform.canonical.CanonicalReviewIngest;
import com.goldys.platform.canonical.ReviewInput;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenTableReviewsCanonicalizationRunnerTest {

  private final RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
  private final OpenTableReviewJsonParser parser =
      new OpenTableReviewJsonParser(new ObjectMapper());
  private final CanonicalReviewIngest reviewIngest = mock(CanonicalReviewIngest.class);
  private final CanonicalGuestIngest guestIngest = mock(CanonicalGuestIngest.class);

  private final OpenTableReviewsCanonicalizationRunner runner =
      new OpenTableReviewsCanonicalizationRunner(rawLedger, parser, reviewIngest, guestIngest);

  @Test
  void replaysStoredReviewsIntoCanonicalAndEnsuresGuests() throws Exception {
    byte[] page = fixture();
    UUID pageId = UUID.randomUUID();
    when(rawLedger.rawPayloadsForFetcher("OPENTABLE", "opentable-reviews"))
        .thenReturn(List.of(new RawLedgerQuery.RawPayload(pageId, page)));

    runner.run(null);

    ArgumentCaptor<ReviewInput> input = ArgumentCaptor.forClass(ReviewInput.class);
    verify(reviewIngest, times(3)).record(input.capture());
    assertThat(input.getAllValues())
        .extracting(ReviewInput::reviewId)
        .containsExactly(
            "OT-140126-39400-210054652671",
            "RT-140126-6acd84c718ae480001d1f4cf",
            "RT-140126-6acb76fff799b70001c54866");

    // Only the OpenTable review carries a Guest ID, so ensure is called exactly once.
    verify(guestIngest).ensure("68f9d1af3720e3c69efd313c", "Mackenzie", pageId);
  }

  @Test
  void noRawRecordsIsANoop() {
    when(rawLedger.rawPayloadsForFetcher("OPENTABLE", "opentable-reviews")).thenReturn(List.of());

    runner.run(null);

    verify(reviewIngest, never()).record(any());
    verify(guestIngest, never()).ensure(any(), any(), any());
  }

  private static byte[] fixture() throws Exception {
    return Files.readAllBytes(Path.of("src/test/resources/fixtures/opentable/reviews_sample.json"));
  }
}
