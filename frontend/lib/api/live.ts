import { fetchApi } from "./client";
import type {
  Api,
  ConnectorStatus,
  DashboardSummary,
  ReconciliationException,
  ReconciliationRecord,
  SaveOverrideInput,
} from "./types";

/** Real backend calls. These endpoints don't exist yet, so in live mode the screens
 *  show the existing honest empty/error states until the backend lands. */
export const liveApi: Api = {
  getDashboardSummary: () => fetchApi<DashboardSummary>("/api/dashboard/summary"),
  listReconciliationExceptions: () =>
    fetchApi<ReconciliationException[]>("/api/reconciliation/exceptions"),
  getReconciliationRecord: (id: string) =>
    fetchApi<ReconciliationRecord>(`/api/reconciliation/records/${id}`),
  listConnectorStatuses: () => fetchApi<ConnectorStatus[]>("/api/connectors"),
  saveOverride: (input: SaveOverrideInput) =>
    fetchApi(`/api/reconciliation/records/${input.recordId}/override`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
};
