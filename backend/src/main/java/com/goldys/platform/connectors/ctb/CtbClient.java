package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authenticated HTTP client for CTB's internal AJAX endpoints (ASP.NET MVC, session-cookie auth, no
 * public API).
 *
 * <p>Request mechanics (from the research repo's {@code cooking_the_books_api.py}): every data call
 * is a {@code POST} with {@code application/x-www-form-urlencoded} body. List/search endpoints page
 * with {@code start} + {@code limit} and return {@code totalCount}; {@code GetAll*}/reference
 * endpoints return the full payload in one call (empty body). A permission failure comes back as an
 * envelope with {@code IsSuccess: false} and {@code Info} carrying the HTML string {@code "You
 * don't have authority…"}; this is surfaced as {@code CONNECTOR_PERMISSION_DENIED}, a distinct
 * failure from a parse error or a generic fetch failure.
 */
public class CtbClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger log = LoggerFactory.getLogger(CtbClient.class);

  private final String baseUrl;
  private final HttpClient http;

  public CtbClient(String baseUrl) {
    this.baseUrl = baseUrl;
    this.http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
  }

  public void login(String email, String password) {
    // Establish the ASP.NET session first: the login POST alone does not set the session
    // cookie, but a GET to a session-backed page (this one 302s to /Default/Login) does.
    get("/Default/Home2");
    String body = post("/Account/Login", form("userEmail", email, "userPassword", password));
    if (!isSuccess(body)) {
      throw new ConnectorFetchException("CONNECTOR_AUTH_FAILED", "CTB login failed");
    }
  }

  /**
   * True when a CTB response reports success. CTB uses two envelopes: the login returns {@code
   * IsSuccess} at the top level, while data endpoints wrap it as {@code message: {IsSuccess, ...}}.
   * Accept either.
   */
  static boolean isSuccess(String body) {
    return isSuccess(parse(body));
  }

  private static boolean isSuccess(JsonNode json) {
    // CTB's success signal varies by endpoint. The login returns a top-level IsSuccess; the
    // older data envelope used message.IsSuccess; the current data endpoints return `data`
    // with no IsSuccess field at all. Prefer an explicit IsSuccess flag when present, and
    // otherwise treat a response without one as success (the data-endpoint shape).
    JsonNode topIsSuccess = json.get("IsSuccess");
    if (topIsSuccess != null && topIsSuccess.isBoolean()) {
      return topIsSuccess.asBoolean();
    }
    JsonNode nestedIsSuccess = json.path("message").get("IsSuccess");
    if (nestedIsSuccess != null && nestedIsSuccess.isBoolean()) {
      return nestedIsSuccess.asBoolean();
    }
    return true;
  }

  /**
   * True when a CTB response is a permission failure. A denied action returns a JSON envelope whose
   * {@code Info} (and {@code message.Info}) carries the HTML {@code "You don't have authority…"}.
   * Checking the raw body (before JSON parsing) also catches a bare HTML denial page.
   */
  static boolean isPermissionDenied(String body) {
    return body != null && body.toLowerCase(Locale.ROOT).contains("you don't have authority");
  }

  /** One page of revenue rows plus the total record count, returned as raw JSON for the sink. */
  public CtbPage searchRevenues(int start, int limit) {
    return page("Revenue/SearchRevenues", post("/Revenue/SearchRevenues", pagedForm(start, limit)));
  }

  /** One page of sale items plus the total count, as raw JSON for the sink. */
  public CtbPage searchSaleItems(String fromDate, String toDate, int start, int limit) {
    return page(
        "Sale/SearchSaleItemsByDateRange",
        post(
            "/Sale/SearchSaleItemsByDateRange",
            form(
                "fromDate",
                fromDate,
                "toDate",
                toDate,
                "searchType",
                "-1",
                "start",
                String.valueOf(start),
                "limit",
                String.valueOf(limit))));
  }

  /** One page of supplier invoices plus the total count. */
  public CtbPage searchInvoices(int start, int limit) {
    return page("Invoice/SearchInvoices", post("/Invoice/SearchInvoices", pagedForm(start, limit)));
  }

  /**
   * One page of POS-sale-item → recipe links plus the total count.
   *
   * <p>Note: the research puller called this with no params and captured only 100 of the reported
   * {@code totalCount} 524 rows. We page explicitly ({@code start}/{@code limit}, no {@code
   * keyword} — the puller sent none) so all rows are pulled; the sample's shortfall is a
   * research-puller artefact, not an endpoint behaviour.
   */
  public CtbPage searchDistinctSaleItemsForLinking(int start, int limit) {
    return page(
        "Sale/SearchDistinctSaleItemsForLinkingWithRecipes",
        post(
            "/Sale/SearchDistinctSaleItemsForLinkingWithRecipes",
            form("start", String.valueOf(start), "limit", String.valueOf(limit))));
  }

  /** All recipes (id + name). Not paginated; returns the full array in one call. */
  public CtbPage getAllRecipes() {
    return page("RecipeBook/GetAllRecipes", post("/RecipeBook/GetAllRecipes", ""));
  }

  /** One page of stock items (master data) plus the total count. */
  public CtbPage searchStocks(int start, int limit) {
    return page("Stock/SearchStocks", post("/Stock/SearchStocks", pagedForm(start, limit)));
  }

  /** All suppliers (full record set). Not paginated. */
  public CtbPage getAllSuppliers() {
    return page("Supplier/GetAllSuppliers", post("/Supplier/GetAllSuppliers", ""));
  }

  /** One page of stocktake records plus the total count. */
  public CtbPage searchStocktakes(int start, int limit) {
    return page(
        "Stocktake/SearchStocktakes", post("/Stocktake/SearchStocktakes", pagedForm(start, limit)));
  }

  /** One page of wastage records plus the total count. */
  public CtbPage searchWastageRecords(int start, int limit) {
    return page(
        "WastageRecord/SearchWastageRecords",
        post("/WastageRecord/SearchWastageRecords", pagedForm(start, limit)));
  }

  /** One page of purchase orders plus the total count. */
  public CtbPage searchStockOrders(int start, int limit) {
    return page(
        "StockOrder/SearchStockOrders",
        post("/StockOrder/SearchStockOrders", pagedForm(start, limit)));
  }

  /**
   * One page of supplier statements plus the total count. CTB's statement search takes a rolling
   * date range in addition to {@code start}/{@code limit} (the research puller used 12 months).
   */
  public CtbPage searchStatements(int start, int limit, String startDate, String endDate) {
    return page(
        "ProformaInvoice/SearchStatement",
        post(
            "/ProformaInvoice/SearchStatement",
            form(
                "keyword",
                "",
                "start",
                String.valueOf(start),
                "limit",
                String.valueOf(limit),
                "startDate",
                startDate,
                "endDate",
                endDate)));
  }

  /**
   * CTB's own POS-vs-expected variance report. Params are a date range, consistent with the other
   * {@code Sale/*} date-driven actions; the research repo did not pull this endpoint, so the exact
   * params are unconfirmed against the live account.
   */
  public CtbPage getVarianceReportData(String fromDate, String toDate) {
    return page(
        "Sale/GetVarianceReportData",
        post("/Sale/GetVarianceReportData", form("fromDate", fromDate, "toDate", toDate)));
  }

  /**
   * Dates with no revenue entry (feeds "ingestion gaps are visible"). Not pulled by the research
   * repo; params are unconfirmed against the live account — called with an empty body.
   */
  public CtbPage missingRevenueReport() {
    return page("Report/MissingRevenueReport", post("/Report/MissingRevenueReport", ""));
  }

  /** Business departments (Food/Beverage). Reference data. */
  public CtbPage getAllDepartments() {
    return page(
        "BusinessDepartmentActivity/GetAllDepartments",
        post("/BusinessDepartmentActivity/GetAllDepartments", ""));
  }

  /** Business department activities. Reference data. */
  public CtbPage getAllActivities() {
    return page(
        "BusinessDepartmentActivity/GetAllActivities",
        post("/BusinessDepartmentActivity/GetAllActivities", ""));
  }

  /** Stock categories. Reference data. */
  public CtbPage getAllStockCategories() {
    return page(
        "StockCategory/GetAllStockCategories", post("/StockCategory/GetAllStockCategories", ""));
  }

  /** Units of measurement. Reference data. */
  public CtbPage getAllUoms() {
    return page("UnitOfMeasurement/GetAllUOMs", post("/UnitOfMeasurement/GetAllUOMs", ""));
  }

  /** Distinct supplier-side measurements. Reference data. */
  public CtbPage getAllSupplierMeasurements() {
    return page(
        "UnitOfMeasurement/GetAllDistinctSupplierMeasurements",
        post("/UnitOfMeasurement/GetAllDistinctSupplierMeasurements", ""));
  }

  /** Unit-of-measurement conversion matrix. Reference data. */
  public CtbPage getAllMeasurementConversions() {
    return page(
        "MeasurementConversion/GetAllMeasurementConversions",
        post("/MeasurementConversion/GetAllMeasurementConversions", ""));
  }

  /**
   * Validate a CTB data-endpoint response and wrap it as a page. Order matters: a permission-denied
   * response is checked on the raw body first (it may be JSON with HTML in {@code Info}, or a bare
   * HTML denial page), then the envelope's success flag, then the {@code totalCount} fallback.
   */
  private CtbPage page(String action, String body) {
    if (isPermissionDenied(body)) {
      throw new ConnectorFetchException(
          "CONNECTOR_PERMISSION_DENIED", "CTB permission denied: " + action);
    }
    JsonNode json = parse(body);
    if (!isSuccess(json)) {
      log.warn("CTB {} failed; response: {}", action, body);
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "CTB " + action + " failed: " + truncate(body, 500));
    }
    int total = json.path("totalCount").asInt(json.path("data").size());
    return new CtbPage(body, total);
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }

  private String get(String path) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Accept", "text/html,application/xhtml+xml")
            .GET()
            .build();
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "CTB request failed: " + path, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "Interrupted: " + path, e);
    }
  }

  private String post(String path, String form) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*")
            .header("Referer", baseUrl + "/Default/Home2")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "CTB request failed: " + path, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "Interrupted: " + path, e);
    }
  }

  /** The standard paged-list form: {@code keyword=&start=N&limit=M} (matches CTB's ExtJS grids). */
  private static String pagedForm(int start, int limit) {
    return form("keyword", "", "start", String.valueOf(start), "limit", String.valueOf(limit));
  }

  private static String form(String... kv) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < kv.length; i += 2) {
      if (i > 0) sb.append('&');
      sb.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8));
      sb.append('=');
      sb.append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
    }
    return sb.toString();
  }

  private static JsonNode parse(String body) {
    try {
      return MAPPER.readTree(body);
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response", e);
    }
  }

  /** Raw JSON body of one page plus the total record count across all pages. */
  public record CtbPage(String json, int totalCount) {}
}
