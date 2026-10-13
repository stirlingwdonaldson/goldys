package com.goldys.platform.classification;

import java.util.UUID;

/** A candidate category offered to the classifier: its identity, display name, and description. */
public record CategoryOption(UUID categoryId, String name, String description) {}
