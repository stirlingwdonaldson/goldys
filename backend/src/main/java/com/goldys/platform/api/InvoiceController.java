package com.goldys.platform.api;

import com.goldys.platform.application.InvoiceDocumentService;
import com.goldys.platform.application.InvoiceReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.DataPage;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Invoices browse, detail and PDF download. Delivery-only; authorization lives in the services. */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
  private final InvoiceReportingService reporting;
  private final InvoiceDocumentService documents;
  private final CurrentUserService currentUser;

  public InvoiceController(
      InvoiceReportingService reporting,
      InvoiceDocumentService documents,
      CurrentUserService currentUser) {
    this.reporting = reporting;
    this.documents = documents;
    this.currentUser = currentUser;
  }

  @GetMapping
  DataPage<InvoiceReportingService.InvoiceSummary> list(
      @RequestParam(required = false) String supplier,
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.list(currentUser.roleOf(user), supplier, from, to, page, size);
  }

  @GetMapping("/{invoiceNumber}")
  InvoiceReportingService.InvoiceDetail detail(
      @PathVariable String invoiceNumber, @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.detail(currentUser.roleOf(user), invoiceNumber);
  }

  @GetMapping("/{invoiceNumber}/pdf")
  ResponseEntity<byte[]> pdf(
      @PathVariable String invoiceNumber,
      @RequestParam(value = "disposition", required = false) String disposition,
      @AuthenticationPrincipal AccountUserDetails user) {
    InvoiceDocumentService.PdfDocument doc = documents.pdf(currentUser.roleOf(user), invoiceNumber);
    boolean attachment = "attachment".equalsIgnoreCase(disposition);
    String contentDisposition =
        (attachment ? "attachment" : "inline") + "; filename=\"" + sanitize(doc.filename()) + "\"";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
        .contentLength(doc.bytes().length)
        .body(doc.bytes());
  }

  private static String sanitize(String filename) {
    return filename.replaceAll("[\\r\\n\"]", "").replace("/", "").replace("\\", "");
  }
}
