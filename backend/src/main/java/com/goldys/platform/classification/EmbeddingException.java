package com.goldys.platform.classification;

/**
 * An expected, classified failure from the embedding service — an unreachable endpoint, a non-2xx
 * response, or an unparseable body.
 *
 * <p>The message is operator-facing: it must never contain the request payload, credentials, or
 * tokens.
 */
public class EmbeddingException extends RuntimeException {

  public EmbeddingException(String message) {
    super(message);
  }

  public EmbeddingException(String message, Throwable cause) {
    super(message, cause);
  }
}
