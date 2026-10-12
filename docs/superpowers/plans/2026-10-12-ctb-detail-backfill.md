# CTB Detail Backfill (Slice A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backfill the CTB line-detail responses (stocktake lines, wastage lines, recipe ingredients, batched recipe cost) into the raw ledger once, byte-faithfully, under one tracked `ctb-detail-backfill` run.

**Architecture:** A new `SourceConnector` (`CtbDetailBackfillConnector`) re-fetches the three header lists via the existing `CtbClient`, fans out one request per id, and streams each response to the `IngestionSink`. A `@ConditionalOnProperty` `ApplicationRunner` (`CtbDetailBackfillRunner`) constructs the connector itself (it is **not** a Spring bean) and drives it through the package-private `ConnectorRunner`, so the run status and per-record identities are derived for free. Raw ledger only — no canonical, projection, semantic, API, UI, or scheduling.

**Tech Stack:** Java 25, Spring Boot, Jackson, JUnit 5 + Mockito + AssertJ, Testcontainers (Postgres 16).

**Spec:** `docs/superpowers/specs/2026-10-12-ctb-detail-backfill-design.md` (on branch `docs/ctb-detail-backfill`; read via `git show docs/ctb-detail-backfill:docs/superpowers/specs/2026-10-12-ctb-detail-backfill-design.md`).

> **Execution environment:** implement in an isolated git worktree on a task branch (`feature/ctb-detail-backfill`) off `main` — never the production checkout, never `main` directly. The spec and this plan travel with the branch.

## Global Constraints

- Raw-ledger only. No `canonical_stock_count` / `canonical_wastage` / recipe-composition entity, no projector, no semantic query, no API/tool/dashboard, no scheduling, no stock-order lines.
- Connector `sourceSystem() = "CTB"`, `connectorName() = "ctb-detail-backfill"`.
- Fetcher identities, verbatim: `ctb-stocktake-detail`, `ctb-wastage-detail`, `ctb-recipe-ingredients`, `ctb-recipe-cost`.
- Every raw record: `fetch_method = API`, `content_type = application/json`, `character_encoding = UTF-8`.
- Do **not** register `CtbDetailBackfillConnector` as a Spring bean — `IngestionService` keys its connector map by `sourceSystem` (`Collectors.toMap`), and a second `CTB` bean collides. Construct it inside the runner.
- Gate the runner on `app.ctb-detail-backfill=true`, off by default (mirroring `app.ctb-invoice-reenrich` / `APP_CTB_INVOICE_REENRICH`).
- `RecipeBook/GetSelectedRecipeFullRecords` requires `recipeIds` as a JSON array **and** `recipeFolderIds=[]` (without the folder ids it returns a server-error envelope).
- Endpoints, params, and response shapes are already live-verified in the spec — do not re-probe. Header id fields: `data[].recipeId`, `data[].stocktakeId`, `data[].wastageRecordId`.
- Stocktake detail is paged (`start`/`limit`) until `totalCount`, using a high limit (1000). Wastage detail and recipe ingredients are one request per id. Recipe cost is one batched request.
- Re-running duplicates raw records (the ledger is append-only and does not dedupe on hash); acceptable for a one-off.
- Fan-out is sequential with a small inter-request delay between id-level requests (spec §4: "be polite"; value is an open question, a conservative fixed delay is fine).

## Review Focus

1. **Empty header lists** (no stocktakes/wastage/recipes) must write nothing and close `NO_NEW_DATA`, not throw or write a spurious `ctb-recipe-cost` record. → `CtbDetailBackfillConnectorTest.emptyHeaderListsWriteNothingAndDoNotThrow`.
2. **A failing header list** (e.g. `getAllRecipes` permission-denied) must skip only its own fan-out while the other two kinds still run, and be rethrown at the end (run closes `PARTIAL`). → `CtbDetailBackfillConnectorTest.aFailingHeaderListSkipsItsFanoutButOthersRun`.
3. **One id's detail failing mid-fanout** must be logged and skipped while remaining ids are still written, with the aggregated failure rethrown at the end. → `CtbDetailBackfillConnectorTest.perIdFailureIsIsolatedAndAggregated`.
4. **A stocktake with more than one page** (`totalCount` > 1000) must be paged until `totalCount`, writing one raw record per page. → `CtbDetailBackfillConnectorTest.stocktakeDetailPagesUntilTotalCount`.
5. **The batched recipe-cost call** must pass `recipeIds` as a compact JSON array; `recipeFolderIds=[]` is hardcoded inside `CtbClient.getSelectedRecipeFullRecords` (the part the connector test cannot see — it is pinned by the client method body and the spec's live evidence). → `CtbDetailBackfillConnectorTest.recipeCostUsesJsonArrayRecipeIds` (asserts the `recipeIds` argument is `"[627,628]"`).

---

## File Structure

- **Create** `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbDetailBackfillConnector.java` — the backfill connector.
- **Modify** `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbClient.java` — four detail-endpoint methods.
- **Create** `backend/src/main/java/com/goldys/platform/ingestion/CtbDetailBackfillRunner.java` — the `@ConditionalOnProperty` startup runner.
- **Modify** `.env.example` — document `APP_CTB_DETAIL_BACKFILL=true` in the CTB section.
- **Create** `backend/src/test/java/com/goldys/platform/connectors/ctb/CtbDetailBackfillConnectorTest.java` — unit test.
- **Create** `backend/src/test/java/com/goldys/platform/ingestion/CtbDetailBackfillRunnerTest.java` — runner delegation unit test.
- **Create** `backend/src/test/java/com/goldys/platform/ingestion/CtbDetailBackfillRunnerIntegrationTest.java` — Testcontainers integration test.
- **Modify** `docs/connectors/source-access.md` — add the four detail endpoints.
- **Modify** `docs/connectors/matching-and-identity.md` — note stock count / wastage now ingested raw; recipe-composition note.

---

### Task 1: The backfill connector (+ `CtbClient` detail methods) with unit test

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbDetailBackfillConnector.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbClient.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/CtbDetailBackfillConnectorTest.java`

**Interfaces:**
- Consumes: `CtbClient` (existing `searchStocktakes`, `searchWastageRecords`, `getAllRecipes`, `login`; new detail methods below), `SourceConnector`, `IngestionSink`, `FetchedPayload`, `FetchMethod`, `ConnectorFetchException`.
- Produces: `CtbDetailBackfillConnector` (constructor `(CtbClient, String email, String password)`), `CtbClient.getStocktakeDetail(int,int,int)`, `getWastageRecordDetail(int)`, `getIngredientsIncludingSubRecipeIngredients(int)`, `getSelectedRecipeFullRecords(String)`.

- [ ] **Step 1: Add the four `CtbClient` methods (no test — thin passthroughs, wire format live-verified).**

Add to `CtbClient.java`, following the existing `page(...)`/`post(...)`/`form(...)` pattern (all are `application/x-www-form-urlencoded` POSTs wrapped by `page()`):

```java
/** One page of a stocktake's counted lines plus the total count. */
public CtbPage getStocktakeDetail(int stocktakeId, int start, int limit) {
  return page("Stocktake/GetStocktakeDetailByStocktakeId",
      post("/Stocktake/GetStocktakeDetailByStocktakeId",
          form("stocktakeId", String.valueOf(stocktakeId),
               "start", String.valueOf(start), "limit", String.valueOf(limit))));
}

/** The per-item lines of one wastage record. Single response. */
public CtbPage getWastageRecordDetail(int wastageRecordId) {
  return page("WastageRecord/GetWastageRecordDetailByWastageRecordId",
      post("/WastageRecord/GetWastageRecordDetailByWastageRecordId",
          form("wastageRecordId", String.valueOf(wastageRecordId))));
}

/** The ingredient lines of one recipe (incl. sub-recipe ingredients). Single response. */
public CtbPage getIngredientsIncludingSubRecipeIngredients(int recipeId) {
  return page("RecipeBook/GetIngredientsIncludingSubRecipeIngredients",
      post("/RecipeBook/GetIngredientsIncludingSubRecipeIngredients",
          form("recipeId", String.valueOf(recipeId))));
}

/** CTB's derived per-recipe cost, batched. recipeIds is a JSON array string; recipeFolderIds must be [] (otherwise a server-error envelope). */
public CtbPage getSelectedRecipeFullRecords(String recipeIdsJson) {
  return page("RecipeBook/GetSelectedRecipeFullRecords",
      post("/RecipeBook/GetSelectedRecipeFullRecords",
          form("recipeIds", recipeIdsJson, "recipeFolderIds", "[]")));
}
```

- [ ] **Step 2: Compile.**

Run: `./gradlew compileJava` (from `backend/`).
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Write the failing connector test.**

Create `CtbDetailBackfillConnectorTest.java` in `com.goldys.platform.connectors.ctb`, mirroring `CtbConnectorTest` (Mockito `CtbClient`, a capturing sink that records each `FetchedPayload` into a `List<FetchedPayload>` and returns `UUID.randomUUID()`). Five tests:

```java
@Test
void oneRawWritePerDetailIdWithCorrectIdentity() {
  CtbClient client = client(
      /* stocktakes */ 2, /* wastage */ 1, /* recipes */ 2);
  CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
  List<FetchedPayload> written = new ArrayList<>();

  connector.fetch(null, p -> { written.add(p); return UUID.randomUUID(); });

  assertThat(written.stream().map(FetchedPayload::fetcherIdentity))
      .containsExactlyInAnyOrder(
          "ctb-stocktake-detail", "ctb-stocktake-detail",
          "ctb-wastage-detail",
          "ctb-recipe-ingredients", "ctb-recipe-ingredients",
          "ctb-recipe-cost");
  assertThat(written).allSatisfy(p -> {
    assertThat(p.fetchMethod()).isEqualTo(FetchMethod.API);
    assertThat(p.contentType()).isEqualTo("application/json");
    assertThat(p.characterEncoding()).isEqualTo("UTF-8");
  });
}

@Test
void stocktakeDetailPagesUntilTotalCount() {
  CtbClient client = mock(CtbClient.class);
  when(client.searchStocktakes(anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"stocktakeId\":38}],\"totalCount\":1}", 1));
  when(client.searchWastageRecords(anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[],\"totalCount\":0}", 0));
  when(client.getAllRecipes()).thenReturn(new CtbClient.CtbPage("{\"data\":[],\"totalCount\":0}", 0));
  when(client.getStocktakeDetail(38, 0, 1000))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"line\":1}],\"totalCount\":1500}", 1500));
  when(client.getStocktakeDetail(38, 1000, 1000))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"line\":2}],\"totalCount\":1500}", 1500));

  CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
  List<FetchedPayload> written = new ArrayList<>();
  connector.fetch(null, p -> { written.add(p); return UUID.randomUUID(); });

  verify(client).getStocktakeDetail(38, 0, 1000);
  verify(client).getStocktakeDetail(38, 1000, 1000);
  assertThat(written.stream().filter(p -> p.fetcherIdentity().equals("ctb-stocktake-detail"))).hasSize(2);
}

@Test
void recipeCostUsesJsonArrayRecipeIds() {
  CtbClient client = mock(CtbClient.class);
  when(client.searchStocktakes(anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[],\"totalCount\":0}", 0));
  when(client.searchWastageRecords(anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[],\"totalCount\":0}", 0));
  when(client.getAllRecipes())
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"recipeId\":627},{\"recipeId\":628}],\"totalCount\":2}", 2));
  when(client.getIngredientsIncludingSubRecipeIngredients(anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{}],\"totalCount\":1}", 1));
  when(client.getSelectedRecipeFullRecords(anyString()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{}],\"totalCount\":2}", 2));

  new CtbDetailBackfillConnector(client, "e", "p").fetch(null, p -> UUID.randomUUID());

  verify(client).getSelectedRecipeFullRecords("[627,628]");
}

@Test
void perIdFailureIsIsolatedAndAggregated() {
  CtbClient client = mock(CtbClient.class);
  when(client.searchStocktakes(anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"stocktakeId\":38}],\"totalCount\":1}", 1));
  when(client.searchWastageRecords(anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[],\"totalCount\":0}", 0));
  when(client.getAllRecipes())
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"recipeId\":627},{\"recipeId\":628}],\"totalCount\":2}", 2));
  when(client.getStocktakeDetail(anyInt(), anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{}],\"totalCount\":1}", 1));
  when(client.getIngredientsIncludingSubRecipeIngredients(627))
      .thenThrow(new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response"));
  when(client.getIngredientsIncludingSubRecipeIngredients(628))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{}],\"totalCount\":1}", 1));
  when(client.getSelectedRecipeFullRecords(anyString()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{}],\"totalCount\":2}", 2));

  CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
  List<FetchedPayload> written = new ArrayList<>();

  assertThatThrownBy(() -> connector.fetch(null, p -> { written.add(p); return UUID.randomUUID(); }))
      .isInstanceOf(ConnectorFetchException.class)
      .hasMessageContaining("ctb-recipe-ingredients#627");

  // 628 and the stocktake still land despite 627 failing.
  assertThat(written.stream().map(FetchedPayload::fetcherIdentity))
      .contains("ctb-recipe-ingredients", "ctb-stocktake-detail", "ctb-recipe-cost");
  assertThat(written.stream().filter(p -> p.fetcherIdentity().equals("ctb-recipe-ingredients"))).hasSize(1);
}

@Test
void emptyHeaderListsWriteNothingAndDoNotThrow() {
  CtbClient client = mock(CtbClient.class);
  when(client.searchStocktakes(anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[],\"totalCount\":0}", 0));
  when(client.searchWastageRecords(anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[],\"totalCount\":0}", 0));
  when(client.getAllRecipes()).thenReturn(new CtbClient.CtbPage("{\"data\":[],\"totalCount\":0}", 0));

  CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
  List<FetchedPayload> written = new ArrayList<>();

  connector.fetch(null, p -> { written.add(p); return UUID.randomUUID(); });

  assertThat(written).isEmpty();
  verify(client, never()).getSelectedRecipeFullRecords(anyString());
}
```

Also add a sixth test for Review Focus line 2:

```java
@Test
void aFailingHeaderListSkipsItsFanoutButOthersRun() {
  CtbClient client = mock(CtbClient.class);
  when(client.searchStocktakes(anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"stocktakeId\":38}],\"totalCount\":1}", 1));
  when(client.searchWastageRecords(anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[],\"totalCount\":0}", 0));
  when(client.getAllRecipes())
      .thenThrow(new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response"));
  when(client.getStocktakeDetail(anyInt(), anyInt(), anyInt()))
      .thenReturn(new CtbClient.CtbPage("{\"data\":[{}],\"totalCount\":1}", 1));

  CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
  List<FetchedPayload> written = new ArrayList<>();

  assertThatThrownBy(() -> connector.fetch(null, p -> { written.add(p); return UUID.randomUUID(); }))
      .isInstanceOf(ConnectorFetchException.class)
      .hasMessageContaining("recipe-headers");
  assertThat(written.stream().map(FetchedPayload::fetcherIdentity)).contains("ctb-stocktake-detail");
  verify(client, never()).getSelectedRecipeFullRecords(anyString());
}
```

- [ ] **Step 4: Run the test, confirm it fails (compilation: connector class absent).**

Run: `./gradlew test --tests com.goldys.platform.connectors.ctb.CtbDetailBackfillConnectorTest`
Expected: FAIL (cannot resolve `CtbDetailBackfillConnector`).

- [ ] **Step 5: Implement `CtbDetailBackfillConnector`.**

`package com.goldys.platform.connectors.ctb; implements SourceConnector`. Fields: `CtbClient client`, `String email`, `String password`; `static final ObjectMapper MAPPER = new ObjectMapper();`, `HEADER_PAGE_SIZE = 200`, `DETAIL_PAGE_SIZE = 1000`, `MAX_PAGES = 250`, `INTER_REQUEST_DELAY_MS = 250`. Constructor `(CtbClient, String, String)`.

`fetch(watermark, sink)` (exact control flow — order is stocktakes → wastage → recipes → batched cost):

```
client.login(email, password);
Map<String, ConnectorFetchException> failures = new LinkedHashMap<>();
List<Integer> stocktakeIds = collectIds("stocktake-headers", failures, this::stocktakeIds);
List<Integer> wastageIds   = collectIds("wastage-headers",   failures, this::wastageIds);
List<Integer> recipeIds    = collectIds("recipe-headers",    failures, this::recipeIds);
for (int id : stocktakeIds) pull("ctb-stocktake-detail#"  + id, failures, () -> pullStocktakeDetail(id, sink));
for (int id : wastageIds)   pull("ctb-wastage-detail#"    + id, failures, () -> pullWastageDetail(id, sink));
for (int id : recipeIds)    pull("ctb-recipe-ingredients#"+ id, failures, () -> pullRecipeIngredients(id, sink));
if (!recipeIds.isEmpty())   pull("ctb-recipe-cost", failures, () -> pullRecipeCost(recipeIds, sink));
if (!failures.isEmpty()) throw aggregate(failures);
```

A `politeDelay()` helper (`Thread.sleep(INTER_REQUEST_DELAY_MS)`; `InterruptedException` → `Thread.currentThread().interrupt()` + `ConnectorFetchException("CONNECTOR_FETCH_FAILED", "Interrupted during backfill delay", e)`) is called between id-level detail requests — once after each `pull(...)` in the three per-id loops, and not after the single batched cost call. (Conservative fixed value from spec §4; tunable via the constant.)

Private helpers (names + signatures only; bodies follow the existing `CtbConnector` pattern):
- `void pull(String key, Map<String,ConnectorFetchException> failures, Runnable attempt)` — try/run, catch `ConnectorFetchException` → `log.warn` + `failures.put(key, e)`. (Copy of `CtbConnector.pull`.)
- `ConnectorFetchException aggregate(Map<String,ConnectorFetchException> failures)` — single failure rethrown verbatim; several summarised as `"CTB detail backfill failed:\n- <key>: <type>: <msg>"` (mirrors `CtbConnector.aggregate`, different prefix).
- `List<Integer> collectIds(String key, Map<...> failures, Supplier<List<Integer>> ids)` — try/`ids.get()`, catch `ConnectorFetchException` → `log.warn` + `failures.put(key, e)` + return `List.of()`.
- `List<Integer> stocktakeIds()` — loop `start`/`HEADER_PAGE_SIZE` over `client.searchStocktakes(start, size)` until `totalCount`, `readIds(page.json(), "stocktakeId")`.
- `List<Integer> wastageIds()` — same over `client.searchWastageRecords`, field `"wastageRecordId"`.
- `List<Integer> recipeIds()` — `readIds(client.getAllRecipes().json(), "recipeId")` (single call).
- `static List<Integer> readIds(String json, String field)` — `MAPPER.readTree(json).path("data")`, if array collect `item.path(field).asInt()`; wrap `IOException` as `new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response", e)`.
- `void pullStocktakeDetail(int id, IngestionSink sink)` — loop `start`/`DETAIL_PAGE_SIZE` over `client.getStocktakeDetail(id, start, size)` until `totalCount`, `writeRaw(page.json(), "ctb-stocktake-detail", sink)`.
- `void pullWastageDetail(int id, IngestionSink sink)` — `writeRaw(client.getWastageRecordDetail(id).json(), "ctb-wastage-detail", sink)`.
- `void pullRecipeIngredients(int id, IngestionSink sink)` — `writeRaw(client.getIngredientsIncludingSubRecipeIngredients(id).json(), "ctb-recipe-ingredients", sink)`.
- `void pullRecipeCost(List<Integer> recipeIds, IngestionSink sink)` — `writeRaw(client.getSelectedRecipeFullRecords(jsonArray(recipeIds)).json(), "ctb-recipe-cost", sink)`.
- `static String jsonArray(List<Integer> ids)` — `MAPPER.writeValueAsString(ids)` (compact `[627,628]`, no spaces); wrap `JsonProcessingException` as `ConnectorFetchException`.
- `void writeRaw(String json, String identity, IngestionSink sink)` — `sink.accept(new FetchedPayload(FetchMethod.API, "application/json", json.getBytes(UTF_8), UTF_8.name(), identity))`.

- [ ] **Step 6: Run the test, confirm it passes.**

Run: `./gradlew test --tests com.goldys.platform.connectors.ctb.CtbDetailBackfillConnectorTest`
Expected: PASS.

- [ ] **Step 7: Spotless + commit.**

```bash
./gradlew spotlessApply
git add backend/src/main/java/com/goldys/platform/connectors/ctb/CtbClient.java \
        backend/src/main/java/com/goldys/platform/connectors/ctb/CtbDetailBackfillConnector.java \
        backend/src/test/java/com/goldys/platform/connectors/ctb/CtbDetailBackfillConnectorTest.java
git commit -m "feat(ctb): add ctb-detail-backfill connector for line-detail backfill"
```

---

### Task 2: The startup runner (+ `.env.example` note) with unit test

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/ingestion/CtbDetailBackfillRunner.java`
- Modify: `.env.example`
- Test: `backend/src/test/java/com/goldys/platform/ingestion/CtbDetailBackfillRunnerTest.java`

**Interfaces:**
- Consumes: `ConnectorRunner` (package-private, `UUID run(SourceConnector, String)`), `CtbClient`, `CtbDetailBackfillConnector` (Task 1).
- Produces: `CtbDetailBackfillRunner` (public ctor for Spring; package-private ctor `(ConnectorRunner, Supplier<CtbDetailBackfillConnector>)` for tests).

- [ ] **Step 1: Write the failing runner test.**

Create `CtbDetailBackfillRunnerTest.java` in `com.goldys.platform.ingestion`:

```java
class CtbDetailBackfillRunnerTest {
  @Test
  void runStartsTheConstructedConnector() {
    ConnectorRunner connectorRunner = mock(ConnectorRunner.class);
    CtbDetailBackfillConnector connector = mock(CtbDetailBackfillConnector.class);
    CtbDetailBackfillRunner runner = new CtbDetailBackfillRunner(connectorRunner, () -> connector);

    runner.run(null);

    verify(connectorRunner).run(connector, null);
  }
}
```

- [ ] **Step 2: Run it, confirm it fails (class absent).**

Run: `./gradlew test --tests com.goldys.platform.ingestion.CtbDetailBackfillRunnerTest`
Expected: FAIL (cannot resolve `CtbDetailBackfillRunner`).

- [ ] **Step 3: Implement `CtbDetailBackfillRunner`.**

`package com.goldys.platform.ingestion;` — `@Component @Order(Ordered.HIGHEST_PRECEDENCE) @ConditionalOnProperty(name = "app.ctb-detail-backfill", havingValue = "true") implements ApplicationRunner`.

Public constructor (Spring): `(ConnectorRunner connectorRunner, @Value("${ctb.base-url:https://web.cookingthebooks.com.au}") String baseUrl, @Value("${ctb.email:}") String email, @Value("${ctb.password:}") String password)` → delegates to the package-private constructor with the factory `() -> new CtbDetailBackfillConnector(new CtbClient(baseUrl), email, password)`.

Package-private constructor: `(ConnectorRunner connectorRunner, Supplier<CtbDetailBackfillConnector> connectorFactory)`.

`run(ApplicationArguments args)`: log an info line, then `connectorRunner.run(connectorFactory.get(), null)` (async, like `CtbScheduledPull`).

Note in the javadoc: it lives in `ingestion` (not `connectors.ctb`) so it can call the package-private `ConnectorRunner`; the connector is constructed here rather than registered as a bean because `IngestionService` keys its connector map by `sourceSystem` and a second `CTB` bean would collide.

- [ ] **Step 4: Run the test, confirm it passes.**

Run: `./gradlew test --tests com.goldys.platform.ingestion.CtbDetailBackfillRunnerTest`
Expected: PASS.

- [ ] **Step 5: Document the flag in `.env.example`.**

In the `# ---- Cooking the Books (CTB) ----` section, after the `APP_CTB_INVOICE_REENRICH` block, add:

```text
# One-off detail backfill: on the next boot, fetch CTB stocktake lines, wastage lines, recipe
# ingredients and a batched recipe-cost pass into the raw ledger (connector ctb-detail-backfill).
# Set true, deploy, verify the run closes SUCCESS on read-only data, then UNSET so it does not
# re-run on every restart (re-runs append duplicate raw records).
# APP_CTB_DETAIL_BACKFILL=true
```

- [ ] **Step 6: Spotless + commit.**

```bash
./gradlew spotlessApply
git add backend/src/main/java/com/goldys/platform/ingestion/CtbDetailBackfillRunner.java \
        backend/src/test/java/com/goldys/platform/ingestion/CtbDetailBackfillRunnerTest.java \
        .env.example
git commit -m "feat(ctb): add gated ctb-detail-backfill startup runner"
```

---

### Task 3: Testcontainers integration test

**Files:**
- Test: `backend/src/test/java/com/goldys/platform/ingestion/CtbDetailBackfillRunnerIntegrationTest.java`

**Interfaces:**
- Consumes: `ConnectorRunner` (`UUID run(SourceConnector, String)`), `IngestionRunRepository`, `RawRecordRepository` (package-private, accessible from `com.goldys.platform.ingestion`), `CtbDetailBackfillConnector` (Task 1), `PostgresContainerConfiguration`.
- Produces: (none — a test.)

- [ ] **Step 1: Write the failing integration test.**

Create `CtbDetailBackfillRunnerIntegrationTest.java` in `com.goldys.platform.ingestion`, mirroring `ConnectorRunnerTest` (`@SpringBootTest` + `@Import(PostgresContainerConfiguration.class)`, autowire `ConnectorRunner runner`, `IngestionRunRepository runs`, `RawRecordRepository rawRecords`):

```java
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CtbDetailBackfillRunnerIntegrationTest {
  @Autowired ConnectorRunner runner;
  @Autowired IngestionRunRepository runs;
  @Autowired RawRecordRepository rawRecords;

  @Test
  void backfillRunPersistsOneRunAndTheFourDetailIdentities() {
    CtbClient client = mock(CtbClient.class);
    when(client.searchStocktakes(anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"stocktakeId\":38}],\"totalCount\":1}", 1));
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"wastageRecordId\":60}],\"totalCount\":1}", 1));
    when(client.getAllRecipes())
        .thenReturn(new CtbClient.CtbPage("{\"data\":[{\"recipeId\":627},{\"recipeId\":628}],\"totalCount\":2}", 2));
    when(client.getStocktakeDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getWastageRecordDetail(anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getIngredientsIncludingSubRecipeIngredients(anyInt()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getSelectedRecipeFullRecords(anyString()))
        .thenReturn(new CtbClient.CtbPage("{\"data\":[{}],\"totalCount\":2}", 2));

    CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
    UUID runId = runner.run(connector, null);

    assertThat(awaitTerminal(runId)).isEqualTo(IngestionStatus.SUCCESS);
    IngestionRun run = runs.findById(runId).orElseThrow();
    assertThat(run.sourceSystem()).isEqualTo("CTB");
    assertThat(run.connectorName()).isEqualTo("ctb-detail-backfill");
    assertThat(rawRecords.findByIngestionRunId(runId))
        .extracting(RawRecord::fetcherIdentity)
        .containsExactlyInAnyOrder(
            "ctb-stocktake-detail", "ctb-wastage-detail",
            "ctb-recipe-ingredients", "ctb-recipe-ingredients", "ctb-recipe-cost");
    assertThat(rawRecords.findByIngestionRunId(runId))
        .allSatisfy(r -> {
          assertThat(r.sourceSystem()).isEqualTo("CTB");
          assertThat(r.fetchMethod()).isEqualTo(FetchMethod.API);
          assertThat(r.contentType()).isEqualTo("application/json");
        });
  }

  private IngestionStatus awaitTerminal(UUID runId) { /* copy of ConnectorRunnerTest.awaitTerminal */ }
}
```

The `awaitTerminal` helper is copied from `ConnectorRunnerTest` (polls `runs.findById(runId)` every 25 ms until `status() != RUNNING`, 10 s deadline).

- [ ] **Step 2: Run it, confirm it fails (compilation).**

Run: `./gradlew test --tests com.goldys.platform.ingestion.CtbDetailBackfillRunnerIntegrationTest`
Expected: FAIL to compile (missing `CtbDetailBackfillConnector` if Task 1 not yet merged; otherwise it compiles and should PASS on first run — the "failing first" check is the compile gate).

- [ ] **Step 3: Run it, confirm it passes.**

Run: `./gradlew test --tests com.goldys.platform.ingestion.CtbDetailBackfillRunnerIntegrationTest`
Expected: PASS (requires Docker for Testcontainers).

- [ ] **Step 4: Commit.**

```bash
git add backend/src/test/java/com/goldys/platform/ingestion/CtbDetailBackfillRunnerIntegrationTest.java
git commit -m "test(ctb): assert ctb-detail-backfill run lands four raw identities"
```

---

### Task 4: Documentation

**Files:**
- Modify: `docs/connectors/source-access.md`
- Modify: `docs/connectors/matching-and-identity.md`

- [ ] **Step 1: Add the four detail endpoints to `docs/connectors/source-access.md`.**

Under the existing "Endpoints to add next (inventory / purchasing / food cost)" table (after the `Wastage` row), add a short subsection "Line detail (raw-only backfill — `ctb-detail-backfill`)" with a table:

| Domain | Endpoint | Params | Returns |
|---|---|---|---|
| Stocktake lines | `Stocktake/GetStocktakeDetailByStocktakeId` | `stocktakeId`,`start`,`limit` | counted lines (`qtyCount`/`qtyCount1-4`, custom UOM, `stocktakeDetailCost`, `subTotal`, `stocktakeAreaName`, `stockCode`/`stockDesc`, `recipeId`, `supplierName`) — paged, one raw record per page |
| Wastage lines | `WastageRecord/GetWastageRecordDetailByWastageRecordId` | `wastageRecordId` | per-item line (`quantity`,`cost`,`reason`,`recipeId`,`unitSize`,…) — one request per id |
| Recipe ingredients | `RecipeBook/GetIngredientsIncludingSubRecipeIngredients` | `recipeId` | ingredient line (`costPerQty`,`costPerUnit`,`portionWeight`,`isSubRecipeIngredient`,`isBrokenLink`,…) — one request per id |
| Recipe cost (batched) | `RecipeBook/GetSelectedRecipeFullRecords` | `recipeIds` (JSON array), `recipeFolderIds=[]` | CTB derived cost per recipe (`costPerPortion`,`costPerPortionIncludingLabour`,`totalCost`,`portionSize`) — one batched call |

Add a note that these are ingested raw-only by the one-off `ctb-detail-backfill` connector (gated on `app.ctb-detail-backfill`), and that `GetSelectedRecipeFullRecords` requires `recipeFolderIds=[]` (otherwise a server-error envelope). Note `Statement/GetStatementDetailByStatementId` returns a non-JSON XHTML page and is not ingested.

- [ ] **Step 2: Update `docs/connectors/matching-and-identity.md`.**

- In the `## Stock count (canonical_stock_count)` section, change the source line to: `CTB` (stocktake detail — **ingested raw** via `ctb-stocktake-detail`; canonicalization and identity still deferred).
- In the `## Wastage (canonical_wastage)` section, change the source line to: `CTB` (wastage detail — **ingested raw** via `ctb-wastage-detail`; canonicalization and identity still deferred).
- Add a short `## Recipe composition` subsection: raw-only, no canonical entity yet (a future slice-D domain); source is `ctb-recipe-ingredients` + `ctb-recipe-cost` under the one-off `ctb-detail-backfill` run.

- [ ] **Step 3: Commit.**

```bash
git add docs/connectors/source-access.md docs/connectors/matching-and-identity.md
git commit -m "docs(ctb): record CTB line-detail endpoints and raw-ingested status"
```

---

## Final verification

Run from `backend/`:

```bash
./gradlew test spotlessCheck
```

Expected: full suite green (including `ArchitectureBoundariesTest`) and `spotlessCheck` clean.

The live backfill itself is an operator step, not part of the test suite: set `APP_CTB_DETAIL_BACKFILL=true`, deploy, confirm the raw ledger gains the four identities and the `ctb-detail-backfill` run closes `SUCCESS`, then unset.
