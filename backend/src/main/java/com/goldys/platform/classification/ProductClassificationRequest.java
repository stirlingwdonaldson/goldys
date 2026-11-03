package com.goldys.platform.classification;

import java.util.List;

public record ProductClassificationRequest(
    String featureText, List<CategoryOption> options, String businessContext) {
  public ProductClassificationRequest(String featureText, List<CategoryOption> options) {
    this(featureText, options, ClassificationContext.TEXT);
  }
}
