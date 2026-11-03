package com.goldys.platform.semantic;

import java.util.UUID;

/** Exact JSON snapshots; null for legacy runs. All proposed assignments require staff review. */
public record ClassificationPreview(
    UUID runId, String contextSnapshot, String proposalSnapshot, String namingSnapshot) {}
