package com.goldys.platform.api;

import com.goldys.platform.application.ProductReportingReviewService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.ProductReportingMapping;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/categories/reporting-mappings")
public class ProductReportingReviewController {
  private final ProductReportingReviewService service;
  private final CurrentUserService currentUser;

  public ProductReportingReviewController(
      ProductReportingReviewService service, CurrentUserService currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping
  List<ProductReportingMapping> revisions(
      @RequestParam LocalDate to, @AuthenticationPrincipal AccountUserDetails user) {
    return service.revisions(currentUser.roleOf(user), to);
  }

  @GetMapping("/wastage")
  List<com.goldys.platform.semantic.ClassifiedWastageQuery.Line> wastage(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return service.wastage(currentUser.roleOf(user), from, to, page, size);
  }

  @PostMapping
  void append(
      @RequestBody ProductReportingMapping mapping,
      @AuthenticationPrincipal AccountUserDetails user) {
    service.append(currentUser.roleOf(user), user.getUsername(), mapping);
  }
}
