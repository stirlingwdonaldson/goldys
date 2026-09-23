package com.goldys.platform.connectors.opentable;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Fortress-backed GuestCenter client. Connects to a Fortress stealth-Chromium sidecar over CDP,
 * reuses a persisted storage_state session, and logs in with human-like pacing only when needed.
 */
public class FortressOpenTableClient implements OpenTableClient {
  private static final String BASE = "https://guestcenter.opentable.com";

  private final String cdpUrl;
  private final String email;
  private final String password;
  private final Path sessionPath;

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;

  public FortressOpenTableClient(String cdpUrl, String email, String password, Path sessionPath) {
    this.cdpUrl = cdpUrl;
    this.email = email;
    this.password = password;
    this.sessionPath = sessionPath;
  }

  @Override
  public void authenticate() {
    ensureConnected();
    if (tryReuseSession()) {
      return;
    }
    login();
  }

  private boolean tryReuseSession() {
    if (!Files.exists(sessionPath)) {
      return false;
    }
    try {
      if (context != null) {
        context.close();
        context = null;
      }
      context =
          browser.newContext(
              new Browser.NewContextOptions()
                  .setAcceptDownloads(true)
                  .setStorageStatePath(sessionPath));
      try (Page page = context.newPage()) {
        page.navigate(BASE + "/reports/reservations");
        return !page.url().contains("/login") && !page.url().contains("restauth");
      }
    } catch (RuntimeException e) {
      if (context != null) {
        context.close();
        context = null;
      }
      return false;
    }
  }

  private void login() {
    try {
      if (context != null) {
        context.close();
      }
      context = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(true));
      Page page = context.newPage();
      page.navigate(BASE + "/login");

      // Stage 1: GuestCenter email -> Continue -> Okta.
      Locator emailField = page.locator("input[name=email]");
      moveMouse(page, emailField);
      typeHuman(page, emailField, email);
      pause();
      page.locator("button[type=submit]").click();
      page.waitForURL("**restauth.opentable.com/**");

      // Stage 2: Okta identifier -> Next. (Fix 8, fold in: wait for the field; inputValue is never
      // null.)
      Locator identifier = page.locator("input[name=identifier]");
      identifier.waitFor();
      if (identifier.inputValue().isBlank()) {
        moveMouse(page, identifier);
        typeHuman(page, identifier, email);
      }
      pause();
      page.locator("input[type=submit]").click();
      page.waitForSelector("input[type=password]");

      // Stage 3: Okta password -> Sign in (invisible hCaptcha may auto-pass or challenge).
      Locator passwordField = page.locator("input[type=password]");
      moveMouse(page, passwordField);
      typeHuman(page, passwordField, password);
      pause();
      page.locator("input[type=submit]").click();
      page.waitForURL("**guestcenter.opentable.com/**");
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_AUTH_FAILED",
          "OpenTable login failed (hCaptcha challenge or bad credentials); a manual re-auth may be "
              + "required by placing a valid session at "
              + sessionPath,
          e);
    }
    persistSession();
  }

  private void persistSession() {
    try {
      if (sessionPath.getParent() != null) {
        Files.createDirectories(sessionPath.getParent());
      }
      context.storageState(new BrowserContext.StorageStateOptions().setPath(sessionPath));
      try {
        Files.setPosixFilePermissions(
            sessionPath, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
        // non-POSIX filesystem; leave default permissions
      }
    } catch (IOException | RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "Failed to persist the OpenTable session at " + sessionPath, e);
    }
  }

  private void typeHuman(Page page, Locator field, String text) {
    field.click();
    page.keyboard().type(text, new Keyboard.TypeOptions().setDelay(HumanPacing.charDelayMs()));
  }

  private void moveMouse(Page page, Locator locator) {
    BoundingBox box = locator.boundingBox();
    if (box != null) {
      page.mouse()
          .move(
              box.x + box.width / 2,
              box.y + box.height / 2,
              new Mouse.MoveOptions().setSteps(HumanPacing.mouseSteps()));
    }
  }

  private void pause() {
    HumanPacing.sleep(HumanPacing.pauseMs());
  }

  private void ensureConnected() {
    if (browser == null) {
      Playwright pw = null;
      try {
        pw = Playwright.create();
        browser = pw.chromium().connectOverCDP(cdpUrl);
        playwright = pw;
      } catch (RuntimeException e) {
        if (pw != null) {
          pw.close();
        }
        throw new ConnectorFetchException(
            "CONNECTOR_BROWSER_FAILED", "Fortress CDP unreachable at " + cdpUrl, e);
      }
    }
  }

  @Override
  public byte[] exportReservationsCsv(LocalDate from, LocalDate to) {
    if (context == null) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "authenticate() must be called before exporting reservations");
    }
    try (Page page = context.newPage()) {
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
    } catch (ConnectorFetchException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "OpenTable reservation export failed", e);
    }
  }

  @Override
  public void close() {
    if (context != null) {
      context.close();
      context = null;
    }
    if (browser != null) {
      browser.close();
      browser = null;
    }
    if (playwright != null) {
      playwright.close();
      playwright = null;
    }
  }
}
