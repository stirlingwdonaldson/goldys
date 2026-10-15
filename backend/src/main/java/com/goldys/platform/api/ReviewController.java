package com.goldys.platform.api;

import com.goldys.platform.application.ReviewReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.AspectSentimentSummary;
import com.goldys.platform.semantic.RatingBucket;
import com.goldys.platform.semantic.RatingTrendBucket;
import com.goldys.platform.semantic.ReviewFilter;
import com.goldys.platform.semantic.ReviewOverview;
import com.goldys.platform.semantic.ReviewView;
import com.goldys.platform.semantic.SentimentBucket;
import com.goldys.platform.semantic.SourceSummary;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reviews analytics. Delivery-only — every call is authorized through the application service and
 * reads only the resolved projection.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
  private final ReviewReportingService reporting;
  private final CurrentUserService currentUser;

  public ReviewController(ReviewReportingService reporting, CurrentUserService currentUser) {
    this.reporting = reporting;
    this.currentUser = currentUser;
  }

  @GetMapping("/overview")
  ReviewOverview overview(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.overview(currentUser.roleOf(user), from, to);
  }

  @GetMapping("/sentiment-trend")
  List<SentimentBucket> sentimentTrend(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.sentimentTrend(currentUser.roleOf(user), from, to);
  }

  @GetMapping("/rating-trend")
  List<RatingTrendBucket> ratingTrend(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.ratingTrend(currentUser.roleOf(user), from, to);
  }

  @GetMapping("/aspects")
  List<AspectSentimentSummary> aspectBreakdown(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.aspectBreakdown(currentUser.roleOf(user), from, to);
  }

  @GetMapping("/ratings")
  List<RatingBucket> ratingDistribution(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.ratingDistribution(currentUser.roleOf(user), from, to);
  }

  @GetMapping("/sources")
  List<SourceSummary> sourceComparison(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.sourceComparison(currentUser.roleOf(user), from, to);
  }

  @GetMapping
  List<ReviewView> reviews(
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(value = "source", required = false) String source,
      @RequestParam(value = "sentiment", required = false) String sentiment,
      @RequestParam(value = "aspect", required = false) String aspect,
      @RequestParam(value = "minRating", required = false) Integer minRating,
      @RequestParam(value = "q", required = false) String q,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.reviews(
        currentUser.roleOf(user),
        new ReviewFilter(from, to, source, sentiment, aspect, minRating, q));
  }
}
