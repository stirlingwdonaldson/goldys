package com.goldys.platform.raw;

/**
 * How a raw record entered the system. Deliberately covers non-API shapes
 * (CSV export, scraped page, manual entry) as first-class, not just API_JSON -
 * see system-context.md's "Per-Source Ingestion Reality": most MVP sources are
 * NOT clean API JSON.
 */
public enum FetchMethod {
    API_JSON,
    CSV_EXPORT,
    SCRAPE,
    MANUAL
}
