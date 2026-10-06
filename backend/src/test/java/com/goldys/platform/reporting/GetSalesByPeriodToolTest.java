package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reconciliation.ResolvedDailySalesQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetSalesByPeriodToolTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void emitsResolvedPointsAndNoticesUnresolvedDates() {
    ResolvedDailySalesQuery resolved = mock(ResolvedDailySalesQuery.class);
    when(resolved.between(SEP_13, SEP_14))
        .thenReturn(List.of(view(SEP_13, "27650.66", "agreed"), view(SEP_14, null, null)));

    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(resolved);
    ToolResult result =
        tool.execute(new GetSalesByPeriodInput(SEP_13, SEP_14, Metric.GROSS_SALES), OWNER);

    assertThat(result.widget().type()).isEqualTo("line-chart");
    assertThat(result.widget().data()).hasSize(1);
    assertThat(result.widget().data().get(0).get("date")).isEqualTo("2026-09-13");
    assertThat(result.notices()).hasSize(1);
    assertThat(result.notices().get(0)).contains("no resolved total");
  }

  @Test
  void treatsMissingDataAsUnresolved() {
    ResolvedDailySalesQuery resolved = mock(ResolvedDailySalesQuery.class);
    when(resolved.between(SEP_13, SEP_13)).thenReturn(List.of(view(SEP_13, null, null)));

    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(resolved);
    ToolResult result =
        tool.execute(new GetSalesByPeriodInput(SEP_13, SEP_13, Metric.GROSS_SALES), OWNER);

    assertThat(result.widget().data()).isEmpty();
    assertThat(result.notices()).hasSize(1);
  }

  @Test
  void rejectsEndDateBeforeStartDate() {
    assertThatThrownBy(() -> new GetSalesByPeriodInput(SEP_14, SEP_13, Metric.GROSS_SALES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startDate");
  }

  @Test
  void rejectsAWrongInputType() {
    ResolvedDailySalesQuery resolved = mock(ResolvedDailySalesQuery.class);
    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(resolved);

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetSalesByPeriodInput");
  }

  private static ResolvedDailySalesQuery.ResolvedDailySalesView view(
      LocalDate date, String total, String source) {
    boolean conflict = total == null;
    return new ResolvedDailySalesQuery.ResolvedDailySalesView(
        date,
        total == null ? null : new BigDecimal(total),
        conflict ? "conflict" : "agreed",
        source,
        conflict);
  }

  private record OtherInput() implements ToolInput {}
}
