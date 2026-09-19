package com.goldys.platform.api;

import java.util.Map;

/**
 * The one error envelope every API error uses: a stable machine code, a human-readable message, a
 * request correlation identifier, and field details when applicable.
 */
public record ApiErrorResponse(
    String code, String message, String correlationId, Map<String, String> fields) {
  public ApiErrorResponse {
    fields = fields == null ? Map.of() : Map.copyOf(fields);
  }
}
