package com.goldys.platform.auth;

/**
 * The single enforcement point for the role/permission model (spec
 * Requirement 3). Both the MVP reconciliation UI (Requirement 5) and, in
 * Phase 2, every AI tool call (Requirement 9) must go through this - do not
 * build a second, parallel permission check anywhere else in the codebase.
 *
 * OWNER seniority is expected to be granted broad access via explicit
 * Permission rows spanning both BOH and FOH (plus owner-only resources like
 * wage data) - NOT via a hardcoded "Owner bypasses checks" shortcut. Keeping
 * it table-driven is what lets Requirement 3's "add a department/seniority
 * without touching existing permissions" acceptance criterion hold.
 */
public interface PermissionService {

    /** True if the given role may read the given resource. */
    boolean canRead(UserRole role, String resource);

    /** True if the given role may write/override the given resource. */
    boolean canWrite(UserRole role, String resource);

    /** Throws AccessDeniedException if canRead(role, resource) is false. */
    void requireRead(UserRole role, String resource);

    /** Throws AccessDeniedException if canWrite(role, resource) is false. */
    void requireWrite(UserRole role, String resource);
}
