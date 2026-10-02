package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportingToolTest {

  @Test
  void exposesItsInputType() {
    ReportingTool tool = new GetSalesByPeriodTool(null);

    assertThat(tool.inputType()).isEqualTo(GetSalesByPeriodInput.class);
  }
}
