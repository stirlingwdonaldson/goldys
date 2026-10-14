package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.AspectSentimentSummary;
import com.goldys.platform.semantic.AspectSentimentView;
import com.goldys.platform.semantic.RatingBucket;
import com.goldys.platform.semantic.ReviewFilter;
import com.goldys.platform.semantic.ReviewMetricsQuery;
import com.goldys.platform.semantic.ReviewOverview;
import com.goldys.platform.semantic.ReviewView;
import com.goldys.platform.semantic.SentimentBucket;
import com.goldys.platform.semantic.SourceSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Application-layer coverage: read authorization on {@code reviews} and delegation. */
class ReviewReportingServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
  private static final LocalDate TO = LocalDate.of(2026, 10, 31);

  @Test
  void authorizesAndReturnsOverview() {
    ReviewMetricsQuery query = mock(ReviewMetricsQuery.class);
    ReviewOverview expected = new ReviewOverview(120, 4.2, 80, 25, 15, 0.87);
    when(query.overview(FROM, TO)).thenReturn(expected);
    PermissionService permissions = mock(PermissionService.class);
    ReviewReportingService service = new ReviewReportingService(query, permissions);

    assertThat(service.overview(OWNER, FROM, TO)).isEqualTo(expected);
    verify(permissions).require(OWNER, new ResourceKey("reviews"), PermissionAction.READ);
  }

  @Test
  void deniesWithoutPermission() {
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("reviews"))
        .when(permissions)
        .require(any(), any(), any());
    ReviewReportingService service =
        new ReviewReportingService(mock(ReviewMetricsQuery.class), permissions);

    assertThatThrownBy(() -> service.overview(OWNER, FROM, TO))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void authorizesAndReturnsSentimentTrend() {
    ReviewMetricsQuery query = mock(ReviewMetricsQuery.class);
    List<SentimentBucket> expected =
        List.of(new SentimentBucket(LocalDate.of(2026, 10, 5), 10, 3, 2));
    when(query.sentimentTrend(FROM, TO)).thenReturn(expected);
    PermissionService permissions = mock(PermissionService.class);
    ReviewReportingService service = new ReviewReportingService(query, permissions);

    assertThat(service.sentimentTrend(OWNER, FROM, TO)).containsExactlyElementsOf(expected);
    verify(permissions).require(OWNER, new ResourceKey("reviews"), PermissionAction.READ);
  }

  @Test
  void authorizesAndReturnsAspectBreakdown() {
    ReviewMetricsQuery query = mock(ReviewMetricsQuery.class);
    List<AspectSentimentSummary> expected =
        List.of(new AspectSentimentSummary("food", 30, 20, 5, 5));
    when(query.aspectBreakdown(FROM, TO)).thenReturn(expected);
    PermissionService permissions = mock(PermissionService.class);
    ReviewReportingService service = new ReviewReportingService(query, permissions);

    assertThat(service.aspectBreakdown(OWNER, FROM, TO)).containsExactlyElementsOf(expected);
    verify(permissions).require(OWNER, new ResourceKey("reviews"), PermissionAction.READ);
  }

  @Test
  void authorizesAndReturnsRatingDistribution() {
    ReviewMetricsQuery query = mock(ReviewMetricsQuery.class);
    List<RatingBucket> expected = List.of(new RatingBucket(5, 60, 58));
    when(query.ratingDistribution(FROM, TO)).thenReturn(expected);
    PermissionService permissions = mock(PermissionService.class);
    ReviewReportingService service = new ReviewReportingService(query, permissions);

    assertThat(service.ratingDistribution(OWNER, FROM, TO)).containsExactlyElementsOf(expected);
    verify(permissions).require(OWNER, new ResourceKey("reviews"), PermissionAction.READ);
  }

  @Test
  void authorizesAndReturnsSourceComparison() {
    ReviewMetricsQuery query = mock(ReviewMetricsQuery.class);
    List<SourceSummary> expected = List.of(new SourceSummary("opentable", 50, 4.1, 35, 8));
    when(query.sourceComparison(FROM, TO)).thenReturn(expected);
    PermissionService permissions = mock(PermissionService.class);
    ReviewReportingService service = new ReviewReportingService(query, permissions);

    assertThat(service.sourceComparison(OWNER, FROM, TO)).containsExactlyElementsOf(expected);
    verify(permissions).require(OWNER, new ResourceKey("reviews"), PermissionAction.READ);
  }

  @Test
  void authorizesAndReturnsReviews() {
    ReviewMetricsQuery query = mock(ReviewMetricsQuery.class);
    ReviewFilter filter = new ReviewFilter(FROM, TO, "opentable", "POSITIVE", "food", 4, "pizza");
    ReviewView expected =
        new ReviewView(
            "rev-1",
            "opentable",
            Instant.parse("2026-10-12T10:00:00Z"),
            5,
            "POSITIVE",
            0.93,
            new BigDecimal("4.7"),
            "Great pizza",
            "Sam",
            List.of(new AspectSentimentView("food", "POSITIVE", 0.95)));
    when(query.reviews(filter)).thenReturn(List.of(expected));
    PermissionService permissions = mock(PermissionService.class);
    ReviewReportingService service = new ReviewReportingService(query, permissions);

    assertThat(service.reviews(OWNER, filter)).containsExactly(expected);
    verify(permissions).require(OWNER, new ResourceKey("reviews"), PermissionAction.READ);
  }
}
