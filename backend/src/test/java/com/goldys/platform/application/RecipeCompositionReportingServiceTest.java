package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.semantic.RecipeCompositionQuery;
import org.junit.jupiter.api.Test;

class RecipeCompositionReportingServiceTest {
  @Test
  void compositionRequiresBothCostAndIngredientPermissionsBeforeReading() {
    var role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
    var permissions = mock(PermissionService.class);
    var query = mock(RecipeCompositionQuery.class);
    doThrow(AccessDeniedException.forResource("recipes.ingredients"))
        .when(permissions)
        .require(role, new ResourceKey("recipes.ingredients"), PermissionAction.READ);
    var service = new RecipeCompositionReportingService(query, permissions);
    assertThatThrownBy(() -> service.composition(463, role))
        .isInstanceOf(AccessDeniedException.class);
    verify(permissions).require(role, new ResourceKey("recipes.cost"), PermissionAction.READ);
    verifyNoInteractions(query);
  }

  @Test
  void detailsRequireBothDomainPermissionsAndRedactRestrictedRawEvidence() {
    var role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
    var permissions = mock(PermissionService.class);
    var query = mock(RecipeCompositionQuery.class);
    var e =
        new com.goldys.platform.semantic.RecipeEvidence(
            java.util.UUID.randomUUID(),
            "CTB",
            "recipe",
            "/RecipeBook/GetRecipeByRecipeId",
            "recipe:377",
            java.time.Instant.now(),
            java.util.UUID.randomUUID(),
            "digest");
    var section =
        new com.goldys.platform.semantic.RecipeSection(
            377, "HEADER", true, null, java.util.List.of(), e);
    var details =
        new com.goldys.platform.semantic.RecipeDetails(
            377,
            "Loaf",
            "Complete observation",
            java.util.List.of(section),
            java.util.List.of(section),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(e),
            null,
            false,
            129600);
    when(query.details(377)).thenReturn(java.util.Optional.of(details));
    doThrow(AccessDeniedException.forResource("connectors"))
        .when(permissions)
        .require(role, new ResourceKey("connectors"), PermissionAction.READ);
    var service = new RecipeCompositionReportingService(query, permissions);
    org.springframework.test.util.ReflectionTestUtils.setField(
        service, "connectorHealth", mock(com.goldys.platform.semantic.ConnectorHealthQuery.class));
    var result = service.details(377, role);
    verify(permissions).require(role, new ResourceKey("recipes.cost"), PermissionAction.READ);
    verify(permissions)
        .require(role, new ResourceKey("recipes.ingredients"), PermissionAction.READ);
    assertThat(result.sections().getFirst().evidence().rawRecordId()).isNull();
    assertThat(result.latestAttempts().getFirst().evidence().ingestionRunId()).isNull();
    assertThat(result.compositionSources().getFirst().sha256()).isNull();
    assertThat(result.sections().getFirst().evidence().sourceSystem()).isEqualTo("CTB");
  }
}
