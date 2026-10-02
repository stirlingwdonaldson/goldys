import { fetchApi } from "./client";
import type {
  ActivityPoint,
  Api,
  ConnectorStatus,
  DashboardSummary,
  ProductOverrideInput,
  RecomputeStatus,
  ReconciliationException,
  ReconciliationRecord,
  ResolutionRule,
  RuleAuditEntry,
  SaveOverrideInput,
  SaveResolutionRuleInput,
} from "./types";

/** Real backend calls. The demo fixtures stay behind the `demo` flag; these hit the live endpoints. */
export const liveApi: Api = {
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
  listProducts: () => fetchApi<string[]>("/api/reconciliation/products"),
};
