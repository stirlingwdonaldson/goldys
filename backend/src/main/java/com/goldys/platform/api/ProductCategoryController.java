package com.goldys.platform.api;

import com.goldys.platform.application.ProductCategoryReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.AssignmentView;
import com.goldys.platform.semantic.CategoryNode;
import com.goldys.platform.semantic.ClassificationRunView;
import com.goldys.platform.semantic.DendrogramNode;
import com.goldys.platform.semantic.ProductPoint;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product categorisation: the curated taxonomy, clustering artifacts and assignment queue, plus
 * staff curation. Delivery-only — every call is authorized through the application service.
 */
@RestController
@RequestMapping("/api/products/categories")
public class ProductCategoryController {
  private final ProductCategoryReportingService reporting;
  private final CurrentUserService currentUser;

  public ProductCategoryController(
      ProductCategoryReportingService reporting, CurrentUserService currentUser) {
    this.reporting = reporting;
    this.currentUser = currentUser;
  }

  @GetMapping("/taxonomy")
  List<CategoryNode> taxonomy(@AuthenticationPrincipal AccountUserDetails user) {
    return reporting.taxonomy(currentUser.roleOf(user));
  }

  @GetMapping("/dendrogram")
  List<DendrogramNode> dendrogram(@AuthenticationPrincipal AccountUserDetails user) {
    return reporting.dendrogram(currentUser.roleOf(user));
  }

  @GetMapping("/embedding-space")
  List<ProductPoint> embeddingSpace(@AuthenticationPrincipal AccountUserDetails user) {
    return reporting.embeddingSpace(currentUser.roleOf(user));
  }

  @GetMapping("/assignments")
  List<AssignmentView> assignments(
      @RequestParam(name = "status", required = false) String status,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.assignments(currentUser.roleOf(user), status);
  }

  @PostMapping("/categories")
  CategoryNode create(
      @RequestBody CreateCategoryRequest body, @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.create(
        currentUser.roleOf(user),
        body.name(),
        body.groupName(),
        body.displayOrder(),
        body.description());
  }

  /** Submits an on-demand clustering/assignment run, accepted for asynchronous execution. */
  @PostMapping("/runs")
  ResponseEntity<Void> run(
      @RequestBody RunClassificationRequest body,
      @AuthenticationPrincipal AccountUserDetails user) {
    reporting.runClassification(currentUser.roleOf(user), body.kind());
    return ResponseEntity.accepted().build();
  }

  /** The recent classification runs, newest first, for the screen's run-status display. */
  @GetMapping("/runs")
  List<ClassificationRunView> runs(@AuthenticationPrincipal AccountUserDetails user) {
    return reporting.listRuns(currentUser.roleOf(user));
  }

  /** Renames and/or regroups a category. A field left {@code null} is left unchanged. */
  @PatchMapping("/categories/{id}")
  void update(
      @PathVariable UUID id,
      @RequestBody UpdateCategoryRequest body,
      @AuthenticationPrincipal AccountUserDetails user) {
    var role = currentUser.roleOf(user);
    if (body.name() != null) {
      reporting.rename(role, id, body.name());
    }
    if (body.groupName() != null) {
      reporting.regroup(role, id, body.groupName());
    }
  }

  @PostMapping("/assignments/{productNameKey}/move")
  void move(
      @PathVariable String productNameKey,
      @RequestBody MoveRequest body,
      @AuthenticationPrincipal AccountUserDetails user) {
    reporting.move(currentUser.roleOf(user), productNameKey, body.categoryId());
  }

  @DeleteMapping("/assignments/{productNameKey}/override")
  void clearOverride(
      @PathVariable String productNameKey, @AuthenticationPrincipal AccountUserDetails user) {
    reporting.clearOverride(currentUser.roleOf(user), productNameKey);
  }

  record CreateCategoryRequest(
      String name, String groupName, int displayOrder, String description) {}

  record RunClassificationRequest(String kind) {}

  record UpdateCategoryRequest(String name, String groupName) {}

  record MoveRequest(UUID categoryId) {}
}
