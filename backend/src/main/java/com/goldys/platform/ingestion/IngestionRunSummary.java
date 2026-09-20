package com.goldys.platform.ingestion;

import java.time.Instant;

/** A read-only view of one ingestion run, for the connector-status API. */
public record IngestionRunSummary(
    String sourceSystem,
    String connectorName,
    String status,
    Instant startedAt,
    String failureSummary) {}
