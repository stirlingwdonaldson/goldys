package com.goldys.platform.api;

import com.goldys.platform.application.PaymentReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.PaymentMix;
import com.goldys.platform.semantic.PaymentRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Resolved payment metrics for the Payments screen. Delivery-only. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final PaymentReportingService reporting;
  private final CurrentUserService currentUser;

  public PaymentController(PaymentReportingService reporting, CurrentUserService currentUser) {
    this.reporting = reporting;
    this.currentUser = currentUser;
  }

  @GetMapping("/mix")
  List<PaymentMix> mix(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.dailyMix(currentUser.roleOf(user), from, to);
  }

  @GetMapping
  DataPage<PaymentRow> list(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String paymentType,
      @RequestParam(required = false) String saleNumber,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.list(currentUser.roleOf(user), paymentType, saleNumber, from, to, page, size);
  }
}
