package com.goldys.platform.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * An account as the user-management screen sees it. {@code self} marks the caller's own row so the
 * UI can disable actions the service refuses anyway.
 */
public record ManagedUserSummary(
    UUID id, String email, String displayName, boolean active, Instant createdAt, boolean self) {}
