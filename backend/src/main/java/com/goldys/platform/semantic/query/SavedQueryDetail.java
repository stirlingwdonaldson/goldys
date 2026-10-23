package com.goldys.platform.semantic.query;

import java.time.Instant;
import java.util.UUID;

/** A saved query's full definition, including its current plan. */
public record SavedQueryDetail(
    UUID id, String title, int revision, QueryPlan plan, Instant createdAt, Instant updatedAt) {}
