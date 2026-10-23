package com.goldys.platform.semantic.query;

import java.time.Instant;
import java.util.UUID;

/** A saved query's list entry. */
public record SavedQuerySummary(
    UUID id, String title, int revision, Instant createdAt, Instant updatedAt) {}
