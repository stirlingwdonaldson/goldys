import { dashboardSchema, renderedWidgetsSchema, threadSchema } from "./dashboard-schema";
import { fetchApi } from "./client";
import { getProvenance } from "./provenance";
import {
  getInvoiceGraphSuppliers,
  getInvoiceGraphInvoices,
  getInvoiceGraphLines,
  getInvoiceGraphInvoicesById,
  getInvoiceGraphLinesById,
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
  AspectSentimentSummary,
  RatingBucket,
  RatingTrendBucket,
  ReviewFilter,
  ReviewOverview,
  ReviewView,
  SentimentBucket,
  SourceSummary,
  QueryDataset,
  QueryField,
  QueryPlan,
  QueryRelationship,
  QueryResult,
  SavedQueryDetail,
  SavedQueryRevision,
  SavedQuerySummary,
  SaveQueryInput,
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
  getDashboardBootstrap: (options?: { signal?: AbortSignal }) => fetchApi<DashboardBootstrap>("/api/dashboard/bootstrap", { signal: options?.signal }),
  getDashboardSummary: (options?: { signal?: AbortSignal }) => fetchApi<DashboardSummary>("/api/dashboard/summary", { signal: options?.signal }),
  getDashboardActivity: (from?: string, to?: string, options?: { signal?: AbortSignal }) =>
    fetchApi<ActivityPoint[]>(`/api/dashboard/activity${dateRangeQuery(from, to)}`, { signal: options?.signal }),
  listReconciliationExceptions: (page: number, size: number, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").DataPage<ReconciliationException>>(
      `/api/reconciliation/exceptions?page=${page}&size=${size}`, { signal: options?.signal }
    ),
  getReconciliationRecord: (id: string, options?: { signal?: AbortSignal }) =>
    fetchApi<ReconciliationRecord>(`/api/reconciliation/records/${id}`, { signal: options?.signal }),
  getProductRecord: (date: string, product: string, options?: { signal?: AbortSignal }) =>
    fetchApi<ReconciliationRecord>(`/api/reconciliation/products/${date}/${product}`, { signal: options?.signal }),
  listConnectorStatuses: (options?: { signal?: AbortSignal }) => fetchApi<ConnectorStatus[]>("/api/connectors", { signal: options?.signal }),
  runConnector: (source: string) =>
    fetchApi<ConnectorStatus>(`/api/connectors/${source}/run`, { method: "POST" }),
  uploadConnectorCsv: (connectorName: string, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return fetchApi<void>(`/api/connectors/${connectorName}/upload`, { method: "POST", body: form }, { responseType: "void" });
  },
  getOpenTableSession: (options?: { signal?: AbortSignal }) =>
    fetchApi<OpenTableSessionState>("/api/connectors/opentable/session", { signal: options?.signal }),
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
  listProductExceptions: (page: number, size: number, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").DataPage<ReconciliationException>>(
      `/api/reconciliation/products/exceptions?page=${page}&size=${size}`, { signal: options?.signal }
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
  listResolutionRules: (options?: { signal?: AbortSignal }) => fetchApi<ResolutionRule[]>("/api/reconciliation/rules", { signal: options?.signal }),
  saveResolutionRule: (input: SaveResolutionRuleInput) =>
    fetchApi<ResolutionRule>("/api/reconciliation/rules", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  deleteResolutionRule: (id: string) =>
    fetchApi<void>(`/api/reconciliation/rules/${id}`, { method: "DELETE" }, { responseType: "void" }),
  getRecomputeStatus: (options?: { signal?: AbortSignal }) => fetchApi<RecomputeStatus>("/api/reconciliation/recompute/status", { signal: options?.signal }),
  listRuleAudit: (options?: { signal?: AbortSignal }) => fetchApi<RuleAuditEntry[]>("/api/reconciliation/rules/audit", { signal: options?.signal }),
  listReconciliationAudit: (page: number, size: number, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").DataPage<ReconciliationAuditEntry>>(
      `/api/reconciliation/audit?page=${page}&size=${size}`, { signal: options?.signal }
    ),
  listProducts: (options?: { signal?: AbortSignal }) => fetchApi<string[]>("/api/reconciliation/products", { signal: options?.signal }),
  listDailySales: (filter: import("./types").SalesFilter, page: number, size: number, options?: { signal?: AbortSignal }) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    return fetchApi<import("./types").DataPage<import("./types").DailySales>>(
      `/api/sales/daily?${q.toString()}`, { signal: options?.signal }
    );
  },
  getLatestSales: (options?: { signal?: AbortSignal }) => fetchApi<import("./types").LatestSales>("/api/sales/latest", { signal: options?.signal }),
  getReservationSummary: (date: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").ReservationSummary>(
      `/api/reservations/summary?date=${date}`,
      { signal: options?.signal }, { allowNoContent: true },
    ),
  getInventoryObservations: (from, to, kind, page, size, options?: { signal?: AbortSignal }) => {
    const params = new URLSearchParams({ from, to, kind, page: String(page), size: String(size) });
    return fetchApi<import("./types").InventoryObservationPage>(`/api/inventory/observations?${params}`, { signal: options?.signal });
  },
  getInventoryLines: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").InventoryLineBreakdown>(
      `/api/inventory/lines?from=${from}&to=${to}`, { signal: options?.signal }
    ),
  getInventorySummary: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").InventorySummary>(`/api/inventory/summary?from=${from}&to=${to}`, { signal: options?.signal }),
  getLabourSummary: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").LabourSummary>(`/api/labour/summary?from=${from}&to=${to}`, { signal: options?.signal }),
  listDailyCovers: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").DailyCovers[]>(`/api/reservations/covers?from=${from}&to=${to}`, { signal: options?.signal }),
  getProvenance,
  getInvoiceGraphSuppliers,
  getInvoiceGraphInvoices,
  getInvoiceGraphLines,
  getInvoiceGraphInvoicesById,
  getInvoiceGraphLinesById,
  listInvoices: (filter: import("./types").InvoiceFilter, page: number, size: number, options?: { signal?: AbortSignal }) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.supplier) q.set("supplier", filter.supplier);
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    return fetchApi<import("./types").DataPage<import("./types").InvoiceSummary>>(
      `/api/invoices?${q.toString()}`, { signal: options?.signal }
    );
  },
  getInvoiceById: (id: string, options?: { signal?: AbortSignal }) => fetchApi<import("./types").InvoiceDetail>(`/api/invoices/by-id/${encodeURIComponent(id)}`, { signal: options?.signal }),
  getInvoiceDocuments: (id: string, options?: { signal?: AbortSignal }) => fetchApi<import("./types").InvoiceDocument[]>(`/api/invoices/by-id/${encodeURIComponent(id)}/documents`, { signal: options?.signal }),
  getInvoice: (invoiceNumber: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").InvoiceDetail>(`/api/invoices/${encodeURIComponent(invoiceNumber)}`, { signal: options?.signal }),
  listInvoiceFlags: (options?: { signal?: AbortSignal }) => fetchApi<import("./types").InvoiceIngestFlag[]>("/api/inventory/flags", { signal: options?.signal }),
  getPlatformMap: (options?: { signal?: AbortSignal }) => fetchApi<import("./types").NetworkGraphDto>("/api/platform-map", { signal: options?.signal }),
  getTopSellers: (from?: string, to?: string, limit?: number, options?: { signal?: AbortSignal }) => {
    const q = new URLSearchParams();
    if (from) q.set("from", from);
    if (to) q.set("to", to);
    if (limit != null) q.set("limit", String(limit));
    const qs = q.toString();
    return fetchApi<import("./types").TopSellersResult>(
      `/api/dashboard/top-sellers${qs ? `?${qs}` : ""}`, { signal: options?.signal }
    );
  },
  getSalesTrend: (from?: string, to?: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").SalesTrendResult>(
      `/api/dashboard/sales-trend${dateRangeQuery(from, to)}`, { signal: options?.signal }
    ),
  queryMetrics: (query: import("./types").MetricQuery, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").MetricResult>("/api/metrics/query", { signal: options?.signal,
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(query),
    }),
  listDashboards: (options?: { signal?: AbortSignal }) => fetchApi<SavedDashboardSummary[]>("/api/dashboards", { signal: options?.signal }),
  getDashboard: (id: string, options?: { signal?: AbortSignal }) => fetchApi<DashboardDocument>(`/api/dashboards/${id}`, { signal: options?.signal }, { schema: dashboardSchema }),
  saveDashboard: (input: SaveDashboardInput) =>
    fetchApi<DashboardDocument>("/api/dashboards", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }, { schema: dashboardSchema }),
  updateDashboard: (id: string, input: SaveDashboardInput) =>
    fetchApi<DashboardDocument>(`/api/dashboards/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }, { schema: dashboardSchema }),
  deleteDashboard: (id: string) =>
    fetchApi<void>(`/api/dashboards/${id}`, { method: "DELETE" }, { responseType: "void" }),
  renderDashboard: (id: string, options?: { signal?: AbortSignal }) => fetchApi<RenderedWidget[]>(`/api/dashboards/${id}/render`, { signal: options?.signal }, { schema: renderedWidgetsSchema }),
  listDashboardTemplates: (options?: { signal?: AbortSignal }) => fetchApi<DashboardTemplate[]>("/api/dashboards/templates", { signal: options?.signal }),
  createDashboardFromTemplate: (templateId: string) =>
    fetchApi<DashboardDocument>(`/api/dashboards/from-template/${templateId}`, { method: "POST" }, { schema: dashboardSchema }),
  listDashboardRevisions: (id: string, options?: { signal?: AbortSignal }) =>
    fetchApi<DashboardRevisionSummary[]>(`/api/dashboards/${id}/revisions`, { signal: options?.signal }),
  restoreDashboardRevision: (id: string, revision: number) =>
    fetchApi<DashboardDocument>(`/api/dashboards/${id}/revisions/${revision}/restore`, {
      method: "POST",
    }, { schema: dashboardSchema }),
  toggleDashboardPin: (id: string) =>
    fetchApi<DashboardDocument>(`/api/dashboards/${id}/pin`, { method: "PUT" }, { schema: dashboardSchema }),
  getDashboardSharing: (id: string, options?: { signal?: AbortSignal }) =>
    fetchApi<DashboardSharing>(`/api/dashboards/${id}/sharing`, { signal: options?.signal }),
  setDashboardSharing: (id: string, sharing: DashboardSharing) =>
    fetchApi<DashboardSharing>(`/api/dashboards/${id}/sharing`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(sharing),
    }),
  listThreads: (options?: { signal?: AbortSignal }) => fetchApi<ConversationThreadSummary[]>("/api/conversational/threads", { signal: options?.signal }),
  getThread: (id: string, options?: { signal?: AbortSignal }) => fetchApi<ConversationThreadView>(`/api/conversational/threads/${id}`, { signal: options?.signal }, { schema: threadSchema }),
  renameThread: (id: string, title: string) =>
    fetchApi<ConversationThreadView>(`/api/conversational/threads/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title }),
    }),
  deleteThread: (id: string) =>
    fetchApi<void>(`/api/conversational/threads/${id}`, { method: "DELETE" }, { responseType: "void" }),
  listRawRecords: (filter: import("./types").RawRecordFilter, page: number, size: number, options?: { signal?: AbortSignal }) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.source) q.set("source", filter.source);
    if (filter.fetcher) q.set("fetcher", filter.fetcher);
    if (filter.method) q.set("method", filter.method);
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    return fetchApi<import("./types").DataPage<import("./types").RawRecordSummary>>(
      `/api/data/raw?${q.toString()}`, { signal: options?.signal }
    );
  },
  getRawRecord: (id: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").RawRecordDetail>(`/api/data/raw/${id}`, { signal: options?.signal }),
  listCanonicalEntities: (options?: { signal?: AbortSignal }) => fetchApi<import("./types").EntityDescriptor[]>("/api/data/canonical", { signal: options?.signal }),
  listCanonicalRows: (entity: string, page: number, size: number, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").DataPage<import("./types").GenericRow>>(
      `/api/data/canonical/${entity}?page=${page}&size=${size}`, { signal: options?.signal }
    ),
  listResolvedDomains: (options?: { signal?: AbortSignal }) => fetchApi<import("./types").EntityDescriptor[]>("/api/data/resolved", { signal: options?.signal }),
  listResolvedRows: (domain: string, page: number, size: number, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").DataPage<import("./types").GenericRow>>(
      `/api/data/resolved/${domain}?page=${page}&size=${size}`, { signal: options?.signal }
    ),
  listPayments: (filter: import("./types").PaymentFilter, page: number, size: number, options?: { signal?: AbortSignal }) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    if (filter.paymentType) q.set("paymentType", filter.paymentType);
    if (filter.saleNumber) q.set("saleNumber", filter.saleNumber);
    return fetchApi<import("./types").DataPage<import("./types").PaymentRow>>(
      `/api/payments?${q.toString()}`, { signal: options?.signal }
    );
  },
  listDeletedSales: (filter: import("./types").DeletedSaleFilter, page: number, size: number, options?: { signal?: AbortSignal }) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    if (filter.saleNumber) q.set("saleNumber", filter.saleNumber);
    return fetchApi<import("./types").DataPage<import("./types").DeletedSaleRow>>(
      `/api/deleted-sales?${q.toString()}`, { signal: options?.signal }
    );
  },
  listSaleItems: (filter: import("./types").SaleItemFilter, page: number, size: number, options?: { signal?: AbortSignal }) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    if (filter.category) q.set("category", filter.category);
    if (filter.saleNumber) q.set("saleNumber", filter.saleNumber);
    return fetchApi<import("./types").DataPage<import("./types").SaleItemRow>>(
      `/api/sale-items?${q.toString()}`, { signal: options?.signal }
    );
  },
  getPaymentMix: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").PaymentMix[]>(`/api/payments/mix?from=${from}&to=${to}`, { signal: options?.signal }),
  getDeletedSaleTotals: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").DeletedSaleDay[]>(`/api/deleted-sales/totals?from=${from}&to=${to}`, { signal: options?.signal }),
  getSaleItemMix: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").SaleItemMix[]>(`/api/sale-items/mix?from=${from}&to=${to}`, { signal: options?.signal }),
  listForecastMetrics: (options?: { signal?: AbortSignal }) => fetchApi<ForecastMetric[]>("/api/forecasting/metrics", { signal: options?.signal }),
  createForecastRun: (input: CreateForecastInput) =>
    fetchApi<ForecastRun>("/api/forecasting/runs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  getRecipeComposition: (recipeId) => fetchApi<import("./types").RecipeComposition | null>(`/api/recipes/${recipeId}/composition`),
  getRecipeCatalog: () => fetchApi<import("./types").RecipeCatalog | null>("/api/recipes/folders"),
  listForecastRuns: (options?: { signal?: AbortSignal }) => fetchApi<ForecastRun[]>("/api/forecasting/runs", { signal: options?.signal }),
  getForecastRun: (id: string, options?: { signal?: AbortSignal }) => fetchApi<ForecastRun>(`/api/forecasting/runs/${id}`, { signal: options?.signal }),
  getForecastComparison: (id: string, options?: { signal?: AbortSignal }) =>
    fetchApi<ForecastComparison>(`/api/forecasting/runs/${id}/comparison`, { signal: options?.signal }),
  getRecipes: (options?: { signal?: AbortSignal }) => fetchApi<import("./types").RecipeCosting[]>("/api/recipes", { signal: options?.signal }),
  getRecipeIngredients: (recipeId: number, options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").RecipeIngredient[]>(`/api/recipes/${recipeId}/ingredients`, { signal: options?.signal }),
  getCategoryTaxonomy: (options?: { signal?: AbortSignal }) => fetchApi<CategoryNode[]>("/api/products/categories/taxonomy", { signal: options?.signal }),
  getCategoryDendrogram: (options?: { signal?: AbortSignal }) =>
    fetchApi<DendrogramNode[]>("/api/products/categories/dendrogram", { signal: options?.signal }),
  getCategoryEmbeddingSpace: (options?: { signal?: AbortSignal }) =>
    fetchApi<ProductPoint[]>("/api/products/categories/embedding-space", { signal: options?.signal }),
  getCategoryAssignments: (status?: string, options?: { signal?: AbortSignal }) => {
    // Omit the query param entirely for "all": the backend rejects `?status=` (400).
    const q = status ? `?status=${encodeURIComponent(status)}` : "";
    return fetchApi<AssignmentRow[]>(`/api/products/categories/assignments${q}`, { signal: options?.signal });
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
  deleteCategory: (id: string) =>
    fetchApi<void>(
      `/api/products/categories/categories/${id}`,
      { method: "DELETE" },
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
  listClassificationRuns: (options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").ClassificationRun[]>("/api/products/categories/runs", { signal: options?.signal }),
  getReviewOverview: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<ReviewOverview>(`/api/reviews/overview?from=${from}&to=${to}`, { signal: options?.signal }),
  getReviewSentimentTrend: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<SentimentBucket[]>(`/api/reviews/sentiment-trend?from=${from}&to=${to}`, { signal: options?.signal }),
  getReviewRatingTrend: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<RatingTrendBucket[]>(`/api/reviews/rating-trend?from=${from}&to=${to}`, { signal: options?.signal }),
  getReviewAspects: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<AspectSentimentSummary[]>(`/api/reviews/aspects?from=${from}&to=${to}`, { signal: options?.signal }),
  getReviewRatings: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<RatingBucket[]>(`/api/reviews/ratings?from=${from}&to=${to}`, { signal: options?.signal }),
  getReviewSources: (from: string, to: string, options?: { signal?: AbortSignal }) =>
    fetchApi<SourceSummary[]>(`/api/reviews/sources?from=${from}&to=${to}`, { signal: options?.signal }),
  getReviews: (filter: ReviewFilter, options?: { signal?: AbortSignal }) => {
    // The list lives at the collection root; `query` maps to the endpoint's `q` param.
    const q = new URLSearchParams();
    if (filter.from) q.set("from", filter.from);
    if (filter.to) q.set("to", filter.to);
    if (filter.source) q.set("source", filter.source);
    if (filter.sentiment) q.set("sentiment", filter.sentiment);
    if (filter.aspect) q.set("aspect", filter.aspect);
    if (filter.minRating != null) q.set("minRating", String(filter.minRating));
    if (filter.query) q.set("q", filter.query);
    const qs = q.toString();
    return fetchApi<ReviewView[]>(`/api/reviews${qs ? `?${qs}` : ""}`, { signal: options?.signal });
  },

  getCategorySuggestions: (options?: { signal?: AbortSignal }) =>
    fetchApi<import("./types").CategorySuggestion[]>("/api/products/categories/suggestions", { signal: options?.signal }),
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
  listQueryDatasets: (options?: { signal?: AbortSignal }) => fetchApi<QueryDataset[]>("/api/query/datasets", { signal: options?.signal }),
  listQueryFields: (dataset: string, options?: { signal?: AbortSignal }) =>
    fetchApi<QueryField[]>(`/api/query/datasets/${dataset}/fields`, { signal: options?.signal }),
  listQueryRelationships: (options?: { signal?: AbortSignal }) => fetchApi<QueryRelationship[]>("/api/query/relationships", { signal: options?.signal }),
  runQuery: (plan: QueryPlan) =>
    fetchApi<QueryResult>("/api/query/plan", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(plan),
    }),
  listSavedQueries: (options?: { signal?: AbortSignal }) => fetchApi<SavedQuerySummary[]>("/api/query/saved", { signal: options?.signal }),
  getSavedQuery: (id: string, options?: { signal?: AbortSignal }) => fetchApi<SavedQueryDetail>(`/api/query/saved/${id}`, { signal: options?.signal }),
  createSavedQuery: (input: SaveQueryInput) =>
    fetchApi<SavedQueryDetail>("/api/query/saved", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  updateSavedQuery: (id: string, input: SaveQueryInput) =>
    fetchApi<SavedQueryDetail>(`/api/query/saved/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  deleteSavedQuery: (id: string) =>
    fetchApi<void>(`/api/query/saved/${id}`, { method: "DELETE" }, { responseType: "void" }),
  listSavedQueryRevisions: (id: string, options?: { signal?: AbortSignal }) =>
    fetchApi<SavedQueryRevision[]>(`/api/query/saved/${id}/revisions`, { signal: options?.signal }),
  restoreSavedQuery: (id: string, revision: number) =>
    fetchApi<SavedQueryDetail>(`/api/query/saved/${id}/restore?revision=${revision}`, {
      method: "POST",
    }),
  runSavedQuery: (id: string) =>
    fetchApi<QueryResult>(`/api/query/saved/${id}/run`, { method: "POST" }),
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
