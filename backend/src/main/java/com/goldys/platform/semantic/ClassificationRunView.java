package com.goldys.platform.semantic;

import java.time.Instant;
import java.util.UUID;

/**
 * The public shape of a persisted classification run, listed newest-first. {@code kind} and {@code
 * status} are the enum names ({@code CLUSTER}/{@code ASSIGN}/{@code FULL} and {@code
 * RUNNING}/{@code SUCCEEDED}/{@code FAILED}/{@code PARTIAL}); {@code productCount} and {@code
 * finishedAt} are null while a run is still in flight, and {@code errorMessage} is null unless the
 * run failed or partially completed.
 */
public record ClassificationRunView(
    UUID id,
    String kind,
    String status,
    Integer productCount,
    Instant startedAt,
    Instant finishedAt,
    String errorMessage) {}
