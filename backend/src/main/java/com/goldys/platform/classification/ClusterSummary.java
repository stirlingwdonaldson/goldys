package com.goldys.platform.classification;

import java.util.List;

/**
 * A summary of one flat-cut cluster, as handed to a {@link ClusterNamer}. {@code productTexts} are
 * the (capped) feature texts of the products in the cluster — name plus old POS category plus
 * recipe folder — so the model sees enough to name it; {@code size} is the total leaf count.
 *
 * @param index the cluster's position in the summarised list, so the model can reference it in a
 *     merge/split hint
 */
public record ClusterSummary(int index, List<String> productTexts, int size) {}
