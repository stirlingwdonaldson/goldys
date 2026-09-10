package com.goldys.platform.raw;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The append-only raw event log (spec Requirement 1). Every ingested payload
 * lands here byte-faithful BEFORE any parsing/transformation, regardless of
 * shape (API JSON, CSV/XLSX export, scraped HTML/DOM extract, manual entry).
 *
 * Invariant: rows in this table are never updated or deleted by application
 * code. A correction is a NEW row, not an edit to an existing one - the
 * canonical layer (see canonical/BitemporalEntity) is what tracks supersession,
 * not this table.
 */
@Entity
@Table(name = "raw_record")
public class RawRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SourceSystem sourceSystem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FetchMethod fetchMethod;

    /** MIME-ish content type of the raw payload, e.g. "application/json", "text/csv". */
    @Column(nullable = false, updatable = false)
    private String contentType;

    /**
     * The raw payload itself, stored exactly as received. JSONB is used even
     * for non-JSON payloads (CSV text, scraped HTML) - store the raw bytes as
     * text inside a JSON wrapper ({"raw": "..."}) rather than adding a second
     * column, so every source flows through one envelope (connector isolation
     * invariant - no source-specific staging tables).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    /** Who or what fetched this record - a connector name, or a staff member's identity for MANUAL. */
    @Column(nullable = false, updatable = false)
    private String fetcherIdentity;

    @Column(nullable = false, updatable = false)
    private Instant fetchedAt;

    protected RawRecord() {
        // JPA
    }

    public RawRecord(SourceSystem sourceSystem, FetchMethod fetchMethod, String contentType,
                      String payload, String fetcherIdentity, Instant fetchedAt) {
        this.sourceSystem = sourceSystem;
        this.fetchMethod = fetchMethod;
        this.contentType = contentType;
        this.payload = payload;
        this.fetcherIdentity = fetcherIdentity;
        this.fetchedAt = fetchedAt;
    }

    public UUID getId() { return id; }
    public SourceSystem getSourceSystem() { return sourceSystem; }
    public FetchMethod getFetchMethod() { return fetchMethod; }
    public String getContentType() { return contentType; }
    public String getPayload() { return payload; }
    public String getFetcherIdentity() { return fetcherIdentity; }
    public Instant getFetchedAt() { return fetchedAt; }
}
