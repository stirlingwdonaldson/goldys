package com.goldys.platform.classification;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single taxonomy proposal from a {@link ClusterNamer}: a short category name, an optional parent
 * group (null = flat), and an optional hint explaining any merge/split reasoning.
 *
 * <p>The wire field for the group is {@code group} (the shorter name the model's JSON schema uses);
 * the Java component stays {@code groupName} to match the surrounding {@code groupName} accessors.
 */
public record SuggestedCategory(
    String name, @JsonProperty("group") String groupName, String hint) {}
