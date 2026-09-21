package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductSalesOverrideServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  private static CanonicalProductSalesQuery queryWithCtb() {
    CanonicalProductSalesQuery query = mock(CanonicalProductSalesQuery.class);
    when(query.currentProductSalesForDate(SEP_14))
        .thenReturn(
            List.of(new ProductSalesView("CTB", SEP_14, "garlic aioli", bd("127"), bd("322.46"))));
    return query;
  }

  @Test
  void deniedUserGetsAccessDenied() {
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());
    ProductSalesOverrideService service =
        new ProductSalesOverrideService(
            mock(ProductSalesOverrideRepository.class), permissions, queryWithCtb());

    assertThatThrownBy(() -> service.save(OWNER, "a@b.com", SEP_14, "garlic aioli", "CTB", null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void unknownSourceIsRejected() {
    ProductSalesOverrideService service =
        new ProductSalesOverrideService(
            mock(ProductSalesOverrideRepository.class),
            mock(PermissionService.class),
            queryWithCtb());

    assertThatThrownBy(
            () -> service.save(OWNER, "a@b.com", SEP_14, "garlic aioli", "LIGHTSPEED", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void permittedSaveRecordsTheOverride() {
    ProductSalesOverrideRepository repository = mock(ProductSalesOverrideRepository.class);
    when(repository.lockCurrent("garlic aioli", SEP_14)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    ProductSalesOverrideService service =
        new ProductSalesOverrideService(repository, mock(PermissionService.class), queryWithCtb());

    ProductSalesOverride saved =
        service.save(OWNER, "a@b.com", SEP_14, "garlic aioli", "CTB", "typo");

    assertThat(saved.authoritativeSource()).isEqualTo("CTB");
    assertThat(saved.productNameKey()).isEqualTo("garlic aioli");
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
