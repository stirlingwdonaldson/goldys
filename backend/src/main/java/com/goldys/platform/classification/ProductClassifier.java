package com.goldys.platform.classification;

/**
 * The port through which the platform assigns a product to one of the candidate categories. The
 * concrete implementation is swappable (a local model, a hosted service, or a heuristic) behind
 * this interface.
 */
public interface ProductClassifier {

  /** Classify a single product against the supplied candidate categories. */
  ProductClassification classify(ProductClassificationRequest request);
}
