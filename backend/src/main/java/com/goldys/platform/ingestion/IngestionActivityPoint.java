package com.goldys.platform.ingestion;

/**
 * One day of connector-run activity for the dashboard trend.
 *
 * @param date the UTC day in ISO-8601 ({@code YYYY-MM-DD})
 * @param clean runs that completed {@code SUCCESS} or {@code NO_NEW_DATA}
 * @param failed runs that completed {@code FAILED} or {@code PARTIAL}
 */
public record IngestionActivityPoint(String date, int clean, int failed) {}
