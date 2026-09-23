package com.goldys.platform.connectors.opentable;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Playwright-backed GuestCenter pull: login, open the reservations report, capture the CSV export.
 */
public class PlaywrightOpenTableClient implements OpenTableClient {
  private static final String BASE = "https://guestcenter.opentable.com";

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;

  @Override
  public void login(String email, String password) {
    ensureBrowser();
    context = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(true));
    Page page = context.newPage();
    page.navigate(BASE + "/login");
    page.locator("input[name=email]").fill(email);
    page.locator("input[name=password]").fill(password);
    page.locator("button[type=submit]").click();
    page.waitForURL("**/guestcenter/**");
  }

  private void ensureBrowser() {
    if (browser == null) {
      try {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
      } catch (RuntimeException e) {
        throw new ConnectorFetchException("CONNECTOR_BROWSER_FAILED", "Chromium launch failed", e);
      }
    }
  }

  @Override
  public byte[] exportReservationsCsv(LocalDate from, LocalDate to) {
    if (context == null) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "login() must be called before exporting reservations");
    }
    Page page = context.newPage();
    page.navigate(BASE + "/reports/reservations");
    page.locator("input[name=from]").fill(from.format(DateTimeFormatter.ISO_LOCAL_DATE));
    page.locator("input[name=to]").fill(to.format(DateTimeFormatter.ISO_LOCAL_DATE));
    Download download =
        page.waitForDownload(
            () ->
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export"))
                    .click());
    try {
      return Files.readAllBytes(download.path());
    } catch (IOException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "OpenTable CSV download failed", e);
    }
  }
}
