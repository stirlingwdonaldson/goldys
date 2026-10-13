package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Asserts the {@code canonical_guest} migration applies the table, its columns and indexes. */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalGuestMigrationTest {
  private static final List<String> EXPECTED_COLUMNS =
      List.of(
          "id",
          "logical_entity_id",
          "source_system",
          "source_record_ref",
          "raw_record_id",
          "guest_name",
          "first_name",
          "last_name",
          "nickname",
          "email",
          "phone",
          "company",
          "marketing_opt_in",
          "first_visit_date",
          "recent_visit_date",
          "upcoming_visits",
          "completed_visits",
          "cancelled_count",
          "no_show_count",
          "lifetime_spend",
          "lifetime_per_cover_spend",
          "valid_from",
          "valid_to",
          "recorded_at",
          "superseded_at");

  @Autowired JdbcTemplate jdbc;

  @Test
  void canonicalGuestTableExistsWithAllColumns() {
    List<String> columns =
        jdbc.queryForList(
            "select column_name from information_schema.columns "
                + "where table_schema = 'public' and table_name = 'canonical_guest'",
            String.class);

    assertThat(columns).containsAll(EXPECTED_COLUMNS);
  }

  @Test
  void currentSourceFactAndLogicalIndexesExist() {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from pg_indexes "
                + "where schemaname = 'public' and tablename = 'canonical_guest' "
                + "and indexname in ('uq_guest_current_source_fact', 'ix_guest_logical_current')",
            Integer.class);

    assertThat(count).isEqualTo(2);
  }
}
