package com.goldys.platform.semantic.query;

import java.time.Instant;
import java.util.List;

/**
 * A query result: schema metadata (columns), positional rows, pagination, and the semantics under
 * which the rows were produced (current-fact, grain, read mode, datasets, as-of).
 */
public record QueryResult(
    List<ResultColumn> columns,
    List<List<Object>> rows,
    Pagination pagination,
    Semantics semantics) {

  public record Pagination(int limit, int offset, boolean truncated) {}

  public record Semantics(
      boolean currentFact, String grain, ReadMode readMode, List<String> datasets, Instant asOf) {}
}
