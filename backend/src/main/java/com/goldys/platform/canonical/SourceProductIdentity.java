package com.goldys.platform.canonical;

import java.util.UUID;

/** A resolved source product's stable identity: the logical id plus the source-scoped key. */
public record SourceProductIdentity(UUID logicalEntityId, String sourceRecordRef) {}
