package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Proves the Fortress CDP endpoint is reachable in this environment. Opt-in. */
@EnabledIfEnvironmentVariable(named = "OPENTABLE_SMOKE", matches = "true")
class FortressSmokeTest {
  @Test
  void connectsOverCdpAndLoadsAPage() {
    String cdpUrl =
        System.getenv().getOrDefault("OPENTABLE_FORTRESS_CDP_URL", "http://localhost:9222");
    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().connectOverCDP(cdpUrl);
      Page page = browser.newPage();
      page.setContent("<title>ok</title>");
      assertThat(page.title()).isEqualTo("ok");
      browser.close();
    }
  }
}
