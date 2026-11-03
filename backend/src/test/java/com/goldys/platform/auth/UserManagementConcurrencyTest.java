package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import com.goldys.platform.support.TestAccounts;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Two owners deactivating each other must not both win and leave zero active owners. */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class UserManagementConcurrencyTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired UserManagementService users;
  @Autowired UserAccountRepository accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired TestAccounts testAccounts;
  @Autowired TransactionTemplate transactions;

  private final ExecutorService pool = Executors.newFixedThreadPool(2);

  @BeforeEach
  void clean() {
    jdbc.update("truncate table user_account cascade");
  }

  @AfterEach
  void stop() {
    pool.shutdownNow();
  }

  @Test
  void concurrentMutualDeactivationCannotLeaveZeroOwners() throws Exception {
    testAccounts.createOwner("first@example.com", "First", "password123");
    testAccounts.createOwner("second@example.com", "Second", "password123");
    UUID firstId = idOf("first@example.com");
    UUID secondId = idOf("second@example.com");

    CountDownLatch firstLocked = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);

    Future<?> first =
        pool.submit(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      List<UserAccount> active =
                          accounts.findByDepartmentAndSeniorityAndActiveTrue("ALL", "OWNER");
                      UserAccount account =
                          active.stream()
                              .filter(a -> a.id().equals(firstId))
                              .findFirst()
                              .orElseThrow();
                      account.setActive(false, Instant.now());
                      firstLocked.countDown();
                      await(releaseFirst);
                    }));

    assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
    Future<Throwable> second =
        pool.submit(
            () -> {
              try {
                users.update(OWNER, secondId, firstId, null, false);
                return null;
              } catch (Throwable failure) {
                return failure;
              }
            });

    Thread.sleep(200); // let the second transaction block on the first owner's row
    releaseFirst.countDown();

    Throwable outcome = second.get(10, TimeUnit.SECONDS);
    assertThat(outcome)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("last active owner");
    first.get(10, TimeUnit.SECONDS);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from user_account where active and seniority = 'OWNER'",
                Long.class))
        .isEqualTo(1L);
  }

  private UUID idOf(String email) {
    return UUID.fromString(
        jdbc.queryForObject(
            "select id::text from user_account where email = ?", String.class, email));
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
