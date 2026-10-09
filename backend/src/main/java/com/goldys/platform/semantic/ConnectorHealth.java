package com.goldys.platform.semantic;

import java.time.Instant;

/**
 * Per-connector freshness, exposed to the semantic layer. {@code connector} is the run's connector
 * name (e.g. {@code ctb-revenue}, {@code ctb-invoices}), so a successful invoice-CSV push cannot
 * mask a partial revenue web pull for the same source system.
 */
public record ConnectorHealth(String source, String connector, Instant lastRunAt, String status) {}
