package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.connectors.opentable.OpenTableCsvIngestService;
import com.goldys.platform.ingestion.IngestionRunSummary;
import com.goldys.platform.ingestion.IngestionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ConnectorStatusController.class)
@Import(SecurityConfig.class)
class ConnectorStatusControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean IngestionService ingestion;
  @MockitoBean OpenTableCsvIngestService openTableCsvIngest;
  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void connectorsListsKnownSourcesMergedWithLatestRun() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(ingestion.latestRunPerSource())
        .thenReturn(
            List.of(new IngestionRunSummary("CTB", "ctb-revenue", "SUCCESS", Instant.EPOCH, null)));

    mvc.perform(get("/api/connectors").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[1].source").value("CTB"))
        .andExpect(jsonPath("$[1].status").value("success"))
        .andExpect(jsonPath("$[2].source").value("OPENTABLE"))
        .andExpect(jsonPath("$[2].status").value("never_run"));
  }

  @Test
  void runDeniedReturnsForbidden() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    doThrow(AccessDeniedException.forResource("connectors"))
        .when(permissions)
        .require(any(), any(), any());

    mvc.perform(post("/api/connectors/CTB/run").with(authenticated(owner())).with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void unknownSourceReturnsBadRequest() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(ingestion.runConnector("CTB"))
        .thenThrow(new IllegalArgumentException("Unknown source: CTB"));

    mvc.perform(post("/api/connectors/CTB/run").with(authenticated(owner())).with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void runReturnsTheResultSummary() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(ingestion.runConnector("CTB"))
        .thenReturn(new IngestionRunSummary("CTB", "ctb-revenue", "SUCCESS", Instant.EPOCH, null));

    mvc.perform(post("/api/connectors/CTB/run").with(authenticated(owner())).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("CTB"))
        .andExpect(jsonPath("$.status").value("success"));
  }

  @Test
  void uploadDeniedReturnsForbidden() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    doThrow(AccessDeniedException.forResource("connectors"))
        .when(permissions)
        .require(any(), any(), any());

    MockMultipartFile file = new MockMultipartFile("file", "r.csv", "text/csv", "a,b".getBytes());

    mvc.perform(
            multipart("/api/connectors/opentable/upload")
                .file(file)
                .with(authenticated(owner()))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void uploadReturnsNoContentAndIngests() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());

    MockMultipartFile file =
        new MockMultipartFile("file", "reservations.csv", "text/csv", "a,b\n1,2".getBytes());

    mvc.perform(
            multipart("/api/connectors/opentable/upload")
                .file(file)
                .with(authenticated(owner()))
                .with(csrf()))
        .andExpect(status().isNoContent());

    verify(openTableCsvIngest).ingest(any(byte[].class));
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of());
    return authentication(auth);
  }
}
