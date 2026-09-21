package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class UserAccountRepositoryTest {

  @Autowired UserAccountRepository repository;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table user_account");
  }

  @Test
  void savesAndFindsByEmail() {
    UserAccount account =
        UserAccount.create("a@b.com", "hash", "A B", "ALL", "STAFF", Instant.now());
    repository.save(account);

    assertThat(repository.findByEmail("a@b.com")).isPresent();
    assertThat(repository.existsByEmail("a@b.com")).isTrue();
    assertThat(repository.existsByEmail("x@y.com")).isFalse();
  }
}
