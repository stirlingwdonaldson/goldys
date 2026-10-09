package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.InventoryReportingService;
import com.goldys.platform.application.InvoiceGraphService;
import com.goldys.platform.application.InvoiceIngestFlagQueryService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.InvoiceGraphNode;
import com.goldys.platform.semantic.InvoiceIngestFlagView;
import com.goldys.platform.semantic.LineGraphNode;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.math.BigDecimal;
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

@WebMvcTest(InventoryController.class)
@Import(SecurityConfig.class)
class InventoryControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean InventoryReportingService reporting;
  @MockitoBean InvoiceGraphService graph;
  @MockitoBean InvoiceIngestFlagQueryService flags;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void suppliersReturnsRankedSuppliers() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(graph.suppliers(any(), any(), any()))
        .thenReturn(List.of(new SupplierGraphNode("A. Foods", 214, new BigDecimal("81230.40"))));

    mvc.perform(
            get("/api/inventory/graph/suppliers")
                .param("from", "2026-09-01")
                .param("to", "2026-09-30")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("A. Foods"))
        .andExpect(jsonPath("$[0].invoiceCount").value(214));
  }

  @Test
  void invoicesReturnsTheSuppliersInvoices() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(graph.invoicesForSupplier(any(), any(), any(), any()))
        .thenReturn(
            List.of(
                new InvoiceGraphNode(
                    "INV-1042",
                    LocalDate.of(2026, 9, 28),
                    new BigDecimal("1420.15"),
                    "PO-88",
                    "inv-1042.pdf")));

    mvc.perform(
            get("/api/inventory/graph/suppliers/{supplier}/invoices", "A. Foods")
                .param("from", "2026-09-01")
                .param("to", "2026-09-30")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].invoiceNumber").value("INV-1042"))
        .andExpect(jsonPath("$[0].purchaseNumber").value("PO-88"));
  }

  @Test
  void linesReturnsTheInvoicesLines() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(graph.linesForInvoice(any(), any()))
        .thenReturn(
            List.of(
                new LineGraphNode(
                    "chicken breast",
                    "CB-1",
                    new BigDecimal("4"),
                    new BigDecimal("12.5"),
                    new BigDecimal("50.00"),
                    "CTN")));

    mvc.perform(get("/api/inventory/graph/invoices/INV-1042/lines").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].productNameKey").value("chicken breast"))
        .andExpect(jsonPath("$[0].uom").value("CTN"));
  }

  @Test
  void flagsReturnsRecordedIngestionFlags() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(flags.flags(any()))
        .thenReturn(
            List.of(
                new InvoiceIngestFlagView(
                    "MISSING_PDF",
                    "INV-1042",
                    null,
                    null,
                    "no PDF filename in CSV",
                    Instant.EPOCH)));

    mvc.perform(get("/api/inventory/flags").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].flagType").value("MISSING_PDF"))
        .andExpect(jsonPath("$[0].invoiceNumber").value("INV-1042"));
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
