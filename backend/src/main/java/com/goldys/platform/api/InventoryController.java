package com.goldys.platform.api;

import com.goldys.platform.application.InventoryReportingService;
import com.goldys.platform.application.InvoiceGraphService;
import com.goldys.platform.application.InvoiceIngestFlagQueryService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.InvoiceGraphNode;
import com.goldys.platform.semantic.InvoiceIngestFlagView;
import com.goldys.platform.semantic.LineGraphNode;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Resolved inventory metrics for the Inventory screen. Delivery-only. */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
  private final InventoryReportingService reporting;
  private final InvoiceGraphService graph;
  private final InvoiceIngestFlagQueryService flags;
  private final CurrentUserService currentUser;

  public InventoryController(
      InventoryReportingService reporting,
      InvoiceGraphService graph,
      InvoiceIngestFlagQueryService flags,
      CurrentUserService currentUser) {
    this.reporting = reporting;
    this.graph = graph;
    this.flags = flags;
    this.currentUser = currentUser;
  }

  @GetMapping("/summary")
  InventoryReportingService.InventorySummary summary(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.summary(currentUser.roleOf(user), from, to);
  }

  @GetMapping("/lines")
  InventoryReportingService.LineBreakdown lines(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.lineBreakdown(currentUser.roleOf(user), from, to);
  }

  @GetMapping("/graph/suppliers")
  List<SupplierGraphNode> suppliers(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return graph.suppliers(currentUser.roleOf(user), from, to);
  }

  @GetMapping("/graph/suppliers/{supplier}/invoices")
  List<InvoiceGraphNode> invoicesForSupplier(
      @PathVariable String supplier,
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return graph.invoicesForSupplier(currentUser.roleOf(user), supplier, from, to);
  }

  @GetMapping("/graph/invoices/{invoiceNumber}/lines")
  List<LineGraphNode> linesForInvoice(
      @PathVariable String invoiceNumber, @AuthenticationPrincipal AccountUserDetails user) {
    return graph.linesForInvoice(currentUser.roleOf(user), invoiceNumber);
  }

  @GetMapping("/flags")
  List<InvoiceIngestFlagView> flags(@AuthenticationPrincipal AccountUserDetails user) {
    return flags.flags(currentUser.roleOf(user));
  }
}
