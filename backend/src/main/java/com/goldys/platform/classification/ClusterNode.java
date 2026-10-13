package com.goldys.platform.classification;

/**
 * One node of a clustering dendrogram. Leaves carry a {@link #productIndex()}; internal nodes carry
 * a {@link #height()} at which their two children merge. {@code parentId} is {@code null} for the
 * root.
 *
 * @param id the node identifier, unique within its {@link ClusterResult}
 * @param parentId the parent node's id, or {@code null} for the root
 * @param isLeaf whether this node is a leaf
 * @param productIndex the input vector index for a leaf, or {@code null} for an internal node
 * @param height the merge height for an internal node (zero for leaves)
 * @param size the number of leaves under this node
 */
public record ClusterNode(
    int id, Integer parentId, boolean isLeaf, Integer productIndex, double height, int size) {}
