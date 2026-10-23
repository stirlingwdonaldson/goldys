package com.goldys.platform.api;

import com.goldys.platform.application.SaleCostingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.SaleCosting;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sale-items/costing")
public class SaleCostingController {
  private final SaleCostingService service;
  private final CurrentUserService currentUser;

  public SaleCostingController(SaleCostingService service, CurrentUserService currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping
  SaleCosting costing(
      @RequestParam String saleNumber,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return service.costing(currentUser.roleOf(user), saleNumber, from, to);
  }
}
