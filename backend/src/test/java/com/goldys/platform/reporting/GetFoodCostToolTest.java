package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.InventoryMetricsQuery;
import com.goldys.platform.widget.TableWidgetSpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GetFoodCostToolTest {

  private static final LocalDate FROM = LocalDate.of(2026, 9, 20);
  private static final LocalDate TO = LocalDate.of(2026, 9, 20);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void emitsResolvedFoodCostTable() {
    InventoryMetricsQuery inventory = mock(InventoryMetricsQuery.class);
    when(inventory.purchases(FROM, TO)).thenReturn(new BigDecimal("70.00"));
    when(inventory.wastage(FROM, TO)).thenReturn(null);

    GetFoodCostTool tool = new GetFoodCostTool(inventory);
    ToolResult result = tool.execute(new GetFoodCostInput(FROM, TO), OWNER);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat((BigDecimal) widget.rows().get(0).get("purchases"))
        .isEqualByComparingTo(new BigDecimal("70.00"));
  }

  @Test
  void rejectsEndDateBeforeStartDate() {
    assertThatThrownBy(
            () -> new GetFoodCostInput(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 20)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startDate");
  }

  @Test
  void rejectsWrongInputType() {
    GetFoodCostTool tool = new GetFoodCostTool(mock(InventoryMetricsQuery.class));

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetFoodCostInput");
  }

  private record OtherInput() implements ToolInput {}
}
