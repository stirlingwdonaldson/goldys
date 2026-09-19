package com.goldys.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class DatabaseMigrationTest {
  @Autowired JdbcTemplate jdbc;

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
