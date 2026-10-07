package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class SavedDashboardMigrationTest {

  @Autowired JdbcTemplate jdbc;

  @Test
  void migrationAddsColumnsAndTables() {
    List<String> cols =
        jdbc.queryForList(
            "select column_name from information_schema.columns where table_name='saved_dashboard'",
            String.class);
    assertThat(cols).contains("filters", "visibility", "pinned", "current_revision");
    Integer n =
        jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_name in ('saved_dashboard_revision','saved_dashboard_share')",
            Integer.class);
    assertThat(n).isEqualTo(2);
  }
}
