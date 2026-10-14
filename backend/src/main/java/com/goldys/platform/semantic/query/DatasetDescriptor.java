package com.goldys.platform.semantic.query;

import java.util.List;

/**
 * A registered, versioned dataset: its physical table, read mode, grain, permission, fields, and
 * whether it is a current-fact (bitemporal) table. This is the allowlist — the query compiler can
 * only read tables named here, never the raw ledger or a multi-source canonical table that has a
 * resolved projection.
 */
public record DatasetDescriptor(
    String id,
    String table,
    ReadMode readMode,
    String grain,
    String permission,
    boolean currentFact,
    List<FieldDescriptor> fields) {

  public FieldDescriptor field(String name) {
    return fields.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
  }
}
