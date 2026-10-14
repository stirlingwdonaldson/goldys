package com.goldys.platform.semantic;

import java.util.UUID;

/**
 * One taxonomy suggestion awaiting review, as surfaced to the UI (mirrors the {@code
 * category_suggestion} draft table). {@code runId} is null for staff-added suggestions that no run
 * produced.
 */
public record CategorySuggestionView(
    UUID id, UUID runId, String name, String groupName, int displayOrder, String hint) {}
