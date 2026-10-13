package com.goldys.platform.classification;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A product→category assignment as surfaced to the UI: the raw assignment plus the resolved
 * category name and group. {@code categoryName}/{@code groupName} are null when the product is
 * unassigned (or its category has been deleted); {@code source} is null when unassigned.
 */
public record AssignmentView(
    String productNameKey,
    UUID categoryId,
    String categoryName,
    String groupName,
    BigDecimal confidence,
    String source,
    String status) {}
