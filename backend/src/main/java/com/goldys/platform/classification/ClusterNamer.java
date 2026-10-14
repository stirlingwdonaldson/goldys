package com.goldys.platform.classification;

import java.util.List;

/**
 * Proposes a human-readable category name (and optional group) for each flat-cut cluster of a
 * clustering run, plus merge/split context. This is the generative counterpart to {@link
 * ProductClassifier}: Jev decides which of the confirmed categories a product belongs to, while a
 * {@code ClusterNamer} drafts the category names themselves.
 */
public interface ClusterNamer {

  List<SuggestedCategory> suggest(List<ClusterSummary> clusters);
}
