package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class PermissionBoundaryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired PermissionService permissions;

  @Test
  void ownerCanReadAllNewDomainResources() {
    UserRole owner = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

    assertThatCode(
            () -> {
              permissions.require(owner, new ResourceKey("reservations.metrics"), PermissionAction.READ);
              permissions.require(owner, new ResourceKey("labour.hours"), PermissionAction.READ);
              permissions.require(owner, new ResourceKey("labour.cost"), PermissionAction.READ);
              permissions.require(owner, new ResourceKey("labour.wages"), PermissionAction.READ);
              permissions.require(owner, new ResourceKey("inventory.cost"), PermissionAction.READ);
              permissions.require(owner, new ResourceKey("inventory.stock"), PermissionAction.READ);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void managerWithOnlyHoursIsDeniedCostAndWages() {
    jdbc.update(
        "insert into permission (id, department, seniority, resource, can_read, can_write) "
            + "values (gen_random_uuid(), 'FOH', 'MANAGER', 'labour.hours', true, true)");
    UserRole fohManager = new UserRole(new DepartmentCode("FOH"), new SeniorityCode("MANAGER"));

    assertThatCode(
            () -> permissions.require(fohManager, new ResourceKey("labour.hours"), PermissionAction.READ))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> permissions.require(fohManager, new ResourceKey("labour.cost"), PermissionAction.READ))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> permissions.require(fohManager, new ResourceKey("labour.wages"), PermissionAction.READ))
        .isInstanceOf(AccessDeniedException.class);
  }
}
