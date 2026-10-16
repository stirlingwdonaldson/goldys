package com.goldys.platform.canonical;

import java.util.UUID;

/** A resolved supplier's stable identity: the logical id plus the source-scoped key. */
public record SupplierIdentity(UUID logicalEntityId, String sourceRecordRef) {}
