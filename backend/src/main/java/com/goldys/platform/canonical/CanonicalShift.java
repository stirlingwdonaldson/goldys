package com.goldys.platform.canonical;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Example canonical entity (spec Requirement 2 / 7). Fields are placeholders -
 * finalize against the entity-matching design session (spec Open Questions)
 * before treating this as authoritative. staffMemberRef and sourceRecordId
 * exist so the entity-matching strategy has somewhere to put its keys.
 */
@Entity
@Table(name = "canonical_shift")
public class CanonicalShift extends BitemporalEntity {

    private UUID staffMemberRef;
    private Instant shiftStart;
    private Instant shiftEnd;

    protected CanonicalShift() {
        super();
    }

    public CanonicalShift(Instant validFrom, Instant recordedAt, UUID staffMemberRef,
                           Instant shiftStart, Instant shiftEnd) {
        super(validFrom, recordedAt);
        this.staffMemberRef = staffMemberRef;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
    }

    public UUID getStaffMemberRef() { return staffMemberRef; }
    public Instant getShiftStart() { return shiftStart; }
    public Instant getShiftEnd() { return shiftEnd; }
}
