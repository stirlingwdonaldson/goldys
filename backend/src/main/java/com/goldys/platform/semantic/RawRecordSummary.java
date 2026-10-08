package com.goldys.platform.semantic;

import java.time.Instant;
import java.util.UUID;

/** Metadata for one raw ingestion record, without the payload bytes. */
public record RawRecordSummary(
    UUID id,
    String sourceSystem,
    String fetcherIdentity,
    String fetchMethod,
    String contentType,
    String characterEncoding,
    Instant fetchedAt,
    long byteLength,
    String sha256) {}
