package com.goldys.platform.semantic.query;

/**
 * One exposed field of a dataset: its logical name, the physical column it maps to, its native
 * type, unit, null semantics, kind, and the permission required to read it.
 */
public record FieldDescriptor(
    String name,
    String column,
    FieldType type,
    String unit,
    boolean nullable,
    FieldKind kind,
    String permission) {}
