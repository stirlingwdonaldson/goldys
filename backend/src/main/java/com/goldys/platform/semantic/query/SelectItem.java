package com.goldys.platform.semantic.query;

/**
 * One selected expression: a field, optionally aggregated. {@code distinct} marks a fanout-safe
 * aggregation (compiles to {@code SUM(DISTINCT …)}); it is required when summing a measure from the
 * "one" side of a fanout relationship.
 */
public record SelectItem(String field, AggregateFunction aggregate, boolean distinct) {}
