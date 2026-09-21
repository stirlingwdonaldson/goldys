package com.goldys.platform.ingestion;

/**
 * Read-only ingestion-health metrics for the dashboard.
 *
 * @param completenessPercent share of completed runs that ran cleanly ({@code SUCCESS} or {@code
 *     NO_NEW_DATA}), 0–100, or {@code null} when there are no completed runs yet
 * @param timeToDetectFailure mean duration of runs that recorded a failure, formatted for display
 *     (e.g. {@code "42m avg"}), or {@code null} when no run has failed
 */
public record IngestionHealth(Integer completenessPercent, String timeToDetectFailure) {}
