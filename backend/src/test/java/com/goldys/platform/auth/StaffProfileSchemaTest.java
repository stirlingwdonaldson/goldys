package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class StaffProfileSchemaTest {
  private static final String INSERT =
      "insert into staff_profile "
          + "(id,oidc_issuer,oidc_subject,display_name,department,seniority,active,"
          + "created_at,updated_at) "
          + "values (?, ?, ?, ?, ?, ?, true, now(), now())";

  @Autowired JdbcTemplate jdbc;

  @Test
  void oidcIdentityIsUniqueByIssuerAndSubject() {
    jdbc.update(
        INSERT, UUID.randomUUID(), "https://id.example", "subject-1", "Alex", "FOH", "MANAGER");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT,
                    UUID.randomUUID(),
                    "https://id.example",
                    "subject-1",
                    "Other",
                    "BOH",
                    "STAFF"))
        .hasRootCauseInstanceOf(SQLException.class);
  }

  @Test
  void differentIssuerWithTheSameSubjectIsAllowed() {
    jdbc.update(
        INSERT, UUID.randomUUID(), "https://id3.example", "subject-2", "Alex", "FOH", "MANAGER");

    jdbc.update(
        INSERT, UUID.randomUUID(), "https://id4.example", "subject-2", "Other", "BOH", "STAFF");

    Integer count =
        jdbc.queryForObject(
            "select count(*) from staff_profile where oidc_subject = 'subject-2'", Integer.class);
    assertThat(count).isEqualTo(2);
  }
}
