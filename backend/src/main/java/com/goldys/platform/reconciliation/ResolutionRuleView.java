package com.goldys.platform.reconciliation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A public, immutable view of a resolution rule for the API layer. */
public record ResolutionRuleView(
    UUID id,
    String entityType,
    String fieldKey,
    String strategy,
    List<String> sourcePriority,
    String customLogic,
    Instant updatedAt,
    String updatedBy) {}
