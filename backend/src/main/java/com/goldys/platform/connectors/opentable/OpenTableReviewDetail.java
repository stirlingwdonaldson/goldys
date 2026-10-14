package com.goldys.platform.connectors.opentable;

import java.time.Instant;
import java.util.List;

/**
 * A single review row parsed from the OpenTable GuestCenter reviews export.
 *
 * <p>Every field after {@code source} is nullable: an optional column that is blank in the CSV maps
 * to {@code null} (integers/booleans) or an empty list ({@code tags}/{@code keywords}). The raw
 * {@code Review ID} is the identity, {@code source} distinguishes {@code OpenTable} from {@code
 * Google} reviews, and {@code reviewDate}/{@code visitDate} are parsed in the configured OpenTable
 * timezone.
 *
 * @param reviewId the {@code Review ID} column (identity)
 * @param source {@code OpenTable} or {@code Google} from the {@code Source} column
 * @param reviewDate the {@code Review Date} column, parsed in the configured OpenTable timezone
 * @param visitDate the {@code Visit Date} column, or {@code null} when blank
 * @param guestName the {@code Guest Name} column, or {@code null} when blank
 * @param guestId the {@code Guest ID} column, or {@code null} when blank
 * @param overallRating the {@code Overall Rating} column, or {@code null} when blank
 * @param foodRating the {@code Food} sub-rating, or {@code null} when blank
 * @param serviceRating the {@code Service} sub-rating, or {@code null} when blank
 * @param ambienceRating the {@code Ambience} sub-rating, or {@code null} when blank
 * @param valueRating the {@code Value} sub-rating, or {@code null} when blank
 * @param noiseLabel the free-text {@code Noise} column, or {@code null} when blank
 * @param wouldRecommend the {@code Would Recommend} column ({@code Yes}/{@code No}), or {@code
 *     null} when blank
 * @param comment the {@code Comment} column, or {@code null} when blank; may span multiple lines
 * @param privateNote the {@code Private Note from Guest} column, or {@code null} when blank
 * @param tags the {@code Tags Added by Diner} column split on commas; empty when blank
 * @param keywords the {@code Keywords} column split on commas; empty when blank
 */
public record OpenTableReviewDetail(
    String reviewId,
    String source,
    Instant reviewDate,
    Instant visitDate,
    String guestName,
    String guestId,
    Integer overallRating,
    Integer foodRating,
    Integer serviceRating,
    Integer ambienceRating,
    Integer valueRating,
    String noiseLabel,
    Boolean wouldRecommend,
    String comment,
    String privateNote,
    List<String> tags,
    List<String> keywords) {}
