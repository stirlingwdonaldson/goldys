package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.semantic.ProductReportingMapping;
import com.goldys.platform.semantic.ProductReportingMapping.*;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductReportingMappingServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final FeatureTextExtractor extractor = mock(FeatureTextExtractor.class);
  private final UserRole owner =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private final ProductReportingMappingService service =
      new ProductReportingMappingService(jdbc, permissions, extractor);

  @Test
  void permission_denial_precedes_candidate_or_database_access() {
    doThrow(new AccessDeniedException("denied")).when(permissions).require(any(), any(), any());
    assertThatThrownBy(() -> service.append(owner, "owner", null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(jdbc, extractor);
  }

  @Test
  void ambiguous_name_cannot_be_approved_as_a_reporting_alias() {
    when(extractor.candidates())
        .thenReturn(Map.of("lager", new FeatureTextExtractor.Candidate("two source IDs", true)));
    assertThatThrownBy(
            () -> service.append(owner, "owner", mapping(SalesNature.BEVERAGE, StockBucket.KEGS)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ambiguous");
    verifyNoInteractions(jdbc);
  }

  @Test
  void merchandise_beside_beverage_stock_is_never_beverage_sales() {
    assertThatThrownBy(
            () ->
                ProductReportingMappingService.validate(
                    mapping(SalesNature.BEVERAGE, StockBucket.MERCHANDISE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Merchandise");
    assertThatCode(
            () ->
                ProductReportingMappingService.validate(
                    mapping(SalesNature.OTHER, StockBucket.MERCHANDISE)))
        .doesNotThrowAnyException();
  }

  @Test
  void preview_permission_denial_precedes_any_identity_or_impact_read() {
    doThrow(new AccessDeniedException("denied")).when(permissions).require(any(), any(), any());
    assertThatThrownBy(() -> service.preview(owner, null, LocalDate.now()))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(jdbc, extractor);
  }

  private ProductReportingMapping mapping(SalesNature nature, StockBucket bucket) {
    return new ProductReportingMapping(
        Scope.PRODUCT_NAME,
        "RESOLVED",
        "lager",
        LocalDate.of(2026, 11, 3),
        nature,
        bucket,
        ItemRole.UNKNOWN,
        BusinessArea.UNKNOWN,
        Lifecycle.UNKNOWN,
        "reviewed source evidence");
  }
}
