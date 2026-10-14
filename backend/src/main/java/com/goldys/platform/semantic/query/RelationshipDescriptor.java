package com.goldys.platform.semantic.query;

import java.util.List;

/**
 * A registered relationship between two datasets: its join keys, cardinality, and fanout risk. A
 * {@code fanoutRisk} relationship is one whose join would repeat a measure from the "one" side once
 * per row of the "many" side (e.g. invoice header → lines), so summing a measure from the "one"
 * side across it would double-count unless explicitly distinct.
 */
public record RelationshipDescriptor(
    String id,
    String from,
    String to,
    List<JoinKey> joinKeys,
    Cardinality cardinality,
    boolean fanoutRisk,
    String permission) {

  public record JoinKey(String fromField, String toField) {}
}
