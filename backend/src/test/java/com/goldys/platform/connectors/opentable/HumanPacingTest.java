package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HumanPacingTest {

  @Test
  void charDelayStaysWithinBounds() {
    for (int i = 0; i < 200; i++) {
      assertThat(HumanPacing.charDelayMs()).isBetween(40, 90);
    }
  }

  @Test
  void pauseStaysWithinBounds() {
    for (int i = 0; i < 200; i++) {
      assertThat(HumanPacing.pauseMs()).isBetween(300, 1500);
    }
  }

  @Test
  void mouseStepsStayWithinBounds() {
    for (int i = 0; i < 200; i++) {
      assertThat(HumanPacing.mouseSteps()).isBetween(10, 20);
    }
  }
}
