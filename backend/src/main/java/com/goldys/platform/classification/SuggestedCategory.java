package com.goldys.platform.classification;

/**
 * A single taxonomy proposal from a {@link ClusterNamer}: a short category name, an optional parent
 * group (null = flat), and an optional hint explaining any merge/split reasoning.
 */
public record SuggestedCategory(String name, String groupName, String hint) {}
