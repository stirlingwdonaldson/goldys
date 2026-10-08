package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.FreshnessState;
import com.goldys.platform.semantic.Provenance;
import com.goldys.platform.semantic.ResolutionDetail;
import com.goldys.platform.semantic.SourceValue;
import com.goldys.platform.semantic.TrustQuery;
import com.goldys.platform.semantic.TrustState;
import com.goldys.platform.semantic.TrustSummary;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProvenanceController.class)
@Import({SecurityConfig.class, MetricCatalog.class})
class ProvenanceControllerTest {

  private static final LocalDate DATE = LocalDate.of(2026, 10, 7);
  private static final Instant AT = Instant.parse("2026-10-07T09:00:00Z");

  @Autowired MockMvc mvc;

  @MockitoBean TrustQuery trustQuery;
  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void roleWithoutSalesReadGetsForbidden() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(managerRole());
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    mvc.perform(get("/api/provenance/sales.gross/2026-10-07").with(authenticated(manager())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));

    verify(permissions).require(any(), any(), any());
  }

  @Test
  void ownerGetsProvenanceWithPerSourceValues() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(trustQuery.provenanceFor(MetricId.SALES_GROSS, DATE)).thenReturn(provenance());

    mvc.perform(get("/api/provenance/sales.gross/2026-10-07").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metric").value("sales.gross"))
        .andExpect(jsonPath("$.date").value("2026-10-07"))
        .andExpect(jsonPath("$.sources[0].sourceSystem").value("LIGHTSPEED"))
        .andExpect(jsonPath("$.sources[1].sourceSystem").value("CTB"))
        .andExpect(jsonPath("$.resolution.kind").value("agreed"));

    verify(permissions)
        .require(any(), eq(new ResourceKey("reconciliation.sales")), eq(PermissionAction.READ));
  }

  @Test
  void roleWithoutConnectorsReadGetsProvenanceWithoutRawRecords() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(trustQuery.provenanceFor(MetricId.SALES_GROSS, DATE)).thenReturn(provenance());
    doThrow(AccessDeniedException.forResource("connectors"))
        .when(permissions)
        .require(any(), eq(new ResourceKey("connectors")), eq(PermissionAction.READ));

    mvc.perform(get("/api/provenance/sales.gross/2026-10-07").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rawRecordIds").isEmpty());

    verify(permissions)
        .require(any(), eq(new ResourceKey("reconciliation.sales")), eq(PermissionAction.READ));
    verify(permissions)
        .require(any(), eq(new ResourceKey("connectors")), eq(PermissionAction.READ));
  }

  @Test
  void roleWithConnectorsReadGetsRawRecords() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(trustQuery.provenanceFor(MetricId.SALES_GROSS, DATE)).thenReturn(provenance());

    mvc.perform(get("/api/provenance/sales.gross/2026-10-07").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rawRecordIds[0]").isString());

    verify(permissions)
        .require(any(), eq(new ResourceKey("connectors")), eq(PermissionAction.READ));
  }

  private static Provenance provenance() {
    TrustSummary trust =
        new TrustSummary(
            TrustState.VERIFIED, FreshnessState.FRESH, "agreed", AT, AT, Duration.ofHours(2));
    return new Provenance(
        MetricId.SALES_GROSS,
        DATE,
        new BigDecimal("10865.72"),
        trust,
        List.of(
            new SourceValue("LIGHTSPEED", new BigDecimal("10865.72"), AT),
            new SourceValue("CTB", new BigDecimal("10860.00"), AT)),
        new ResolutionDetail("agreed", "agreed", "sources agree within tolerance", null, AT),
        List.of(UUID.randomUUID()));
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static AccountUserDetails manager() {
    return new AccountUserDetails(
        UUID.randomUUID(), "manager@example.com", "hash", "Manager", "FOH", "MANAGER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static UserRole managerRole() {
    return new UserRole(new DepartmentCode("FOH"), new SeniorityCode("MANAGER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
