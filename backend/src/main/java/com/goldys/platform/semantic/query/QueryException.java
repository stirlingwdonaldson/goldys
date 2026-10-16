package com.goldys.platform.semantic.query;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A rejected query, carrying a machine-readable code and optional per-identifier details. The
 * message never leaks internal schema beyond the catalogue.
 */
public class QueryException extends RuntimeException {
  private final QueryErrorCode code;
  private final Map<String, String> details;

  public QueryException(QueryErrorCode code, String message) {
    this(code, message, Map.of());
  }

  public QueryException(QueryErrorCode code, String message, Map<String, String> details) {
    super(message);
    this.code = code;
    this.details = new LinkedHashMap<>(details);
  }

  public QueryErrorCode code() {
    return code;
  }

  public Map<String, String> details() {
    return Map.copyOf(details);
  }
}
