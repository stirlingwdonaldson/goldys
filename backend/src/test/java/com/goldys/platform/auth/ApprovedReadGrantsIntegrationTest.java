package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ApprovedReadGrantsIntegrationTest {
  @Autowired PermissionService permissions;

  @Test
  void managersReadAggregatesButNotIndividualPayOrRawRecords() {
    for (String department : new String[] {"ALL", "FOH", "BOH"}) {
      UserRole manager = new UserRole(new DepartmentCode(department), new SeniorityCode("MANAGER"));
      for (String resource :
          new String[] {
            "reconciliation.sales", "reservations.metrics", "labour.cost", "labour.hours"
          }) {
        assertThatCode(
                () ->
                    permissions.require(manager, new ResourceKey(resource), PermissionAction.READ))
            .doesNotThrowAnyException();
      }
      for (String resource : new String[] {"labour.wages", "connectors", "inventory.cost"}) {
        assertThatThrownBy(
                () ->
                    permissions.require(manager, new ResourceKey(resource), PermissionAction.READ))
            .isInstanceOf(AccessDeniedException.class);
      }
    }
  }

  @Test
  void staffHaveForecastPurposeWithoutGenericPrivateMetricAccess() {
    UserRole staff = new UserRole(new DepartmentCode("FOH"), new SeniorityCode("STAFF"));
    assertThatCode(
            () -> permissions.require(staff, new ResourceKey("forecasting"), PermissionAction.READ))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                permissions.require(staff, new ResourceKey("labour.wages"), PermissionAction.READ))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> permissions.require(staff, new ResourceKey("connectors"), PermissionAction.READ))
        .isInstanceOf(AccessDeniedException.class);
  }
}
