package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the single permission enforcement point (spec Requirement 3). Deliberately
 * repository-mocked rather than database-backed: the model's guarantees are about which rows are
 * consulted and how they combine, not about SQL, and this keeps the suite runnable without a live
 * Postgres.
 *
 * <p>The behaviours asserted here are the ones Requirement 3's acceptance criteria name literally -
 * table-driven grants, no seniority-ordinal shortcut, and an explicit denial rather than a silently
 * empty result.
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

  private static final String RESOURCE = "canonical_shift.hours_worked";
  private static final UserRole BOH_MANAGER = new UserRole(Department.BOH, Seniority.MANAGER);

  @Mock private PermissionRepository permissionRepository;

  @InjectMocks private PermissionServiceImpl permissionService;

  @BeforeEach
  void startFromAnEmptyPermissionTable() {
    // The permission table is a fixture: rows exist independently of which check happens to read
    // them, so these stubs are lenient rather than strict. Strict stubbing would fail any test that
    // seeds a row precisely to prove it does NOT apply to the role being checked.
    lenient()
        .when(permissionRepository.findByDepartmentAndSeniorityAndResource(any(), any(), any()))
        .thenReturn(List.of());
  }

  private void grant(
      Department department, Seniority seniority, boolean canRead, boolean canWrite) {
    // Returns an immutable list on purpose: it is what a projection or an unmodifiable
    // wrapper would hand back, and the service must not depend on being able to mutate it.
    lenient()
        .when(
            permissionRepository.findByDepartmentAndSeniorityAndResource(
                department, seniority, RESOURCE))
        .thenReturn(List.of(new Permission(department, seniority, RESOURCE, canRead, canWrite)));
  }

  @Test
  void readsWhenTheExactDepartmentAndSeniorityGrantExists() {
    grant(Department.BOH, Seniority.MANAGER, true, false);

    assertThat(permissionService.canRead(BOH_MANAGER, RESOURCE)).isTrue();
  }

  @Test
  void readsWhenOnlyAnAllDepartmentGrantExists() {
    // A resource that isn't department-specific is granted once as (ALL, seniority) rather than
    // duplicated per department.
    grant(Department.ALL, Seniority.MANAGER, true, false);

    assertThat(permissionService.canRead(BOH_MANAGER, RESOURCE)).isTrue();
  }

  @Test
  void doesNotMutateTheListReturnedByTheRepository() {
    // Regression guard: the service used to merge its two lookups with exact.addAll(allDept) on the
    // repository's own list, which throws UnsupportedOperationException for any unmodifiable
    // result.
    // A permission check must never depend on the mutability of a query result.
    grant(Department.BOH, Seniority.MANAGER, true, false);

    assertThat(permissionService.canRead(BOH_MANAGER, RESOURCE)).isTrue();
  }

  @Test
  void seniorityIsNotAnOrdinalShortcut() {
    // OWNER holding a BOH grant must not imply access to FOH data. Owner's broader access is meant
    // to come from its own rows (typically Department.ALL), not from being "higher" than MANAGER.
    grant(Department.BOH, Seniority.OWNER, true, true);

    UserRole fohOwner = new UserRole(Department.FOH, Seniority.OWNER);

    assertThat(permissionService.canRead(fohOwner, RESOURCE)).isFalse();
  }

  @Test
  void noRowsMeansNoAccess() {
    assertThat(permissionService.canRead(BOH_MANAGER, RESOURCE)).isFalse();
    assertThat(permissionService.canWrite(BOH_MANAGER, RESOURCE)).isFalse();
  }

  @Test
  void writeIsIndependentOfRead() {
    grant(Department.BOH, Seniority.MANAGER, true, false);

    assertThat(permissionService.canRead(BOH_MANAGER, RESOURCE)).isTrue();
    assertThat(permissionService.canWrite(BOH_MANAGER, RESOURCE)).isFalse();
  }

  @Test
  void writeOnlyGrantPermitsWriteWithoutImplyingRead() {
    grant(Department.BOH, Seniority.MANAGER, false, true);

    assertThat(permissionService.canWrite(BOH_MANAGER, RESOURCE)).isTrue();
    assertThat(permissionService.canRead(BOH_MANAGER, RESOURCE)).isFalse();
  }

  @Test
  void deniedReadThrowsExplicitlyRatherThanReturningNothing() {
    // Requirement 3: a denial is an explicit "not permitted", never a silently filtered result.
    assertThatThrownBy(() -> permissionService.requireRead(BOH_MANAGER, RESOURCE))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("BOH x MANAGER")
        .hasMessageContaining(RESOURCE)
        .hasMessageContaining("read");
  }

  @Test
  void deniedWriteThrowsExplicitly() {
    grant(Department.BOH, Seniority.MANAGER, true, false);

    assertThatThrownBy(() -> permissionService.requireWrite(BOH_MANAGER, RESOURCE))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("write");
  }

  @Test
  void permittedChecksDoNotThrow() {
    grant(Department.BOH, Seniority.MANAGER, true, true);

    permissionService.requireRead(BOH_MANAGER, RESOURCE);
    permissionService.requireWrite(BOH_MANAGER, RESOURCE);
  }
}
