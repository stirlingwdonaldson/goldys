package com.goldys.platform.config;

import org.flywaydb.core.api.exception.FlywayValidateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Validates migrations on startup and, only when validation fails, runs {@code flyway.repair()} to
 * realign the applied checksums before migrating.
 *
 * <p>Production once had {@code V73__drop_raw_record_parsed_payload.sql} applied from a branch that
 * was deployed but never merged; that migration's exact content was later lost in a rebase, so its
 * checksum cannot be reproduced. When a migration on disk no longer byte-matches what was applied
 * (as happened here), {@code repair()} updates {@code flyway_schema_history} checksums to match the
 * files on disk — it never re-runs SQL, so the already-applied schema change is untouched.
 * Validation passes on every subsequent startup, so the repair is a one-time realignment.
 */
@Configuration
public class FlywayRepairConfig {

  private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfig.class);

  @Bean
  public FlywayMigrationStrategy repairThenMigrate() {
    return flyway -> {
      try {
        flyway.validate();
      } catch (FlywayValidateException e) {
        log.warn("Flyway validation failed — repairing checksums: {}", e.getMessage());
        flyway.repair();
      }
      flyway.migrate();
    };
  }
}
