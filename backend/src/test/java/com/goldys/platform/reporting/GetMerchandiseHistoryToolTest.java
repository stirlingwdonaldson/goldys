package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.semantic.MerchandiseHistoryQuery;
import com.goldys.platform.widget.TableWidgetSpec;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetMerchandiseHistoryToolTest {
  @Test
  void retainsMissingCoverageAndHistoricalQualifications() {
    var query = mock(MerchandiseHistoryQuery.class);
    when(query.coverage())
        .thenReturn(List.of(new MerchandiseHistoryQuery.Coverage("Lightspeed", null, null, 0, 0)));
    var result =
        new GetMerchandiseHistoryTool(query)
            .execute(
                new GetMerchandiseHistoryInput(
                    GetMerchandiseHistoryInput.Mode.COVERAGE, null, null, 0),
                null);
    assertThat(((TableWidgetSpec) result.widget()).rows().getFirst())
        .containsEntry("firstDate", null);
    assertThat(String.join(" ", result.notices()))
        .contains("2025", "not zero sales", "not certify");
  }

  @Test
  void validatesBoundedDatesAndLimit() {
    var date = LocalDate.of(2020, 11, 1);
    assertThatThrownBy(
            () ->
                new GetMerchandiseHistoryInput(
                    GetMerchandiseHistoryInput.Mode.PRODUCTS, date, null, 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new GetMerchandiseHistoryInput(
                    GetMerchandiseHistoryInput.Mode.PRODUCTS, date, date.plusDays(3661), 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new GetMerchandiseHistoryInput(
                    GetMerchandiseHistoryInput.Mode.PRODUCTS, date, date, 101))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new GetMerchandiseHistoryInput(
                    GetMerchandiseHistoryInput.Mode.COVERAGE, date, date, 20))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
