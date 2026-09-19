package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class JpaPermissionLookupTest {
  @Autowired PermissionRepository repository;
  @Autowired PermissionLookup lookup;

  @Test
  void readGrantDoesNotImplyAnotherSeniorityOrAction() {
    repository.save(Permission.grant("BOH", "MANAGER", "inventory.cost", true, false));

    assertThat(
            lookup.isAllowed(
                new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER")),
                new ResourceKey("inventory.cost"),
                PermissionAction.READ))
        .isTrue();
    assertThat(
            lookup.isAllowed(
                new UserRole(new DepartmentCode("BOH"), new SeniorityCode("OWNER")),
                new ResourceKey("inventory.cost"),
                PermissionAction.READ))
        .isFalse();
    assertThat(
            lookup.isAllowed(
                new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER")),
                new ResourceKey("inventory.cost"),
                PermissionAction.WRITE))
        .isFalse();
  }

  @Test
  void absentRowIsDenied() {
    assertThat(
            lookup.isAllowed(
                new UserRole(new DepartmentCode("FOH"), new SeniorityCode("STAFF")),
                new ResourceKey("labor.wages"),
                PermissionAction.READ))
        .isFalse();
  }
}
