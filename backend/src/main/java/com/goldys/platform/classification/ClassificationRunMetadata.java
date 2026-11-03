package com.goldys.platform.classification;

import java.time.Instant;
import java.util.UUID;

/** Polling metadata only; snapshots belong to the explicit preview path. */
record ClassificationRunMetadata(
    UUID id,
    ClassificationRunKind kind,
    ClassificationRunStatus status,
    Integer productCount,
    Instant startedAt,
    Instant finishedAt,
    String errorMessage) {}
