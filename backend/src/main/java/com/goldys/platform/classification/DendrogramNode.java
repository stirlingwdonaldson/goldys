package com.goldys.platform.classification;

import java.util.UUID;

/**
 * One node of the agglomerative dendrogram for a classification run. Leaves are products ({@code
 * productNameKey} set); internal nodes are merges ({@code productNameKey} null). {@code
 * mergeHeight} and {@code size} are primitives here: a leaf stores {@code null} in the entity (see
 * {@link ClassificationCluster}) and is mapped to {@code mergeHeight = 0.0} and {@code size = 1}.
 */
public record DendrogramNode(
    UUID id, UUID parentId, boolean isLeaf, String productNameKey, double mergeHeight, int size) {}
