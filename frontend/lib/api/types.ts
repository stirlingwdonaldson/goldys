/** The current staff identity the backend resolves from the OIDC session. */
export interface CurrentUser {
  displayName: string;
  department: string;
  seniority: string;
}

/** `GET /api/health` response body. */
export interface HealthResponse {
  status: string;
}

/** The one envelope every API error uses (mirrors the backend's `ApiErrorResponse`). */
export interface ApiErrorResponse {
  code: string;
  message: string;
  correlationId?: string;
  fields?: Record<string, string>;
}

/** A source's value for a field. `value: null` means the source has no data. */
export interface SourceValue {
  source: string;
  value: string | null;
}

export type ExceptionStatus = "conflict" | "missing";

export interface ReconciliationException {
  id: string;
  recordId: string;
  entity: string;
  field: string;
  sources: SourceValue[];
  status: ExceptionStatus;
}

export interface ReconciliationField {
  name: string;
  label: string;
  sources: SourceValue[];
  overridden: boolean;
  authoritativeSource?: string;
}

export interface ReconciliationRecord {
  id: string;
  entity: string;
  entityType: string;
  fields: ReconciliationField[];
}

export type ConnectorRunStatus = "success" | "partial" | "failed" | "no_new_data" | "never_run";

export interface ConnectorStatus {
  source: string;
  connectorName: string;
  lastRunAt: string | null;
  status: ConnectorRunStatus;
  failureCount: number;
}

export interface DashboardSummary {
  ingestionCompleteness: number | null;
  openConflicts: number;
  timeToDetectFailure: string | null;
  overrideUsage: { count: number; period: string } | null;
}

export interface SaveOverrideInput {
  recordId: string;
  field: string;
  source: string;
  reason?: string;
}

export interface OverrideResult {
  ok: true;
  recordId: string;
  field: string;
}

/** The data contract the screens depend on. `demoApi` and `liveApi` both implement it. */
export interface Api {
  getDashboardSummary(): Promise<DashboardSummary>;
  listReconciliationExceptions(): Promise<ReconciliationException[]>;
  getReconciliationRecord(id: string): Promise<ReconciliationRecord>;
  listConnectorStatuses(): Promise<ConnectorStatus[]>;
  saveOverride(input: SaveOverrideInput): Promise<OverrideResult>;
}
