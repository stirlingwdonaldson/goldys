package com.goldys.platform.connectors.opentable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Randomized human-like pacing for browser interaction. Instant fill/click is a bot signal; the
 * client uses these bounds to type, move, and pause like a person. Timing only — no browser code.
 */
final class HumanPacing {
  private HumanPacing() {}

  static int charDelayMs() {
    return ThreadLocalRandom.current().nextInt(40, 91); // [40, 90]
  }

  static int pauseMs() {
    return ThreadLocalRandom.current().nextInt(300, 1501); // [300, 1500]
  }

  static int mouseSteps() {
    return ThreadLocalRandom.current().nextInt(10, 21); // [10, 20]
  }

  static void sleep(int ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
