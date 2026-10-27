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
  static final String SALE_ITEMS_SEARCH_TYPE = "-1";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger log = LoggerFactory.getLogger(CtbClient.class);

  private final String baseUrl;
  private final HttpClient http;

  public CtbClient(String baseUrl) {
    this.baseUrl = baseUrl;
    this.http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .build();
  }

  public void login(String email, String password) {
    // Establish the ASP.NET session first: the login POST alone does not set the session
    // cookie, but a GET to a session-backed page (this one 302s to /Default/Login) does.
    get("/Default/Home2");
    String body = post("/Account/Login", form("userEmail", email, "userPassword", password)).body();
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
                SALE_ITEMS_SEARCH_TYPE,
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
   * <p>Verified live: the endpoint honours {@code start}/{@code limit} (an empty-body call returns
   * a default page of 100), so paging with {@code start}/{@code limit} pulls all rows. The research
   * sample's 100 rows were its default page, not the whole set.
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

  public RecipeResponse getRecipeList() {
    return recipeResponse("RecipeBook/GetAllRecipes", post("/RecipeBook/GetAllRecipes", ""));
  }

  public RecipeResponse getRecipeDefinition(int recipeId) {
    return recipeResponse(
        "RecipeBook/GetRecipeByRecipeId",
        post("/RecipeBook/GetRecipeByRecipeId", form("recipeId", String.valueOf(recipeId))));
  }

  public RecipeResponse getRecipeDirectStocks(int recipeId) {
    return recipeResponse(
        "RecipeBook/GetRecipeDetailByRecipeId",
        post("/RecipeBook/GetRecipeDetailByRecipeId", form("recipeId", String.valueOf(recipeId))));
  }

  public RecipeResponse getRecipeUsages(int recipeId) {
    return recipeResponse(
        "RecipeBook/GetIngredientFromExistingRecipesByRecipeId",
        post(
            "/RecipeBook/GetIngredientFromExistingRecipesByRecipeId",
            form("recipeId", String.valueOf(recipeId))));
  }

  public RecipeResponse getRecipeSection(int recipeId, String section) {
    String endpoint = CtbRecipeRefresh.sectionEndpoint(section);
    String body = form("recipeId", String.valueOf(recipeId));
    if (section.equals("EQUIPMENT")) body += "&" + form("keyword", "", "start", "0", "limit", "50");
    else if (section.equals("DIETARY") || section.equals("ALLERGENS"))
      body += "&" + form("isSortByName", "true");
    else if (!section.equals("NUTRITION") && !section.equals("INGREDIENT_METHODS"))
      body += "&" + form("recipeFactType", section);
    return recipeResponse(endpoint, post(endpoint, body));
  }

  public RecipeResponse getRecipeEquipmentOptions() {
    return recipeResponse(
        "Equipment/SearchEquipments",
        post("/Equipment/SearchEquipments", form("keyword", "", "start", "0", "limit", "1000")));
  }

  public RecipeResponse getRecipeFolder(int folderId) {
    return recipeResponse(
        "RecipeFolder/GetTreeItemsUnderRecipeFolder",
        post(
            "/RecipeFolder/GetTreeItemsUnderRecipeFolder",
            form(
                "recipeFolderId",
                String.valueOf(folderId),
                "recipeName",
                "",
                "searchType",
                "-1",
                "regionId",
                "-1",
                "recipeStatus",
                "",
                "outletId",
                "-1",
                "belongToOutletId",
                "-1",
                "requisitionOrderSupplierId",
                "-1",
                "onlyCountableOnStocktakeRecipes",
                "false",
                "isShowCheckbox",
                "false",
                "isShowLeafCheckbox",
                "false")));
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
   * All supplier statements in a date range, as one response. Verified live: {@code
   * ProformaInvoice/SearchStatement} ignores {@code start}/{@code limit} and returns the full list
   * in a single call (it still reports {@code totalCount}), so it must not be paged — paging would
   * re-fetch the same rows on every page.
   */
  public CtbPage getStatements(String startDate, String endDate) {
    return page(
        "ProformaInvoice/SearchStatement",
        post(
            "/ProformaInvoice/SearchStatement",
            form("keyword", "", "startDate", startDate, "endDate", endDate)));
  }

  /**
   * One page of a stocktake's counted lines plus the total count. Paged with {@code start}/{@code
   * limit}.
   */
  public CtbPage getStocktakeDetail(int stocktakeId, int start, int limit) {
    return page(
        "Stocktake/GetStocktakeDetailByStocktakeId",
        post(
            "/Stocktake/GetStocktakeDetailByStocktakeId",
            form(
                "stocktakeId",
                String.valueOf(stocktakeId),
                "start",
                String.valueOf(start),
                "limit",
                String.valueOf(limit))));
  }

  /**
   * One page of a wastage record's item lines plus the total count. The CTB web app sends {@code
   * start}/{@code limit} with the id and the response carries {@code totalCount} (captured live
   * 2026-10-23: {@code start=0&limit=50&wastageRecordId=149}), so callers page by {@code
   * totalCount} rather than trusting one response to hold every line.
   */
  public CtbPage getWastageRecordDetail(int wastageRecordId, int start, int limit) {
    return page(
        "WastageRecord/GetWastageRecordDetailByWastageRecordId",
        post(
            "/WastageRecord/GetWastageRecordDetailByWastageRecordId",
            form(
                "start",
                String.valueOf(start),
                "limit",
                String.valueOf(limit),
                "wastageRecordId",
                String.valueOf(wastageRecordId))));
  }

  /** The ingredient lines of one recipe (incl. sub-recipe ingredients). Single response. */
  public CtbPage getIngredientsIncludingSubRecipeIngredients(int recipeId) {
    return page(
        "RecipeBook/GetIngredientsIncludingSubRecipeIngredients",
        post(
            "/RecipeBook/GetIngredientsIncludingSubRecipeIngredients",
            form("recipeId", String.valueOf(recipeId))));
  }

  /**
   * CTB's derived per-recipe cost, batched. {@code recipeIdsJson} is a JSON array (e.g. {@code
   * [627,628]}); {@code recipeFolderIds} must be {@code []} — without it the action returns a
   * server-error envelope.
   */
  public CtbPage getSelectedRecipeFullRecords(String recipeIdsJson) {
    return page(
        "RecipeBook/GetSelectedRecipeFullRecords",
        post(
            "/RecipeBook/GetSelectedRecipeFullRecords",
            form("recipeIds", recipeIdsJson, "recipeFolderIds", "[]")));
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
  private CtbPage page(String action, Response response) {
    String body = response.body();
    if (isPermissionDenied(body)) {
      throw new ConnectorFetchException(
          "CONNECTOR_PERMISSION_DENIED", "CTB permission denied: " + action);
    }
    JsonNode json = parse(action, response);
    if (!isSuccess(json)) {
      log.warn("CTB {} failed; response: {}", action, body);
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED",
          "CTB " + action + " failed (HTTP " + response.status() + "): " + truncate(body, 500));
    }
    int total = json.path("totalCount").asInt(json.path("data").size());
    if (action.equals("Sale/SearchSaleItemsByDateRange")
        && (!json.path("totalCount").isIntegralNumber()
            || !json.path("totalCount").canConvertToInt())) total = -1;
    return new CtbPage(body, total);
  }

  /**
   * Build the operator-facing detail for a non-JSON response. A non-JSON body is normally an HTML
   * login/redirect page or a server-error page, so the status, content-type and a bounded prefix
   * are the evidence needed to identify it without a re-run. Bounded (and matching the {@code
   * truncate} used for an explicit {@code IsSuccess:false}) so the message stays operator-sized;
   * the JSON agent's cause (a {@code JsonParseException}) is attached by the caller.
   */
  static String nonJsonDetail(String action, int status, String contentType, String body) {
    int length = body == null ? 0 : body.length();
    return "Non-JSON CTB response from "
        + action
        + " (HTTP "
        + status
        + ", "
        + contentType
        + ", "
        + length
        + " chars): "
        + truncate(body, 300);
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
            .timeout(java.time.Duration.ofSeconds(45))
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

  private Response post(String path, String form) {
    // IIS/ASP.NET answers a POST with no Content-Length with HTTP 411 "Length Required" and an HTML
    // error page. Java's HttpClient does not send Content-Length for an empty body (Python/urllib
    // and browsers do), so the empty-body GetAll* endpoints (recipes, suppliers, reference data)
    // failed to parse as JSON. Always send a non-empty form; the extra `keyword` is ignored by the
    // GetAll* actions.
    String body = requestBody(form);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(java.time.Duration.ofSeconds(45))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*")
            .header("Referer", baseUrl + "/Default/Home2")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      return new Response(
          response.body(),
          response.statusCode(),
          response.headers().firstValue("content-type").orElse(""));
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "CTB request failed: " + path, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "Interrupted: " + path, e);
    }
  }

  // Recipe responses are validated by the refresh only AFTER raw storage, including failures.
  private RecipeResponse recipeResponse(String action, Response response) {
    return new RecipeResponse(response.bytes(), response.status(), response.contentType());
  }

  public record RecipeResponse(byte[] bytes, int status, String contentType) {
    public RecipeResponse {
      bytes = bytes.clone();
    }

    public RecipeResponse(String json, int status) {
      this(json.getBytes(StandardCharsets.UTF_8), status, "application/json");
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }

    public String json() {
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }

  /**
   * One HTTP exchange: the raw body plus the status and content-type needed to explain a failure.
   */
  record Response(byte[] bytes, int status, String contentType) {
    String body() {
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }

  /**
   * IIS/ASP.NET returns HTTP 411 for a POST with no body/Content-Length, and Java's HttpClient does
   * not send Content-Length for an empty body. Substitute a non-empty form so every request carries
   * a body; the {@code GetAll*} actions ignore the extra {@code keyword} parameter.
   */
  static String requestBody(String form) {
    return form == null || form.isEmpty() ? "keyword=" : form;
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

  private static JsonNode parse(String action, Response response) {
    try {
      return MAPPER.readTree(response.body());
    } catch (IOException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH",
          nonJsonDetail(action, response.status(), response.contentType(), response.body()),
          e);
    }
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
