package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.DataExplorerService;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.EntityDescriptor;
import com.goldys.platform.semantic.GenericRow;
import com.goldys.platform.semantic.RawRecordSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(DataExplorerController.class)
@Import(SecurityConfig.class)
class DataExplorerControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean DataExplorerService service;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void rawListsMetadata() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(service.listRaw(any(), any(), anyInt(), anyInt()))
        .thenReturn(
            new DataPage<>(
                List.of(
                    new RawRecordSummary(
                        UUID.randomUUID(),
                        "CTB",
                        "ctb-recipes",
                        "API",
                        "application/json",
                        "UTF-8",
                        Instant.parse("2026-10-01T00:00:00Z"),
                        10L,
                        "0".repeat(64))),
                1,
                0,
                50));

    mvc.perform(get("/api/data/raw?source=CTB").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].sourceSystem").value("CTB"))
        .andExpect(jsonPath("$.items[0].fetcherIdentity").value("ctb-recipes"));
  }

  @Test
  void canonicalListsEntities() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(service.canonicalEntities(any()))
        .thenReturn(List.of(new EntityDescriptor("daily_sales", "Daily sales", false)));

    mvc.perform(get("/api/data/canonical").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("daily_sales"))
        .andExpect(jsonPath("$[0].label").value("Daily sales"));
  }

  @Test
  void canonicalRowsReturnsGenericRow() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(service.canonicalRows(any(), anyString(), anyInt(), anyInt()))
        .thenReturn(
            new DataPage<>(
                List.of(new GenericRow("1", Map.of("trading_date", "2026-09-13"))), 1, 0, 50));

    mvc.perform(get("/api/data/canonical/daily_sales").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value("1"))
        .andExpect(jsonPath("$.items[0].columns.trading_date").value("2026-09-13"));
  }

  @Test
  void resolvedListsDomains() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(service.resolvedDomains(any()))
        .thenReturn(List.of(new EntityDescriptor("resolved_daily_sales", "Daily sales", false)));

    mvc.perform(get("/api/data/resolved").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("resolved_daily_sales"));
  }

  @Test
  void deniedReturnsForbidden() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    doThrow(AccessDeniedException.forResource("connectors")).when(service).canonicalEntities(any());

    mvc.perform(get("/api/data/canonical").with(authenticated(owner())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void unknownEntityReturnsNotFound() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(service.canonicalRows(any(), anyString(), anyInt(), anyInt()))
        .thenThrow(new java.util.NoSuchElementException("nope"));

    mvc.perform(get("/api/data/canonical/nope").with(authenticated(owner())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
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
