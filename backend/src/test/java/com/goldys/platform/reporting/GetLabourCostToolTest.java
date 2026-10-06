package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.widget.TableWidgetSpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GetLabourCostToolTest {

  private static final LocalDate FROM = LocalDate.of(2026, 9, 20);
  private static final LocalDate TO = LocalDate.of(2026, 9, 20);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void emitsResolvedLabourTable() {
    LabourMetricsQuery labour = mock(LabourMetricsQuery.class);
    when(labour.scheduledHours(FROM, TO)).thenReturn(new BigDecimal("14.00"));
    when(labour.actualHours(FROM, TO)).thenReturn(new BigDecimal("13.50"));
    when(labour.labourCost(FROM, TO)).thenReturn(new BigDecimal("350.00"));
    when(labour.scheduledVsActualVariance(FROM, TO)).thenReturn(new BigDecimal("0.50"));

    GetLabourCostTool tool = new GetLabourCostTool(labour);
    ToolResult result = tool.execute(new GetLabourCostInput(FROM, TO), OWNER);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat(widget.rows()).hasSize(1);
    assertThat((BigDecimal) widget.rows().get(0).get("labourCost"))
        .isEqualByComparingTo(new BigDecimal("350.00"));
    assertThat(result.notices()).isEmpty();
  }

  @Test
  void emitsNoticeWhenCostIsUnknown() {
    LabourMetricsQuery labour = mock(LabourMetricsQuery.class);
    when(labour.scheduledHours(FROM, TO)).thenReturn(new BigDecimal("14.00"));
    when(labour.actualHours(FROM, TO)).thenReturn(new BigDecimal("13.50"));
    when(labour.labourCost(FROM, TO)).thenReturn(null);
    when(labour.scheduledVsActualVariance(FROM, TO)).thenReturn(new BigDecimal("0.50"));

    GetLabourCostTool tool = new GetLabourCostTool(labour);
    ToolResult result = tool.execute(new GetLabourCostInput(FROM, TO), OWNER);

    assertThat(result.notices()).hasSize(1);
    assertThat(result.notices().get(0)).contains("unknown");
  }

  @Test
  void rejectsEndDateBeforeStartDate() {
    assertThatThrownBy(
            () -> new GetLabourCostInput(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 20)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startDate");
  }

  @Test
  void rejectsWrongInputType() {
    GetLabourCostTool tool = new GetLabourCostTool(mock(LabourMetricsQuery.class));

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetLabourCostInput");
  }

  private record OtherInput() implements ToolInput {}
}
