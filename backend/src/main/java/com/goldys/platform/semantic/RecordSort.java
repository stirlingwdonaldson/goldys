package com.goldys.platform.semantic;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;

/** Single-column record order. Query identifiers always come from a domain-owned allowlist. */
public record RecordSort(String field, String direction) {
  public RecordSort {
    if (field == null || field.isBlank()) throw new IllegalArgumentException("Sort field required");
    if (!"asc".equals(direction) && !"desc".equals(direction))
      throw new IllegalArgumentException("Sort direction must be asc or desc");
  }

  public String orderBy(Map<String, String> fields, String tieBreaker) {
    String expression = fields.get(field);
    if (expression == null) throw new IllegalArgumentException("Unsupported sort field: " + field);
    return expression + " " + direction + " nulls last, " + tieBreaker;
  }

  public <T, U extends Comparable<? super U>> Comparator<T> comparing(Function<T, U> value) {
    return Comparator.comparing(
        value,
        Comparator.nullsLast(
            "desc".equals(direction)
                ? Comparator.<U>reverseOrder()
                : Comparator.<U>naturalOrder()));
  }
}
