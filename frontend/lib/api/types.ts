import type { CustomLogic, ResolutionRule, RuleStrategy } from "@/lib/rule-logic";

export type { CustomLogic, ResolutionRule, RuleStrategy };

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

export type ConnectorRunStatus =
  | "success"
  | "partial"
  | "failed"
  | "no_new_data"
  | "running"
  | "never_run";

/** One day of connector-run activity for the dashboard trend. */
export interface ActivityPoint {
  /** UTC day in ISO-8601 (YYYY-MM-DD). */
  date: string;
  /** Runs that completed SUCCESS or NO_NEW_DATA. */
  clean: number;
  /** Runs that completed FAILED or PARTIAL. */
  failed: number;
}

export interface ConnectorStatus {
  source: string;
  connectorName: string;
  lastRunAt: string | null;
  status: ConnectorRunStatus;
  failureCount: number;
  /** Latest run's failure; null when the latest run had no failure. `message` is nullable. */
  failure?: { type: string; message: string | null; at: string; stackTrace?: string | null } | null;
  /** True when the source has a pull connector (i.e. "Run now" applies). */
  runnable: boolean;
}

/** One daily-sales total for a (date, source) pair. */
export interface DailySales {
  date: string;
  source: string;
  totalSales: number | string;
  gst: number | string;
  net: number | string;
}

/** One date's resolved reservation summary. Ratios are null when their denominator is zero. */
export interface ReservationSummary {
  date: string;
  bookings: number;
  attended: number;
  covers: number;
  cancelled: number;
  noShows: number;
  walkIns: number;
  avgPartySize: number | null;
  noShowRate: number | null;
  bookingToCoverConversion: number | null;
}

/**
 * The latest trading date's resolved total, or nulls when there is no data yet or the latest
 * date is still unresolved. `total: null` with a non-null `date` means "needs a decision".
 */
export interface LatestSales {
  date: string | null;
  total: number | null;
  authoritativeSource: string | null;
}

export interface DashboardSummary {
  ingestionCompleteness: number | null;
  openConflicts: number;
  timeToDetectFailure: string | null;
  overrideUsage: { count: number; period: string } | null;
}

/** The dashboard's whole initial render, fetched in one request. */
export interface DashboardBootstrap {
  summary: DashboardSummary;
  latestSales: LatestSales;
  salesTrend: SalesTrendPoint[];
  activity: ActivityPoint[];
  topSellers: TopSeller[];
}

/** A top-selling product over a recent window. `quantitySold`/`amount` are null when unresolved. */
export interface TopSeller {
  name: string;
  quantitySold: number | string | null;
  amount: number | string | null;
  hasConflict: boolean;
}

/** One day of resolved daily sales for the dashboard trend. */
export interface SalesTrendPoint {
  date: string;
  total: number | string | null;
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

export interface ProductOverrideInput {
  date: string;
  product: string;
  source: string;
  reason?: string;
}

export interface SaveResolutionRuleInput {
  id?: string; // present when editing an existing rule
  entityType: string;
  fieldKey: string;
  strategy: RuleStrategy;
  sourcePriority?: string[];
  customLogic?: CustomLogic;
}

export type RecomputeState = "idle" | "recomputing" | "complete" | "failed";

export interface RecomputeStatus {
  state: RecomputeState;
  lastChangedAt: string | null;
}

export interface RuleAuditEntry {
  ruleId: string | null; // null => the rule was deleted
  entityType: string;
  fieldKey: string;
  change: "created" | "updated" | "deleted";
  at: string;
  by: string;
}

/** The bounded semantic query behind a persisted widget. */
export interface MetricQuery {
  metric: string;
  range: { from: string; to: string; calendar: "TRADING" | "CALENDAR" };
  grain: "DAY" | "WEEK" | "MONTH";
  dimensions: string[];
  comparison: string | null;
}

/** A widget's grid span: width in 12-column units, height in row units. */
export interface WidgetLayout {
  w: number;
  h: number;
}

/** One persisted widget: a bounded set of semantic queries plus a render type and a grid span. */
export interface SavedWidget {
  id: string;
  renderType: string;
  queries: MetricQuery[];
  layout: WidgetLayout;
}

/** Dashboard-level reusable filters merged into each widget's query at render. */
export interface DashboardFilters {
  dateRange: { from: string; to: string; calendar: "TRADING" | "CALENDAR" } | null;
  comparison: string | null;
  dimensions: string[];
}

export type Visibility = "PRIVATE" | "SHARED" | "ORG_WIDE";

/**
 * A dashboard's list entry. `description`/`createdBy`/`pinned`/`visibility` are optional
 * because the live `GET /api/dashboards` summary predates the richer card contract and may
 * not return them yet; the library card renders gracefully when they are absent.
 */
export interface SavedDashboardSummary {
  id: string;
  title: string;
  updatedAt: string;
  description?: string | null;
  createdBy?: string;
  pinned?: boolean;
  visibility?: Visibility;
}

export interface DashboardDocument {
  id: string;
  schemaVersion: number;
  title: string;
  description: string | null;
  layout: string;
  widgets: SavedWidget[];
  filters: DashboardFilters;
  visibility: Visibility;
  pinned: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface SaveDashboardInput {
  title: string;
  description?: string | null;
  layout?: string;
  filters?: DashboardFilters;
  visibility?: Visibility;
  widgets: SavedWidget[];
}

/** One widget's render outcome: a resolved spec, or the metric that denied it. */
export interface RenderedWidget {
  widgetId: string;
  widget: import("@/components/widgets/types").WidgetSpec | null;
  deniedResource: string | null;
}

/** A named starting-point dashboard built from catalogue metrics. */
export interface DashboardTemplate {
  id: string;
  name: string;
  description: string;
  widgets: SavedWidget[];
}

/** One revision of a saved dashboard. */
export interface DashboardRevisionSummary {
  revision: number;
  createdBy: string;
  createdAt: string;
}

/** A department × seniority role grant for a SHARED dashboard. */
export interface DashboardSharingRole {
  department: { value: string };
  seniority: { value: string };
}

/** Visibility plus the role list that can open a SHARED dashboard. */
export interface DashboardSharing {
  visibility: Visibility;
  roles: DashboardSharingRole[];
}

/** The data contract the screens depend on. `demoApi` and `liveApi` both implement it. */
export interface Api {
  getDashboardBootstrap(): Promise<DashboardBootstrap>;
  getDashboardSummary(): Promise<DashboardSummary>;
  getDashboardActivity(): Promise<ActivityPoint[]>;
  listReconciliationExceptions(): Promise<ReconciliationException[]>;
  listProductExceptions(): Promise<ReconciliationException[]>;
  getReconciliationRecord(id: string): Promise<ReconciliationRecord>;
  getProductRecord(date: string, product: string): Promise<ReconciliationRecord>;
  listConnectorStatuses(): Promise<ConnectorStatus[]>;
  runConnector(source: string): Promise<ConnectorStatus>;
  uploadOpenTableCsv(file: File): Promise<void>;
  saveOverride(input: SaveOverrideInput): Promise<OverrideResult>;
  saveProductOverride(input: ProductOverrideInput): Promise<OverrideResult>;
  listResolutionRules(): Promise<ResolutionRule[]>;
  saveResolutionRule(input: SaveResolutionRuleInput): Promise<ResolutionRule>;
  deleteResolutionRule(id: string): Promise<void>;
  getRecomputeStatus(): Promise<RecomputeStatus>;
  listRuleAudit(): Promise<RuleAuditEntry[]>;
  listProducts(): Promise<string[]>;
  listDailySales(): Promise<DailySales[]>;
  getLatestSales(): Promise<LatestSales>;
  getReservationSummary(date: string): Promise<ReservationSummary | undefined>;
  getTopSellers(): Promise<TopSeller[]>;
  getSalesTrend(): Promise<SalesTrendPoint[]>;
  listDashboards(): Promise<SavedDashboardSummary[]>;
  getDashboard(id: string): Promise<DashboardDocument>;
  saveDashboard(input: SaveDashboardInput): Promise<DashboardDocument>;
  updateDashboard(id: string, input: SaveDashboardInput): Promise<DashboardDocument>;
  deleteDashboard(id: string): Promise<void>;
  renderDashboard(id: string): Promise<RenderedWidget[]>;
  listDashboardTemplates(): Promise<DashboardTemplate[]>;
  createDashboardFromTemplate(templateId: string): Promise<DashboardDocument>;
  listDashboardRevisions(id: string): Promise<DashboardRevisionSummary[]>;
  restoreDashboardRevision(id: string, revision: number): Promise<DashboardDocument>;
  toggleDashboardPin(id: string): Promise<DashboardDocument>;
  getDashboardSharing(id: string): Promise<DashboardSharing>;
  setDashboardSharing(id: string, sharing: DashboardSharing): Promise<DashboardSharing>;
}
