package com.goldys.platform.classification;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds the free-text description the review classifier sees for one review: the guest's public
 * comment, their private note, and the diner tags, joined in that order.
 *
 * <p>A review whose text is entirely blank still yields a non-empty feature string carrying the
 * source and overall rating (Review Focus #1), so the classifier always has something to read and
 * {@link ReviewClassificationService} never has to special-case a null/empty request.
 */
@Component
public class ReviewFeatureTextExtractor {

  /**
   * Joins the review's comment, private note and tags; when that is blank, falls back to a
   * non-empty string containing the source and rating. Never returns {@code null} or an empty
   * string.
   */
  public String featureText(
      String source, Integer overallRating, String comment, String privateNote, List<String> tags) {
    List<String> parts = new ArrayList<>(3);
    if (notBlank(comment)) {
      parts.add(comment.trim());
    }
    if (notBlank(privateNote)) {
      parts.add(privateNote.trim());
    }
    if (tags != null) {
      for (String tag : tags) {
        if (notBlank(tag)) {
          parts.add(tag.trim());
        }
      }
    }
    if (!parts.isEmpty()) {
      return String.join(" ", parts);
    }

    String rating = overallRating == null ? "" : String.valueOf(overallRating);
    String fallback = String.join(" ", notBlank(source) ? source.trim() : "", rating).trim();
    return fallback.isEmpty() ? "review" : fallback;
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
