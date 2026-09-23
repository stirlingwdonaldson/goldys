package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Proves headless Chromium launches in this environment. Opt-in; needs Chromium installed. */
@EnabledIfEnvironmentVariable(named = "PLAYWRIGHT_SMOKE", matches = "true")
class PlaywrightSmokeTest {
  @Test
  void launchesHeadlessChromium() {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
      Page page = browser.newPage();
      page.setContent("<title>ok</title>");
      assertThat(page.title()).isEqualTo("ok");
    }
  }
}
