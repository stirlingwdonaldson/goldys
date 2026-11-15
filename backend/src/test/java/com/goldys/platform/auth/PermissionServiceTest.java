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
  void observesActualChecksIncludingDeniedDiscoveryWithoutGrantingAccess() {
    var service =
        new PermissionService((role, resource, action) -> resource.value().equals("sales.net"));
    var role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
    var sales = new ResourceKey("sales.net");
    var wages = new ResourceKey("labour.cost");
    var observed =
        service.observe(
            () -> {
              service.require(role, sales, PermissionAction.READ);
              service.require(role, sales, PermissionAction.READ);
              assertThat(service.isAllowed(role, wages, PermissionAction.READ)).isFalse();
              assertThatThrownBy(() -> service.require(role, wages, PermissionAction.READ))
                  .isInstanceOf(AccessDeniedException.class);
              return "result";
            });
    assertThat(observed.value()).isEqualTo("result");
    assertThat(observed.checks())
        .containsExactly(
            new PermissionService.Check(role, sales, PermissionAction.READ, true),
            new PermissionService.Check(role, wages, PermissionAction.READ, false));
    assertThatThrownBy(() -> observed.checks().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(service.observe(() -> "next").checks()).isEmpty();
  }

  @Test
  void failedAndNestedObservationCannotLeakChecksIntoNextOperation() {
    var service = new PermissionService((role, resource, action) -> false);
    var role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
    assertThatThrownBy(
            () ->
                service.observe(
                    () -> {
                      service.require(role, new ResourceKey("labour.cost"), PermissionAction.READ);
                      return null;
                    }))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.observe(() -> service.observe(() -> "nested")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.observe(() -> "next").checks()).isEmpty();
  }

  @Test
  void concurrentObservationsRemainIsolated() throws Exception {
    var service = new PermissionService((role, resource, action) -> true);
    var role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var futures =
          java.util.stream.Stream.of("sales.net", "labour.cost")
              .map(
                  name ->
                      executor.submit(
                          () ->
                              service.observe(
                                  () -> {
                                    service.require(
                                        role, new ResourceKey(name), PermissionAction.READ);
                                    try {
                                      barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                                    } catch (Exception failure) {
                                      throw new IllegalStateException(failure);
                                    }
                                    return name;
                                  })))
              .toList();
      for (var future : futures) {
        var observed = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(observed.checks()).hasSize(1);
        assertThat(observed.checks().getFirst().resource().value()).isEqualTo(observed.value());
      }
    }
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
