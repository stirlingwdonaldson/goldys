package com.goldys.platform.semantic.query;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A leaf filter: a field compared with a value (or a list of values for {@code BETWEEN}/{@code
 * IN}). The value is a JSON node, coerced to the field's native type at validation time — never
 * interpreted as SQL.
 */
public record Predicate(String field, FilterOp op, JsonNode value) {}
