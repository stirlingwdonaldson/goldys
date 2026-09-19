package com.goldys.platform.auth;

/**
 * Thrown when the sole permission service denies a request.
 *
 * <p>Denial is always explicit — there is no silently filtered or partial result. The API layer
 * maps this to a {@code NOT_PERMITTED} response.
 */
public class AccessDeniedException extends RuntimeException {
  public AccessDeniedException(String resource) {
    super("Access denied for resource: " + resource);
  }
}
