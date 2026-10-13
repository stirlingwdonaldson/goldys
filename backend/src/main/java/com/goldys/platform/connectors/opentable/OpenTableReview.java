package com.goldys.platform.connectors.opentable;

import java.time.Instant;

/**
 * A single review row parsed from the OpenTable GuestCenter reviews export.
 *
 * <p>Raw-only for now: the typed rows are parked for the reviews canonicalization slice and are not
 * written anywhere by the ingest pipeline yet.
 *
 * @param reviewId the {@code Review ID} column (identity)
 * @param source {@code OpenTable} or {@code Google} from the {@code Source} column
 * @param reviewDate the {@code Review Date} column, parsed in the configured OpenTable timezone
 * @param overallRating the {@code Overall Rating} column, or {@code null} when blank
 * @param comment the {@code Comment} column, or {@code null} when blank; may span multiple lines
 */
public record OpenTableReview(
    String reviewId, String source, Instant reviewDate, Integer overallRating, String comment) {}
