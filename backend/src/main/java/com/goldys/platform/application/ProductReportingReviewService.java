package com.goldys.platform.application;

import com.goldys.platform.auth.*;
import com.goldys.platform.classification.ProductReportingMappingService;
import com.goldys.platform.semantic.ClassifiedSalesTotals;
import com.goldys.platform.semantic.ClassifiedWastageQuery;
import com.goldys.platform.semantic.ProductReportingMapping;
import com.goldys.platform.semantic.ReportingReviewQuery;
import com.goldys.platform.semantic.ReportingSalesQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductReportingReviewService {
  private final ReportingReviewQuery review;
  private final ReportingSalesQuery sales;
  private final ProductReportingMappingService mappings;
  private final PermissionService permissions;
  private final ClassifiedWastageQuery wastage;

  public ProductReportingReviewService(
      ProductReportingMappingService mappings,
      PermissionService permissions,
      ClassifiedWastageQuery wastage,
      ReportingReviewQuery review,
      ReportingSalesQuery sales) {
    this.review = review;
    this.sales = sales;
    this.mappings = mappings;
    this.permissions = permissions;
    this.wastage = wastage;
  }

  public record Day(
      LocalDate date,
      BigDecimal salesCoverage,
      BigDecimal unclassifiedSales,
      long missingTax,
      long unresolved,
      boolean belowThreshold) {}

  public record Queue(
      List<ReportingReviewQuery.Item> items,
      boolean hasMore,
      List<Day> days,
      ReportingReviewQuery.WasteCoverage wastage) {}

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Queue queue(
      UserRole role,
      ReportingReviewQuery.Domain domain,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    for (String resource :
        List.of("products.category", "reconciliation.sales", "inventory.stock", "inventory.cost"))
      permissions.require(role, new ResourceKey(resource), PermissionAction.READ);
    if (domain == null
        || from == null
        || to == null
        || to.isBefore(from)
        || to.isAfter(from.plusDays(91))
        || page < 0
        || page > 10000
        || size < 1
        || size > 200)
      throw new IllegalArgumentException(
          "Use an ordered range up to 92 days, page 0–10000 and size 1–200");
    var rows = review.items(domain, from, to, page * size, size + 1);
    var byDay =
        sales.sales(from, to).stream()
            .collect(Collectors.groupingBy(ReportingSalesQuery.Sale::date));
    var days =
        from.datesUntil(to.plusDays(1))
            .map(
                date -> {
                  var totals = ClassifiedSalesTotals.summarize(byDay.getOrDefault(date, List.of()));
                  return new Day(
                      date,
                      totals.coverage(),
                      totals.unknown(),
                      totals.missingTax(),
                      totals.unresolved(),
                      totals.coverage() != null
                          && totals.coverage().compareTo(new BigDecimal("0.95")) < 0);
                })
            .toList();
    return new Queue(
        rows.stream().limit(size).toList(),
        rows.size() > size,
        days,
        review.wasteCoverage(from, to));
  }

  public List<ProductReportingMapping> revisions(UserRole role, LocalDate to) {
    permissions.require(role, new ResourceKey("products.category"), PermissionAction.READ);
    return mappings.revisions(to);
  }

  public List<ClassifiedWastageQuery.Line> wastage(
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
