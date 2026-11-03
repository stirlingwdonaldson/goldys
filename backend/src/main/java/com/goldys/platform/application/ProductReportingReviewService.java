package com.goldys.platform.application;

import com.goldys.platform.auth.*;
import com.goldys.platform.classification.ProductReportingMappingService;
import com.goldys.platform.semantic.ProductReportingMapping;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductReportingReviewService {
  private final ProductReportingMappingService mappings;
  private final PermissionService permissions;
  private final com.goldys.platform.semantic.ClassifiedWastageQuery wastage;

  public ProductReportingReviewService(
      ProductReportingMappingService mappings,
      PermissionService permissions,
      com.goldys.platform.semantic.ClassifiedWastageQuery wastage) {
    this.mappings = mappings;
    this.permissions = permissions;
    this.wastage = wastage;
  }

  public List<ProductReportingMapping> revisions(UserRole role, LocalDate to) {
    permissions.require(role, new ResourceKey("products.category"), PermissionAction.READ);
    return mappings.revisions(to);
  }

  public List<com.goldys.platform.semantic.ClassifiedWastageQuery.Line> wastage(
      UserRole role, LocalDate from, LocalDate to, int page, int size) {
    permissions.require(role, new ResourceKey("inventory.stock"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("inventory.cost"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("products.category"), PermissionAction.READ);
    if (from == null
        || to == null
        || to.isBefore(from)
        || to.isAfter(from.plusYears(1))
        || page < 0
        || page > 10000
        || size < 1
        || size > 200)
      throw new IllegalArgumentException(
          "Use an ordered date range up to one year, page 0–10000 and size 1–200");
    return wastage.lines(from, to, page * size, size);
  }

  public com.goldys.platform.semantic.ReportingMappingPreview preview(
      UserRole role, ProductReportingMapping mapping, LocalDate through) {
    return mappings.preview(role, mapping, through);
  }

  public void append(UserRole role, String actor, ProductReportingMapping mapping) {
    mappings.append(role, actor, mapping);
  }
}
