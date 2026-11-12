package com.goldys.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class DatabaseMigrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  @Autowired ObjectProvider<FlywayMigrationStrategy> migrationStrategy;

  @Test
  void startupRejectsChangedAppliedMigrationWithoutRepairingHistory() {
    Integer original =
        jdbc.queryForObject(
            "select checksum from flyway_schema_history where version = '1'", Integer.class);
    int mismatched = original ^ 1;
    jdbc.update("update flyway_schema_history set checksum = ? where version = '1'", mismatched);
    try {
      // Exercise the same initializer and optional strategy used by application startup.
      var initializer = new FlywayMigrationInitializer(flyway, migrationStrategy.getIfAvailable());
      assertThatThrownBy(initializer::afterPropertiesSet)
          .isInstanceOf(FlywayValidateException.class);
      assertThat(
              jdbc.queryForObject(
                  "select checksum from flyway_schema_history where version = '1'", Integer.class))
          .isEqualTo(mismatched);
    } finally {
      jdbc.update("update flyway_schema_history set checksum = ? where version = '1'", original);
    }
    flyway.validate();
  }

  @Test
  void flywayAppliesTheBaseline() {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = 'public' "
                + "and table_name in ('raw_record','ingestion_failure','permission',"
                + "'canonical_shift','canonical_sale_item')",
            Integer.class);

    assertThat(count).isEqualTo(5);
  }
}
