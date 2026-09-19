package com.goldys.platform.ingestion;

/**
 * How a payload reached the platform.
 *
 * <p>Public because the vendor-neutral connector port in {@code ingestion.port} describes fetched
 * payloads in these terms; the concrete vendor clients stay behind that port.
 */
public enum FetchMethod {
  API,
  FILE_EXPORT,
  SCRAPE,
  MANUAL
}
