package com.goldys.platform.classification;

import java.util.List;

/**
 * The output of {@link EmbeddingClient#cluster}: the dendrogram nodes, the flat label per input
 * vector, and a two-dimensional projection of each vector for display.
 *
 * <p>{@code labels} is aligned by index with the vectors passed to {@code cluster}; {@code
 * projection} is aligned the same way.
 */
public record ClusterResult(
    List<ClusterNode> nodes, List<Integer> labels, List<Point2d> projection) {}
