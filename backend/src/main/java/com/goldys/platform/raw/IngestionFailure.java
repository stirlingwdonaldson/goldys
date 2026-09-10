package com.goldys.platform.raw;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A first-class ingestion failure record (spec Requirement 6). Connector
 * failures must be observable here - NOT inferred later from an absence of
 * new RawRecord rows or from a downstream reconciliation gap. See
 * system-context.md Build Order Prerequisite #4.
 */
@Entity
@Table(name = "ingestion_failure")
public class IngestionFailure {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SourceSystem sourceSystem;

    /** e.g. AUTH_FAILURE, TIMEOUT, PARTIAL_FETCH, SCHEMA_MISMATCH - kept as a string, not an
     * enum, so a new connector can report a failure mode this scaffold didn't anticipate
     * without a schema change. Tighten to an enum once the real failure modes are known. */
    @Column(nullable = false, updatable = false)
    private String failureType;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    protected IngestionFailure() {
        // JPA
    }

    public IngestionFailure(SourceSystem sourceSystem, String failureType, String detail, Instant occurredAt) {
        this.sourceSystem = sourceSystem;
        this.failureType = failureType;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public SourceSystem getSourceSystem() { return sourceSystem; }
    public String getFailureType() { return failureType; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
}
