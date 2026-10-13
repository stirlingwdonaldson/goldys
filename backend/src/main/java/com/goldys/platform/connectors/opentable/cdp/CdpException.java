package com.goldys.platform.connectors.opentable.cdp;

/**
 * A DevTools protocol or channel failure. Messages name the CDP method or condition only; they
 * never carry page content, since evaluated results may include guest data or session state.
 */
public class CdpException extends RuntimeException {
  public CdpException(String message) {
    super(message);
  }

  public CdpException(String message, Throwable cause) {
    super(message, cause);
  }
}
