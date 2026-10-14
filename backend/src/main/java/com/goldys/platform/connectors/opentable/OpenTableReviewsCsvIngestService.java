package com.goldys.platform.connectors.opentable;

import com.goldys.platform.canonical.CanonicalGuestIngest;
import com.goldys.platform.canonical.CanonicalReviewIngest;
import com.goldys.platform.canonical.ReviewInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for the OpenTable reviews export: persist the dropped CSV
 * byte-faithfully, then parse and canonicalize each review row, ensuring each review's guest exists
 * first. The operator exports GuestCenter's Reviews CSV and POSTs it to {@code
 * /api/ingest/opentable-reviews}; there is no automated browser pull.
 *
 * <p>Supersession is not handled here: {@link CanonicalReviewIngest} closes and re-inserts a fact
 * when a re-uploaded row's fields have changed, so this service simply records every row it parses.
 */
@Service
public class OpenTableReviewsCsvIngestService {
  private final IngestionService ingestion;
  private final OpenTableReviewsCsvParser parser;
  private final CanonicalReviewIngest reviewIngest;
  private final CanonicalGuestIngest guestIngest;

  public OpenTableReviewsCsvIngestService(
      IngestionService ingestion,
      OpenTableReviewsCsvParser parser,
      CanonicalReviewIngest reviewIngest,
      CanonicalGuestIngest guestIngest) {
    this.ingestion = ingestion;
    this.parser = parser;
    this.reviewIngest = reviewIngest;
    this.guestIngest = guestIngest;
  }

  public void ingest(byte[] csv) {
    UUID rawId =
        ingestion.ingestPush(
            "OPENTABLE",
            "opentable-reviews",
            FetchMethod.FILE_EXPORT,
            "text/csv",
            csv,
            StandardCharsets.UTF_8.name(),
            "opentable-reviews");

    for (OpenTableReviewDetail detail : parser.parse(csv)) {
      if (detail.guestId() != null) {
        guestIngest.ensure(detail.guestId(), detail.guestName(), rawId);
      }
      reviewIngest.record(toInput(detail, rawId));
    }
  }

  private static ReviewInput toInput(OpenTableReviewDetail detail, UUID rawId) {
    return new ReviewInput(
        "OPENTABLE",
        detail.reviewId(),
        detail.source(),
        detail.reviewDate(),
        detail.visitDate(),
        detail.overallRating(),
        detail.foodRating(),
        detail.serviceRating(),
        detail.ambienceRating(),
        detail.valueRating(),
        detail.noiseLabel(),
        detail.wouldRecommend(),
        detail.comment(),
        detail.privateNote(),
        detail.tags(),
        detail.keywords(),
        detail.guestId(),
        detail.guestName(),
        rawId);
  }
}
