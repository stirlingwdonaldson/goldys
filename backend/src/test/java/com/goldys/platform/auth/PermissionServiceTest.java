package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PermissionServiceTest {
  @Test
  void ownerHasNoHardcodedBypass() {
    PermissionLookup lookup = (role, resource, action) -> false;
    PermissionService service = new PermissionService(lookup);

    assertThatThrownBy(
            () ->
                service.require(
                    new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER")),
                    new ResourceKey("labor.wages"),
                    PermissionAction.READ))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("labor.wages");
  }

  @Test
  void allowedRequestDoesNotThrow() {
    PermissionLookup lookup = (role, resource, action) -> true;
    PermissionService service = new PermissionService(lookup);

    service.require(
        new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER")),
        new ResourceKey("inventory.cost"),
        PermissionAction.READ);
  }

  @Test
  void writeIsCheckedSeparatelyFromRead() {
    PermissionLookup lookup = (role, resource, action) -> action == PermissionAction.READ;
    PermissionService service = new PermissionService(lookup);
    UserRole bohManager = new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER"));
    ResourceKey resource = new ResourceKey("inventory.cost");

    service.require(bohManager, resource, PermissionAction.READ);
    assertThatThrownBy(() -> service.require(bohManager, resource, PermissionAction.WRITE))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void roleCodesAreNormalisedAndValidated() {
    assertThat(new DepartmentCode("  foh ").value()).isEqualTo("FOH");
    assertThat(new SeniorityCode("  manager ").value()).isEqualTo("MANAGER");
    assertThatThrownBy(() -> new DepartmentCode("9foh"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ResourceKey("Labor.Wages"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
