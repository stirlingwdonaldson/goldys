package com.goldys.platform.semantic.query;

import java.time.Instant;

/** One revision snapshot of a saved query. */
public record SavedQueryRevisionView(int revision, String createdBy, Instant createdAt) {}
