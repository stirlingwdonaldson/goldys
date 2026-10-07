package com.goldys.platform.semantic;

import java.time.Instant;

/** Per-source connector freshness, exposed to the semantic layer (PASS 9 extends this). */
public record ConnectorHealth(String source, Instant lastRunAt, String status) {}
