package com.goldys.platform.auth;

/**
 * Thrown when the sole permission service denies a request, or when an authenticated identity has
 * no active staff profile to authorize it against.
 *
 * <p>Denial is always explicit — there is no silently filtered or partial result. The API layer
 * maps this to a {@code NOT_PERMITTED} response.
 */
public class AccessDeniedException extends RuntimeException {
  public AccessDeniedException(String message) {
    super(message);
  }

  public static AccessDeniedException forResource(String resource) {
    return new AccessDeniedException("Access denied for resource: " + resource);
  }
}
