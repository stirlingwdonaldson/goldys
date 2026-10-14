import { fetchApi } from "./client";
import { getProvenance } from "./provenance";
import {
  getInvoiceGraphSuppliers,
  getInvoiceGraphInvoices,
  getInvoiceGraphLines,
} from "./invoice-graph";
import { isApiError } from "./errors";
import type {
  ActivityPoint,
  Api,
  AssignmentRow,
  CategoryNode,
  ConnectorStatus,
  OpenTableFrame,
  OpenTableInput,
  OpenTableSessionHandlers,
  OpenTableSessionState,
  OpenTableSyncMode,
  ConversationThreadSummary,
  ConversationThreadView,
  CreateCategoryInput,
  CreateForecastInput,
  DashboardBootstrap,
  DashboardDocument,
  DashboardRevisionSummary,
  DashboardSharing,
  DashboardSummary,
  DashboardTemplate,
  DendrogramNode,
  ForecastComparison,
  ForecastMetric,
  ForecastRun,
  ProductOverrideInput,
  ProductPoint,
  RecomputeStatus,
  ReconciliationException,
  ReconciliationRecord,
  ReconciliationAuditEntry,
  RenderedWidget,
  ResolutionRule,
  RuleAuditEntry,
  SaveDashboardInput,
  SaveOverrideInput,
  SaveResolutionRuleInput,
  SavedDashboardSummary,
  UpdateCategoryInput,
} from "./types";

/** A `?from=…&to=…` suffix, or "" when both bounds are absent. */
function dateRangeQuery(from?: string, to?: string): string {
  const q = new URLSearchParams();
  if (from) q.set("from", from);
  if (to) q.set("to", to);
  const qs = q.toString();
  return qs ? `?${qs}` : "";
}

/** Real backend calls. The demo fixtures stay behind the `demo` flag; these hit the live endpoints. */
export const liveApi: Api = {
  getDashboardBootstrap: () => fetchApi<DashboardBootstrap>("/api/dashboard/bootstrap"),
  getDashboardSummary: () => fetchApi<DashboardSummary>("/api/dashboard/summary"),
  getDashboardActivity: (from?: string, to?: string) =>
    fetchApi<ActivityPoint[]>(`/api/dashboard/activity${dateRangeQuery(from, to)}`),
  listReconciliationExceptions: (page: number, size: number) =>
    fetchApi<import("./types").DataPage<ReconciliationException>>(
      `/api/reconciliation/exceptions?page=${page}&size=${size}`,
    ),
  getReconciliationRecord: (id: string) =>
    fetchApi<ReconciliationRecord>(`/api/reconciliation/records/${id}`),
  getProductRecord: (date: string, product: string) =>
    fetchApi<ReconciliationRecord>(`/api/reconciliation/products/${date}/${product}`),
  listConnectorStatuses: () => fetchApi<ConnectorStatus[]>("/api/connectors"),
  runConnector: (source: string) =>
    fetchApi<ConnectorStatus>(`/api/connectors/${source}/run`, { method: "POST" }),
  uploadConnectorCsv: (connectorName: string, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return fetchApi<void>(`/api/connectors/${connectorName}/upload`, { method: "POST", body: form }, { responseType: "void" });
  },
  getOpenTableSession: () =>
    fetchApi<OpenTableSessionState>("/api/connectors/opentable/session"),
  startOpenTableSession: (mode: OpenTableSyncMode) =>
    fetchApi<OpenTableSessionState>("/api/connectors/opentable/session", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode }),
    }),
  sendOpenTableInput: async (input: OpenTableInput) => {
    try {
      await fetchApi<void>(
        "/api/connectors/opentable/session/input",
        { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) },
        { responseType: "void" },
      );
    } catch (e) {
      // 409: sign-in finished while keystrokes were in flight. Expected, not an error.
      if (isApiError(e) && e.status === 409) return;
      throw e;
    }
  },
  endOpenTableSession: (signOut: boolean) =>
    fetchApi<void>(
      `/api/connectors/opentable/session${signOut ? "?logout=true" : ""}`,
      { method: "DELETE" },
      { responseType: "void" },
    ),
  watchOpenTableSession: (handlers: OpenTableSessionHandlers) => watchOpenTableStream(handlers),
  listProductExceptions: (page: number, size: number) =>
    fetchApi<import("./types").DataPage<ReconciliationException>>(
      `/api/reconciliation/products/exceptions?page=${page}&size=${size}`,
    ),
  saveOverride: (input: SaveOverrideInput) =>
    fetchApi(`/api/reconciliation/records/${input.recordId}/override`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  saveProductOverride: (input: ProductOverrideInput) =>
    fetchApi(`/api/reconciliation/products/${input.date}/${input.product}/override`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ source: input.source, reason: input.reason }),
    }),
  listResolutionRules: () => fetchApi<ResolutionRule[]>("/api/reconciliation/rules"),
  saveResolutionRule: (input: SaveResolutionRuleInput) =>
    fetchApi<ResolutionRule>("/api/reconciliation/rules", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  deleteResolutionRule: (id: string) =>
    fetchApi<void>(`/api/reconciliation/rules/${id}`, { method: "DELETE" }, { responseType: "void" }),
  getRecomputeStatus: () => fetchApi<RecomputeStatus>("/api/reconciliation/recompute/status"),
  listRuleAudit: () => fetchApi<RuleAuditEntry[]>("/api/reconciliation/rules/audit"),
  listReconciliationAudit: (page: number, size: number) =>
    fetchApi<import("./types").DataPage<ReconciliationAuditEntry>>(
      `/api/reconciliation/audit?page=${page}&size=${size}`,
    ),
  listProducts: () => fetchApi<string[]>("/api/reconciliation/products"),
  listDailySales: (filter: import("./types").SalesFilter, page: number, size: number) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    return fetchApi<import("./types").DataPage<import("./types").DailySales>>(
      `/api/sales/daily?${q.toString()}`,
    );
  },
  getLatestSales: () => fetchApi<import("./types").LatestSales>("/api/sales/latest"),
  getReservationSummary: (date: string) =>
    fetchApi<import("./types").ReservationSummary>(
      `/api/reservations/summary?date=${date}`,
      undefined, { allowNoContent: true },
    ),
  getInventoryLines: (from: string, to: string) =>
    fetchApi<import("./types").InventoryLineBreakdown>(
      `/api/inventory/lines?from=${from}&to=${to}`,
    ),
  getInventorySummary: (from: string, to: string) =>
    fetchApi<import("./types").InventorySummary>(`/api/inventory/summary?from=${from}&to=${to}`),
  getLabourSummary: (from: string, to: string) =>
    fetchApi<import("./types").LabourSummary>(`/api/labour/summary?from=${from}&to=${to}`),
  listDailyCovers: (from: string, to: string) =>
    fetchApi<import("./types").DailyCovers[]>(`/api/reservations/covers?from=${from}&to=${to}`),
  getProvenance,
  getInvoiceGraphSuppliers,
  getInvoiceGraphInvoices,
  getInvoiceGraphLines,
  listInvoices: (filter: import("./types").InvoiceFilter, page: number, size: number) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.supplier) q.set("supplier", filter.supplier);
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    return fetchApi<import("./types").DataPage<import("./types").InvoiceSummary>>(
      `/api/invoices?${q.toString()}`,
    );
  },
  getInvoice: (invoiceNumber: string) =>
    fetchApi<import("./types").InvoiceDetail>(`/api/invoices/${encodeURIComponent(invoiceNumber)}`),
  listInvoiceFlags: () => fetchApi<import("./types").InvoiceIngestFlag[]>("/api/inventory/flags"),
  getPlatformMap: () => fetchApi<import("./types").NetworkGraphDto>("/api/platform-map"),
  getTopSellers: (from?: string, to?: string, limit?: number) => {
    const q = new URLSearchParams();
    if (from) q.set("from", from);
    if (to) q.set("to", to);
    if (limit != null) q.set("limit", String(limit));
    const qs = q.toString();
    return fetchApi<import("./types").TopSeller[]>(
      `/api/dashboard/top-sellers${qs ? `?${qs}` : ""}`,
    );
  },
  getSalesTrend: (from?: string, to?: string) =>
    fetchApi<import("./types").SalesTrendPoint[]>(
      `/api/dashboard/sales-trend${dateRangeQuery(from, to)}`,
    ),
  queryMetrics: (query: import("./types").MetricQuery) =>
    fetchApi<import("./types").MetricResult>("/api/metrics/query", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(query),
    }),
  listDashboards: () => fetchApi<SavedDashboardSummary[]>("/api/dashboards"),
  getDashboard: (id: string) => fetchApi<DashboardDocument>(`/api/dashboards/${id}`),
  saveDashboard: (input: SaveDashboardInput) =>
    fetchApi<DashboardDocument>("/api/dashboards", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  updateDashboard: (id: string, input: SaveDashboardInput) =>
    fetchApi<DashboardDocument>(`/api/dashboards/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  deleteDashboard: (id: string) =>
    fetchApi<void>(`/api/dashboards/${id}`, { method: "DELETE" }, { responseType: "void" }),
  renderDashboard: (id: string) => fetchApi<RenderedWidget[]>(`/api/dashboards/${id}/render`),
  listDashboardTemplates: () => fetchApi<DashboardTemplate[]>("/api/dashboards/templates"),
  createDashboardFromTemplate: (templateId: string) =>
    fetchApi<DashboardDocument>(`/api/dashboards/from-template/${templateId}`, { method: "POST" }),
  listDashboardRevisions: (id: string) =>
    fetchApi<DashboardRevisionSummary[]>(`/api/dashboards/${id}/revisions`),
  restoreDashboardRevision: (id: string, revision: number) =>
    fetchApi<DashboardDocument>(`/api/dashboards/${id}/revisions/${revision}/restore`, {
      method: "POST",
    }),
  toggleDashboardPin: (id: string) =>
    fetchApi<DashboardDocument>(`/api/dashboards/${id}/pin`, { method: "PUT" }),
  getDashboardSharing: (id: string) =>
    fetchApi<DashboardSharing>(`/api/dashboards/${id}/sharing`),
  setDashboardSharing: (id: string, sharing: DashboardSharing) =>
    fetchApi<DashboardSharing>(`/api/dashboards/${id}/sharing`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(sharing),
    }),
  listThreads: () => fetchApi<ConversationThreadSummary[]>("/api/conversational/threads"),
  getThread: (id: string) => fetchApi<ConversationThreadView>(`/api/conversational/threads/${id}`),
  renameThread: (id: string, title: string) =>
    fetchApi<ConversationThreadView>(`/api/conversational/threads/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title }),
    }),
  deleteThread: (id: string) =>
    fetchApi<void>(`/api/conversational/threads/${id}`, { method: "DELETE" }, { responseType: "void" }),
  listRawRecords: (filter: import("./types").RawRecordFilter, page: number, size: number) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.source) q.set("source", filter.source);
    if (filter.fetcher) q.set("fetcher", filter.fetcher);
    if (filter.method) q.set("method", filter.method);
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    return fetchApi<import("./types").DataPage<import("./types").RawRecordSummary>>(
      `/api/data/raw?${q.toString()}`,
    );
  },
  getRawRecord: (id: string) =>
    fetchApi<import("./types").RawRecordDetail>(`/api/data/raw/${id}`),
  listCanonicalEntities: () => fetchApi<import("./types").EntityDescriptor[]>("/api/data/canonical"),
  listCanonicalRows: (entity: string, page: number, size: number) =>
    fetchApi<import("./types").DataPage<import("./types").GenericRow>>(
      `/api/data/canonical/${entity}?page=${page}&size=${size}`,
    ),
  listResolvedDomains: () => fetchApi<import("./types").EntityDescriptor[]>("/api/data/resolved"),
  listResolvedRows: (domain: string, page: number, size: number) =>
    fetchApi<import("./types").DataPage<import("./types").GenericRow>>(
      `/api/data/resolved/${domain}?page=${page}&size=${size}`,
    ),
  listPayments: (filter: import("./types").PaymentFilter, page: number, size: number) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    if (filter.paymentType) q.set("paymentType", filter.paymentType);
    if (filter.saleNumber) q.set("saleNumber", filter.saleNumber);
    return fetchApi<import("./types").DataPage<import("./types").PaymentRow>>(
      `/api/payments?${q.toString()}`,
    );
  },
  listDeletedSales: (filter: import("./types").DeletedSaleFilter, page: number, size: number) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    if (filter.saleNumber) q.set("saleNumber", filter.saleNumber);
    return fetchApi<import("./types").DataPage<import("./types").DeletedSaleRow>>(
      `/api/deleted-sales?${q.toString()}`,
    );
  },
  listSaleItems: (filter: import("./types").SaleItemFilter, page: number, size: number) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    if (filter.category) q.set("category", filter.category);
    if (filter.saleNumber) q.set("saleNumber", filter.saleNumber);
    return fetchApi<import("./types").DataPage<import("./types").SaleItemRow>>(
      `/api/sale-items?${q.toString()}`,
    );
  },
  getPaymentMix: (from: string, to: string) =>
    fetchApi<import("./types").PaymentMix[]>(`/api/payments/mix?from=${from}&to=${to}`),
  getDeletedSaleTotals: (from: string, to: string) =>
    fetchApi<import("./types").DeletedSaleDay[]>(`/api/deleted-sales/totals?from=${from}&to=${to}`),
  getSaleItemMix: (from: string, to: string) =>
    fetchApi<import("./types").SaleItemMix[]>(`/api/sale-items/mix?from=${from}&to=${to}`),
  listForecastMetrics: () => fetchApi<ForecastMetric[]>("/api/forecasting/metrics"),
  createForecastRun: (input: CreateForecastInput) =>
    fetchApi<ForecastRun>("/api/forecasting/runs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  listForecastRuns: () => fetchApi<ForecastRun[]>("/api/forecasting/runs"),
  getForecastRun: (id: string) => fetchApi<ForecastRun>(`/api/forecasting/runs/${id}`),
  getForecastComparison: (id: string) =>
    fetchApi<ForecastComparison>(`/api/forecasting/runs/${id}/comparison`),
  getRecipes: () => fetchApi<import("./types").RecipeCosting[]>("/api/recipes"),
  getRecipeIngredients: (recipeId: number) =>
    fetchApi<import("./types").RecipeIngredient[]>(`/api/recipes/${recipeId}/ingredients`),
  getCategoryTaxonomy: () => fetchApi<CategoryNode[]>("/api/products/categories/taxonomy"),
  getCategoryDendrogram: () =>
    fetchApi<DendrogramNode[]>("/api/products/categories/dendrogram"),
  getCategoryEmbeddingSpace: () =>
    fetchApi<ProductPoint[]>("/api/products/categories/embedding-space"),
  getCategoryAssignments: (status?: string) => {
    // Omit the query param entirely for "all": the backend rejects `?status=` (400).
    const q = status ? `?status=${encodeURIComponent(status)}` : "";
    return fetchApi<AssignmentRow[]>(`/api/products/categories/assignments${q}`);
  },
  createCategory: (input: CreateCategoryInput) =>
    fetchApi<CategoryNode>("/api/products/categories/categories", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  renameCategory: (id: string, input: UpdateCategoryInput) =>
    fetchApi<void>(
      `/api/products/categories/categories/${id}`,
      { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) },
      { responseType: "void" },
    ),
  moveProduct: (productNameKey: string, categoryId: string) =>
    fetchApi<void>(
      `/api/products/categories/assignments/${encodeURIComponent(productNameKey)}/move`,
      { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ categoryId }) },
      { responseType: "void" },
    ),
  clearProductOverride: (productNameKey: string) =>
    fetchApi<void>(
      `/api/products/categories/assignments/${encodeURIComponent(productNameKey)}/override`,
      { method: "DELETE" },
      { responseType: "void" },
    ),
  runClassification: (kind: import("./types").ClassificationRunKind) =>
    fetchApi<void>(
      "/api/products/categories/runs",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ kind }),
      },
      { responseType: "void" },
    ),
  listClassificationRuns: () =>
    fetchApi<import("./types").ClassificationRun[]>("/api/products/categories/runs"),
  getCategorySuggestions: () =>
    fetchApi<import("./types").CategorySuggestion[]>("/api/products/categories/suggestions"),
  addCategorySuggestion: (input: { name: string; groupName?: string | null }) =>
    fetchApi<import("./types").CategorySuggestion>("/api/products/categories/suggestions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  updateCategorySuggestion: (
    id: string,
    input: { name?: string | null; groupName?: string | null },
  ) =>
    fetchApi<void>(
      `/api/products/categories/suggestions/${id}`,
      { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) },
      { responseType: "void" },
    ),
  deleteCategorySuggestion: (id: string) =>
    fetchApi<void>(
      `/api/products/categories/suggestions/${id}`,
      { method: "DELETE" },
      { responseType: "void" },
    ),
  applyCategorySuggestions: () =>
    fetchApi<import("./types").CategoryNode[]>("/api/products/categories/suggestions/apply", {
      method: "POST",
    }),
};

/**
 * Live-view stream over Server-Sent Events (same origin, so the session cookie rides along).
 * EventSource would silently reconnect after the server ends the stream (another viewer took
 * over, or the session ended), re-attaching a viewer nobody is watching; so any error closes it
 * for good and reports `onClosed`. The panel reopens it explicitly when needed.
 */
function watchOpenTableStream(handlers: OpenTableSessionHandlers): () => void {
  const source = new EventSource("/api/connectors/opentable/session/stream");
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    source.close();
    handlers.onClosed();
  };
  source.addEventListener("state", (e) => {
    handlers.onState(JSON.parse((e as MessageEvent<string>).data) as OpenTableSessionState);
  });
  source.addEventListener("frame", (e) => {
    handlers.onFrame(JSON.parse((e as MessageEvent<string>).data) as OpenTableFrame);
  });
  source.onerror = close;
  return () => {
    closed = true;
    source.close();
  };
}
