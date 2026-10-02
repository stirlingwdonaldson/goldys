package com.goldys.platform.api;

/** A feature is not configured (e.g. Conversational BI has no chat model / API key). */
public class NotConfiguredException extends RuntimeException {
  public NotConfiguredException(String message) {
    super(message);
  }
}
