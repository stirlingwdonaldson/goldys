package com.goldys.platform.auth;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A single (department, seniority, resource) grant (spec Requirement 3).
 * Permissions are defined per axis combination, not per named role, so a new
 * department or seniority value only ever adds rows here - it never requires
 * editing an existing row.
 *
 * "resource" is deliberately a free string (e.g. "canonical_shift.wage_amount",
 * "tool.get_labor_cost_variance") rather than a typed reference, so this table
 * can gate both resolved-view fields (MVP, Requirement 5) and AI tool calls
 * (Phase 2, Requirement 9) with the same mechanism. Populate this table from
 * the field-to-role mapping design session (spec Open Questions) before
 * relying on it for real access control - it ships empty from this scaffold.
 */
@Entity
@Table(name = "permission")
public class Permission {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Seniority seniority;

    /** e.g. "canonical_shift.wage_amount", "tool.get_labor_cost_variance". */
    @Column(nullable = false)
    private String resource;

    @Column(nullable = false)
    private boolean canRead;

    @Column(nullable = false)
    private boolean canWrite;

    protected Permission() {
        // JPA
    }

    public Permission(Department department, Seniority seniority, String resource,
                       boolean canRead, boolean canWrite) {
        this.department = department;
        this.seniority = seniority;
        this.resource = resource;
        this.canRead = canRead;
        this.canWrite = canWrite;
    }

    public UUID getId() { return id; }
    public Department getDepartment() { return department; }
    public Seniority getSeniority() { return seniority; }
    public String getResource() { return resource; }
    public boolean isCanRead() { return canRead; }
    public boolean isCanWrite() { return canWrite; }
}
