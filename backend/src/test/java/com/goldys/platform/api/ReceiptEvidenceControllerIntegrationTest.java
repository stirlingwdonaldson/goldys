package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.goldys.platform.application.ReceiptEvidenceApplicationService;
import com.goldys.platform.auth.*;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.connectors.lightspeed.LightspeedReceiptEvidenceIngestService.ImportResult;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReceiptEvidenceController.class)
@Import(SecurityConfig.class)
class ReceiptEvidenceControllerIntegrationTest {
  @Autowired MockMvc mvc;
  @MockitoBean ReceiptEvidenceApplicationService service;
  @MockitoBean CurrentUserService users;

  @Test
  void scopedUploadRequiresSessionAndCsrfAndDelegatesDeclaredScope() throws Exception {
    var file = new MockMultipartFile("file", "feed.csv", "text/csv", new byte[] {1});
    mvc.perform(
            multipart("/api/connectors/lightspeed-receipts/transactions/upload")
                .file(file)
                .with(csrf()))
        .andExpect(status().is3xxRedirection());
    var account =
        new AccountUserDetails(
            UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
    var auth = new UsernamePasswordAuthenticationToken(account, "hash", List.of());
    var role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
    when(users.roleOf(any())).thenReturn(role);
    when(service.upload(any(), anyString(), any(), anyLong(), anyString(), any(), any()))
        .thenReturn(new ImportResult(UUID.randomUUID(), 1, "SOURCE_COVERAGE_UNVERIFIED"));
    mvc.perform(
            multipart("/api/connectors/lightspeed-receipts/transactions/upload")
                .file(file)
                .with(authentication(auth)))
        .andExpect(status().isForbidden());
    mvc.perform(
            multipart("/api/connectors/lightspeed-receipts/transactions/upload")
                .file(file)
                .with(authentication(auth))
                .with(csrf())
                .param("siteNumber", "96181")
                .param("siteName", "Goldy’s Tavern")
                .param("from", "2026-10-28")
                .param("to", "2026-10-29"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.qualification").value("SOURCE_COVERAGE_UNVERIFIED"));
    verify(service)
        .upload(
            eq(role),
            eq("transactions"),
            any(),
            eq(96181L),
            eq("Goldy’s Tavern"),
            eq(LocalDate.parse("2026-10-28")),
            eq(LocalDate.parse("2026-10-29")));
  }
}
