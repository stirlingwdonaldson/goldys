package com.goldys.platform.config;

import org.springframework.context.annotation.Configuration;

/**
 * Placeholder for wiring Instant.now()/clock injection consistently across
 * RawRecord.fetchedAt, BitemporalEntity.recordedAt, etc. Keeping this as its
 * own config class from the start avoids five different "new Date()" call
 * sites once real connectors land - inject a Clock bean here when the first
 * connector is implemented, rather than each connector picking its own.
 */
@Configuration
public class JpaAuditingConfig {
}
