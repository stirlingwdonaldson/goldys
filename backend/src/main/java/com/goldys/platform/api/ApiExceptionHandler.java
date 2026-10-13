package com.goldys.platform.api;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.config.CorrelationIdFilter;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

  /**
   * A body that could not be deserialized (malformed JSON, an unknown enum value, or a record
   * constructor that rejected the input, e.g. a missing {@code range}). Our own {@link
   * IllegalArgumentException}s are surfaced verbatim; Jackson parser internals are collapsed to a
   * generic message so we never echo request content back to the caller.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiErrorResponse> handleUnreadable(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ApiErrorResponse(
                "VALIDATION_FAILED", readableMessage(exception), correlationId(request), Map.of()));
  }

  /** A query/path parameter that failed type conversion (e.g. {@code ?from=not-a-date}). */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ApiErrorResponse(
                "VALIDATION_FAILED",
                "Invalid value for '" + exception.getName() + "'",
                correlationId(request),
                Map.of()));
  }

  /** A required query/path parameter that was omitted entirely. */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ApiErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ApiErrorResponse(
                "VALIDATION_FAILED",
                "Missing required parameter '" + exception.getParameterName() + "'",
                correlationId(request),
                Map.of()));
  }

  /**
   * Walks to the deepest cause, surfacing our own validation messages and hiding parser internals.
   */
  private static String readableMessage(HttpMessageNotReadableException exception) {
    Throwable cause = exception;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    if (cause instanceof IllegalArgumentException iae && iae.getMessage() != null) {
      return iae.getMessage();
    }
    return "Malformed request body";
  }

  /** A feature that is present but not configured (e.g. no chat model / API key). */
  @ExceptionHandler(NotConfiguredException.class)
  ResponseEntity<ApiErrorResponse> handleNotConfigured(
      NotConfiguredException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            new ApiErrorResponse(
                "NOT_CONFIGURED", exception.getMessage(), correlationId(request), Map.of()));
  }

  /** A push/upload payload that failed to parse (e.g. a CSV with unexpected columns). */
  @ExceptionHandler(ConnectorFetchException.class)
  ResponseEntity<ApiErrorResponse> handleConnectorFetch(
      ConnectorFetchException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ApiErrorResponse(
                exception.failureType(), exception.getMessage(), correlationId(request), Map.of()));
  }

  /** An entity/domain/raw id that does not exist (data explorer). */
  @ExceptionHandler(java.util.NoSuchElementException.class)
  ResponseEntity<ApiErrorResponse> handleNotFound(
      java.util.NoSuchElementException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            new ApiErrorResponse(
                "NOT_FOUND", exception.getMessage(), correlationId(request), Map.of()));
  }

  /** Resolved once per request by {@link CorrelationIdFilter}, which also tags it in Sentry. */
  private String correlationId(HttpServletRequest request) {
    return CorrelationIdFilter.of(request);
  }
}
