package com.goldys.platform.semantic;

import java.util.UUID;

/**
 * One curated category in the taxonomy, with the number of products currently assigned to it.
 * {@code groupName} is null for flat (top-level) categories.
 */
public record CategoryNode(UUID id, String name, String groupName, int productCount) {}
