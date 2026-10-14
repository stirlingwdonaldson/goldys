package com.goldys.platform.classification;

/**
 * The port through which the platform classifies a guest review into an overall sentiment, a
 * predicted rating, and per-aspect sentiments. The concrete implementation is swappable (the Jev
 * hosted service, a local model, or a heuristic) behind this interface.
 */
public interface ReviewClassifier {

  /** Classify a single review from its assembled feature text and the aspects to detect. */
  ReviewClassification classify(ReviewClassificationRequest request);
}
