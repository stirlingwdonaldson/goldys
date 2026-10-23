package com.goldys.platform.semantic.query;

/** One result column's schema metadata. */
public record ResultColumn(String name, FieldType type, String unit) {}
