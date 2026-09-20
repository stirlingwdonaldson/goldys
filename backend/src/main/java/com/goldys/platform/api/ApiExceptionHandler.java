package com.goldys.platform.api;

import com.goldys.platform.auth.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps application exceptions to the stable {@link ApiErrorResponse} envelope.
 *
 * <p>Denials carry the request correlation id and never leak stack traces, claims, tokens,
 * payloads, or credentials.
 */
@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiErrorResponse> handleAccessDenied(
      AccessDeniedException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            new ApiErrorResponse(
                "NOT_PERMITTED", exception.getMessage(), correlationId(request), Map.of()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> handleValidation(
      IllegalArgumentException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ApiErrorResponse(
                "VALIDATION_FAILED", exception.getMessage(), correlationId(request), Map.of()));
  }

  private String correlationId(HttpServletRequest request) {
    String provided = request.getHeader("X-Correlation-ID");
    return (provided != null && !provided.isBlank()) ? provided : UUID.randomUUID().toString();
  }
}
