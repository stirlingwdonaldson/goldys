package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.InvoiceDocumentService;
import com.goldys.platform.application.InvoiceReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.DataPage;
import java.math.BigDecimal;
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

@WebMvcTest(InvoiceController.class)
@Import(SecurityConfig.class)
class InvoiceControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean InvoiceReportingService reporting;
  @MockitoBean InvoiceDocumentService documents;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void explicitInvoiceAndDocumentIdsReachTheScopedRead() throws Exception {
    UUID invoiceId = UUID.randomUUID(), documentId = UUID.randomUUID();
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(documents.pdf(any(), eq(invoiceId), eq(documentId)))
        .thenReturn(new InvoiceDocumentService.PdfDocument(new byte[] {1, 2}, "selected.pdf"));
    mvc.perform(
            get("/api/invoices/by-id/" + invoiceId + "/documents/" + documentId + "/pdf")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline; filename=\"selected.pdf\""))
        .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
  }

  @Test
  void malformedInvoiceIdIsAValidationError() throws Exception {
    mvc.perform(get("/api/invoices/by-id/not-a-uuid").with(authenticated(owner())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void listReturnsPagedSummaries() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.list(any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(
            new DataPage<>(
                List.of(
                    new InvoiceReportingService.InvoiceSummary(
                        "A. Foods",
                        "INV-1",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 1),
                        new BigDecimal("10.00"),
                        "PO-1",
                        true,
                        null,
                        null,
                        "NOT_LINKED")),
                1,
                0,
                50));

    mvc.perform(
            get("/api/invoices")
                .param("from", "2026-09-01")
                .param("to", "2026-09-30")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].invoiceNumber").value("INV-1"))
        .andExpect(jsonPath("$.items[0].hasPdf").value(true));
  }

  @Test
  void listWithoutDateParamsSucceeds() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.list(any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new DataPage<>(List.of(), 0, 0, 50));

    mvc.perform(get("/api/invoices").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void detailReturnsTheInvoice() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.detail(any(), any()))
        .thenReturn(
            new InvoiceReportingService.InvoiceDetail(
                "A. Foods",
                "INV-1",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1),
                new BigDecimal("10.00"),
                "PO-1",
                true,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                "NOT_LINKED",
                false,
                false,
                null,
                null,
                List.of()));

    mvc.perform(get("/api/invoices/INV-1").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoiceNumber").value("INV-1"))
        .andExpect(jsonPath("$.hasPdf").value(true));
  }

  @Test
  void pdfReturnsInlineByDefault() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(documents.pdf(any(), any()))
        .thenReturn(new InvoiceDocumentService.PdfDocument(new byte[] {1, 2, 3}, "inv-1.pdf"));

    mvc.perform(get("/api/invoices/INV-1/pdf").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().string("Content-Disposition", "inline; filename=\"inv-1.pdf\""))
        .andExpect(header().string("Content-Length", "3"));
  }

  @Test
  void pdfIsFrameableByTheAppForThePreview() throws Exception {
    // The detail view embeds this response in an <iframe>. Spring Security's default
    // X-Frame-Options: DENY blocks even same-origin framing, which surfaced in the browser as
    // "platform.swd.sh refused to connect" instead of the PDF.
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(documents.pdf(any(), any()))
        .thenReturn(new InvoiceDocumentService.PdfDocument(new byte[] {1}, "inv-1.pdf"));

    mvc.perform(get("/api/invoices/INV-1/pdf").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
  }

  @Test
  void pdfAttachmentDisposition() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(documents.pdf(any(), any()))
        .thenReturn(new InvoiceDocumentService.PdfDocument(new byte[] {1}, "inv-1.pdf"));

    mvc.perform(
            get("/api/invoices/INV-1/pdf")
                .param("disposition", "attachment")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"inv-1.pdf\""));
  }

  @Test
  void pdfBogusDispositionFallsBackToInline() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(documents.pdf(any(), any()))
        .thenReturn(new InvoiceDocumentService.PdfDocument(new byte[] {1}, "inv-1.pdf"));

    mvc.perform(
            get("/api/invoices/INV-1/pdf")
                .param("disposition", "bogus")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline; filename=\"inv-1.pdf\""));
  }

  @Test
  void pdfSanitizesFilename() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(documents.pdf(any(), any()))
        .thenReturn(new InvoiceDocumentService.PdfDocument(new byte[] {1}, "a/b\\c\"d.pdf"));

    mvc.perform(get("/api/invoices/INV-1/pdf").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline; filename=\"abcd.pdf\""));
  }

  @Test
  void pdfSanitizesCarriageReturnAndLineFeed() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(documents.pdf(any(), any()))
        .thenReturn(new InvoiceDocumentService.PdfDocument(new byte[] {1}, "a\r\nb.pdf"));

    mvc.perform(get("/api/invoices/INV-1/pdf").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline; filename=\"ab.pdf\""));
  }

  @Test
  void pdfReturns404WhenNoLink() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(documents.pdf(any(), any()))
        .thenThrow(new java.util.NoSuchElementException("No PDF linked"));

    mvc.perform(get("/api/invoices/INV-1/pdf").with(authenticated(owner())))
        .andExpect(status().isNotFound());
  }

  @Test
  void invoiceNumberWithSpaceRoundTrips() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.detail(any(), eq("INV 1042")))
        .thenReturn(
            new InvoiceReportingService.InvoiceDetail(
                "A. Foods",
                "INV 1042",
                LocalDate.of(2026, 9, 1),
                null,
                new BigDecimal("10.00"),
                null,
                false,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                "NOT_LINKED",
                false,
                false,
                null,
                null,
                List.of()));

    mvc.perform(get("/api/invoices/{invoiceNumber}", "INV 1042").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoiceNumber").value("INV 1042"));
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
