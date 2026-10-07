import { fetchApi } from "./client";
import type {
  ActivityPoint,
  Api,
  ConnectorStatus,
  ConversationThreadSummary,
  ConversationThreadView,
  DashboardBootstrap,
  DashboardDocument,
  DashboardRevisionSummary,
  DashboardSharing,
  DashboardSummary,
  DashboardTemplate,
  ProductOverrideInput,
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
} from "./types";

/** Real backend calls. The demo fixtures stay behind the `demo` flag; these hit the live endpoints. */
export const liveApi: Api = {
  getDashboardBootstrap: () => fetchApi<DashboardBootstrap>("/api/dashboard/bootstrap"),
  getDashboardSummary: () => fetchApi<DashboardSummary>("/api/dashboard/summary"),
  getDashboardActivity: () => fetchApi<ActivityPoint[]>("/api/dashboard/activity"),
  listReconciliationExceptions: () =>
    fetchApi<ReconciliationException[]>("/api/reconciliation/exceptions"),
  getReconciliationRecord: (id: string) =>
    fetchApi<ReconciliationRecord>(`/api/reconciliation/records/${id}`),
  getProductRecord: (date: string, product: string) =>
    fetchApi<ReconciliationRecord>(`/api/reconciliation/products/${date}/${product}`),
  listConnectorStatuses: () => fetchApi<ConnectorStatus[]>("/api/connectors"),
  runConnector: (source: string) =>
    fetchApi<ConnectorStatus>(`/api/connectors/${source}/run`, { method: "POST" }),
  uploadOpenTableCsv: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return fetchApi<void>("/api/connectors/opentable/upload", { method: "POST", body: form });
  },
  listProductExceptions: () =>
    fetchApi<ReconciliationException[]>("/api/reconciliation/products/exceptions"),
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
    fetchApi<void>(`/api/reconciliation/rules/${id}`, { method: "DELETE" }),
  getRecomputeStatus: () => fetchApi<RecomputeStatus>("/api/reconciliation/recompute/status"),
  listRuleAudit: () => fetchApi<RuleAuditEntry[]>("/api/reconciliation/rules/audit"),
  listReconciliationAudit: () =>
    fetchApi<ReconciliationAuditEntry[]>("/api/reconciliation/audit"),
  listProducts: () => fetchApi<string[]>("/api/reconciliation/products"),
  listDailySales: () => fetchApi<import("./types").DailySales[]>("/api/sales/daily"),
  getLatestSales: () => fetchApi<import("./types").LatestSales>("/api/sales/latest"),
  getReservationSummary: (date: string) =>
    fetchApi<import("./types").ReservationSummary | undefined>(
      `/api/reservations/summary?date=${date}`,
    ),
  getTopSellers: () => fetchApi<import("./types").TopSeller[]>("/api/dashboard/top-sellers"),
  getSalesTrend: () =>
    fetchApi<import("./types").SalesTrendPoint[]>("/api/dashboard/sales-trend"),
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
    fetchApi<void>(`/api/dashboards/${id}`, { method: "DELETE" }),
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
    fetchApi<void>(`/api/conversational/threads/${id}`, { method: "DELETE" }),
};
