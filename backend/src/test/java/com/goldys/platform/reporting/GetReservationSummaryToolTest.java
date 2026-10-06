package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.ReservationSummary;
import com.goldys.platform.widget.TableWidgetSpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetReservationSummaryToolTest {

  private static final LocalDate SEP_20 = LocalDate.of(2026, 9, 20);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void emitsResolvedSummaryTable() {
    ReservationMetricsQuery metrics = mock(ReservationMetricsQuery.class);
    when(metrics.summary(SEP_20))
        .thenReturn(
            Optional.of(
                new ReservationSummary(
                    SEP_20,
                    10,
                    8,
                    32,
                    1,
                    1,
                    2,
                    new BigDecimal("4.0000"),
                    new BigDecimal("0.1000"),
                    new BigDecimal("0.8000"))));

    GetReservationSummaryTool tool = new GetReservationSummaryTool(metrics);
    ToolResult result = tool.execute(new GetReservationSummaryInput(SEP_20), OWNER);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat(widget.rows()).hasSize(1);
    assertThat(widget.rows().get(0).get("bookings")).isEqualTo(10L);
    assertThat(widget.rows().get(0).get("covers")).isEqualTo(32L);
    assertThat(widget.rows().get(0).get("noShows")).isEqualTo(1L);
    assertThat(result.notices()).isEmpty();
  }

  @Test
  void emitsNoticeWhenThereIsNoData() {
    ReservationMetricsQuery metrics = mock(ReservationMetricsQuery.class);
    when(metrics.summary(SEP_20)).thenReturn(Optional.empty());

    GetReservationSummaryTool tool = new GetReservationSummaryTool(metrics);
    ToolResult result = tool.execute(new GetReservationSummaryInput(SEP_20), OWNER);

    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat(widget.rows()).isEmpty();
    assertThat(result.notices()).hasSize(1);
    assertThat(result.notices().get(0)).contains("No reservation data");
  }

  @Test
  void rejectsWrongInputType() {
    GetReservationSummaryTool tool =
        new GetReservationSummaryTool(mock(ReservationMetricsQuery.class));

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetReservationSummaryInput");
  }

  private record OtherInput() implements ToolInput {}
}
