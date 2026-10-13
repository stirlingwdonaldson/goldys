package com.goldys.platform.classification;

import java.util.List;

/**
 * The port through which the platform calls the self-hosted embedding service. Adapters are
 * swappable behind this interface; callers depend only on the embedding and clustering operations,
 * not on how the vectors are produced.
 */
public interface EmbeddingClient {

  /**
   * Embed each input text, returning one vector per text in the same order. The vectors need not be
   * normalised; consumers treat them as opaque points.
   */
  List<float[]> embed(List<String> texts);

  /**
   * Cluster the supplied vectors, returning the dendrogram nodes, the flat leaf labels, and a
   * two-dimensional projection for display.
   */
  ClusterResult cluster(List<float[]> vectors);
}
