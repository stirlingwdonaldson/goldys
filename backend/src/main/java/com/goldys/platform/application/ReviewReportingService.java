package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.AspectSentimentSummary;
import com.goldys.platform.semantic.RatingBucket;
import com.goldys.platform.semantic.RatingTrendBucket;
import com.goldys.platform.semantic.ReviewFilter;
import com.goldys.platform.semantic.ReviewMetricsQuery;
import com.goldys.platform.semantic.ReviewOverview;
import com.goldys.platform.semantic.ReviewView;
import com.goldys.platform.semantic.SentimentBucket;
import com.goldys.platform.semantic.SourceSummary;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The Reviews analytics screen's read model. Authorizes the read on {@code reviews} and delegates
 * to the resolved {@link ReviewMetricsQuery}; every figure comes only from the resolved projection.
 */
@Service
public class ReviewReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("reviews");

  private final ReviewMetricsQuery query;
  private final PermissionService permissions;

  public ReviewReportingService(ReviewMetricsQuery query, PermissionService permissions) {
    this.query = query;
    this.permissions = permissions;
  }

  /** Headline counts over the inclusive range. */
  public ReviewOverview overview(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.overview(from, to);
  }

  /** Weekly sentiment buckets over the inclusive range. */
  public List<SentimentBucket> sentimentTrend(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.sentimentTrend(from, to);
  }

  /** Weekly average rating and % who would recommend over the inclusive range. */
  public List<RatingTrendBucket> ratingTrend(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.ratingTrend(from, to);
  }

  /** Mentions and sentiment per aspect over the inclusive range. */
  public List<AspectSentimentSummary> aspectBreakdown(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.aspectBreakdown(from, to);
  }

  /** Star ratings, actual and predicted, over the inclusive range. */
  public List<RatingBucket> ratingDistribution(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.ratingDistribution(from, to);
  }

  /** Per-source comparison over the inclusive range. */
  public List<SourceSummary> sourceComparison(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.sourceComparison(from, to);
  }

  /** Individual reviews matching the filter. */
  public List<ReviewView> reviews(UserRole role, ReviewFilter filter) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.reviews(filter);
  }
}
