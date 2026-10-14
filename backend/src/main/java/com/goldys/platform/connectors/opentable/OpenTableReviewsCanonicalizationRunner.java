package com.goldys.platform.connectors.opentable;

import com.goldys.platform.canonical.CanonicalGuestIngest;
import com.goldys.platform.canonical.CanonicalReviewIngest;
import com.goldys.platform.canonical.ReviewInput;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off replay: canonicalizes the {@code opentable-reviews} raw records already in the ledger
 * (from the assisted-session JSON pull) into {@code canonical_review}, populating {@code
 * canonical_guest} from each review's {@code Guest ID}. Runs only when {@code
 * app.opentable-reviews-canonicalize=true}, before the projection seeder, and is idempotent — the
 * canonical services dedupe on the review id / Guest ID, and classification is driven by the {@code
 * ReviewRecorded} event each record publishes.
 *
 * <p>This recovers reviews ingested raw-only before the API connector canonicalized them on sync.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.opentable-reviews-canonicalize", havingValue = "true")
public class OpenTableReviewsCanonicalizationRunner implements ApplicationRunner {
  private static final Logger log =
      LoggerFactory.getLogger(OpenTableReviewsCanonicalizationRunner.class);

  private final RawLedgerQuery rawLedger;
  private final OpenTableReviewJsonParser parser;
  private final CanonicalReviewIngest reviewIngest;
  private final CanonicalGuestIngest guestIngest;

  public OpenTableReviewsCanonicalizationRunner(
      RawLedgerQuery rawLedger,
      OpenTableReviewJsonParser parser,
      CanonicalReviewIngest reviewIngest,
      CanonicalGuestIngest guestIngest) {
    this.rawLedger = rawLedger;
    this.parser = parser;
    this.reviewIngest = reviewIngest;
    this.guestIngest = guestIngest;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<ReviewInput> reviews = new ArrayList<>();
    for (RawLedgerQuery.RawPayload payload :
        rawLedger.rawPayloadsForFetcher(
            OpenTableReviewJsonParser.SOURCE_SYSTEM, "opentable-reviews")) {
      reviews.addAll(parser.parse(payload.bytes(), payload.id()));
    }
    for (ReviewInput review : reviews) {
      if (review.guestId() != null) {
        guestIngest.ensure(review.guestId(), review.guestName(), review.rawRecordId());
      }
      reviewIngest.record(review);
    }
    log.info("OpenTable review canonicalization replayed {} reviews", reviews.size());
  }
}
