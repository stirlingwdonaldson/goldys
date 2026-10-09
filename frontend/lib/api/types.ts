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
  /** Why an override was chosen: the reason/actor/timestamp recorded when it was saved. */
  overrideReason?: string | null;
  overrideActor?: string | null;
  overrideAt?: string | null;
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

/** One day's resolved covers (mirrors `semantic.CoversMetric`). */
export interface DailyCovers {
  date: string;
  covers: number;
  authoritativeSource: string | null;
  hasConflict: boolean;
}

/**
 * Period totals for the Staff & Labor screen (mirrors `LabourReportingService.LabourSummary`).
 * Every figure is null when nothing resolved for the period — never a fabricated zero. The
 * department percentages are fractions (0.28 = 28%) of gross sales.
 */
export interface LabourSummary {
  from: string;
  to: string;
  scheduledHours: number | null;
  actualHours: number | null;
  labourCost: number | null;
  variance: number | null;
  hoursPerCover: number | null;
  labourCostPerCover: number | null;
  fohLabourCostPercent: number | null;
  bohLabourCostPercent: number | null;
}

/**
 * Period totals for the Kitchen screen (mirrors `InventoryReportingService.InventorySummary`).
 * `foodCostPercent` is purchases ÷ gross sales as a fraction; null when either side is missing.
 */
export interface InventorySummary {
  from: string;
  to: string;
  purchases: number | null;
  wastage: number | null;
  foodCostPercent: number | null;
}

/** Blended unit cost for one unit-of-measure over a period (mirrors `semantic.UomUnitCost`). */
export interface UomUnitCost {
  uom: string | null;
  lineTotal: number;
  quantity: number;
  unitCost: number | null;
}

/** Purchases and WET grouped by supplier over a period (mirrors `semantic.SupplierCogs`). */
export interface SupplierCogs {
  supplier: string;
  lineTotal: number;
  wetAmount: number;
}

/** Line-level inventory enrichment breakdown for the Kitchen screen. */
export interface InventoryLineBreakdown {
  from: string;
  to: string;
  unitCostByUom: UomUnitCost[];
  cogsBySupplier: SupplierCogs[];
}

/** One supplier in the invoice node graph (mirrors `semantic.SupplierGraphNode`). */
export interface SupplierGraphNode {
  name: string;
  invoiceCount: number;
  totalSpend: number | null;
}

/** One invoice header in the invoice node graph (mirrors `semantic.InvoiceGraphNode`). */
export interface InvoiceGraphNode {
  invoiceNumber: string;
  invoiceDate: string;
  totalAmount: number | null;
  purchaseNumber: string | null;
  pdfFilename: string | null;
}

/** One invoice line in the invoice node graph (mirrors `semantic.LineGraphNode`). */
export interface LineGraphNode {
  productNameKey: string;
  stockCode: string | null;
  quantity: number;
  unitCost: number;
  lineTotal: number;
  uom: string | null;
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

/** The combined reconciliation audit change kinds (rule + override). */
export type ReconciliationAuditChange =
  | "created" // rule
  | "updated" // rule
  | "deleted" // rule
  | "set" // override (authoritative source chosen)
  | "removed"; // override (superseded / no longer authoritative)

/** One entry in the combined rule + override audit history (mirrors the backend `AuditEntry`). */
export interface ReconciliationAuditEntry {
  kind: "rule" | "override";
  change: ReconciliationAuditChange;
  entityType: string;
  fieldKey: string;
  source: string | null;
  reason: string | null;
  by: string;
  at: string;
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
  /** Trust + freshness summary for the widget's metric; null when denied or unresolved. */
  trust: TrustSummary | null;
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

/** A compact row for the Ask Goldy's conversation list (mirrors `ConversationService.ThreadSummary`). */
export interface ConversationThreadSummary {
  id: string;
  title: string;
  updatedAt: string;
  /** The tail of the last message, truncated by the backend. Empty string when there is none. */
  lastPreview: string;
}

/** One persisted message in a thread (mirrors the `ConversationMessage` entity's JSON). */
export interface ConversationMessage {
  id: string;
  threadId: string;
  role: "user" | "assistant";
  content: string;
  toolTrace: import("@/components/ask-goldys/types").TraceEntry[];
  createdAt: string;
}

/** A thread and its full message history, for the owning user (mirrors `ThreadView`). */
export interface ConversationThreadView {
  id: string;
  title: string;
  messages: ConversationMessage[];
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
  listReconciliationAudit(): Promise<ReconciliationAuditEntry[]>;
  listProducts(): Promise<string[]>;
  listDailySales(): Promise<DailySales[]>;
  getLatestSales(): Promise<LatestSales>;
  getReservationSummary(date: string): Promise<ReservationSummary | undefined>;
  getInventoryLines(from: string, to: string): Promise<InventoryLineBreakdown>;
  getInventorySummary(from: string, to: string): Promise<InventorySummary>;
  getInvoiceGraphSuppliers(from: string, to: string): Promise<SupplierGraphNode[]>;
  getInvoiceGraphInvoices(
    supplier: string,
    from: string,
    to: string,
  ): Promise<InvoiceGraphNode[]>;
  getInvoiceGraphLines(invoiceNumber: string): Promise<LineGraphNode[]>;
  getLabourSummary(from: string, to: string): Promise<LabourSummary>;
  listDailyCovers(from: string, to: string): Promise<DailyCovers[]>;
  getProvenance(metricId: string, date: string): Promise<Provenance>;
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
  listThreads(): Promise<ConversationThreadSummary[]>;
  getThread(id: string): Promise<ConversationThreadView>;
  renameThread(id: string, title: string): Promise<ConversationThreadView>;
  deleteThread(id: string): Promise<void>;
  listRawRecords(
    filter: RawRecordFilter,
    page: number,
    size: number,
  ): Promise<DataPage<RawRecordSummary>>;
  getRawRecord(id: string): Promise<RawRecordDetail>;
  listCanonicalEntities(): Promise<EntityDescriptor[]>;
  listCanonicalRows(entity: string, page: number, size: number): Promise<DataPage<GenericRow>>;
  listResolvedDomains(): Promise<EntityDescriptor[]>;
  listResolvedRows(domain: string, page: number, size: number): Promise<DataPage<GenericRow>>;
}

/** How a resolved value earned the operator's trust, strongest to weakest (backend `TrustState`). */
export type TrustState =
  | "VERIFIED"
  | "RESOLVED_BY_RULE"
  | "MANUALLY_OVERRIDDEN"
  | "SINGLE_SOURCE"
  | "CONFLICTED"
  | "INCOMPLETE"
  | "NOT_RECEIVED";

/** Freshness of the source data relative to its ingestion cadence (backend `FreshnessState`). */
export type FreshnessState = "FRESH" | "STALE" | "SOURCE_FAILURE" | "UNKNOWN";

/** Why a period carries no value, in venue-friendly language (backend `MissingDataStatus`). */
export type MissingDataStatus =
  | "ZERO"
  | "UNKNOWN"
  | "NOT_RECEIVED"
  | "UNRESOLVED"
  | "NOT_APPLICABLE"
  | "NOT_PERMITTED";

/**
 * Trust and freshness summary for a metric over a range (mirrors the backend `TrustSummary` record).
 * `threshold` is the backend `Duration`, serialized by Jackson as a numeric count of milliseconds.
 */
export interface TrustSummary {
  state: TrustState;
  freshness: FreshnessState;
  authoritativeSource: string | null;
  resolvedAt: string | null;
  lastIngestionAt: string | null;
  threshold: number | null;
}

/**
 * A value contributed by one source system (mirrors the backend `semantic.SourceValue`). Named
 * `ProvenanceSourceValue` because the reconciliation `SourceValue` above already occupies the
 * unqualified name with a different shape.
 */
export interface ProvenanceSourceValue {
  sourceSystem: string;
  value: number | null;
  recordedAt: string | null;
}

/** Why and how a resolved value was chosen (mirrors the backend `ResolutionDetail`). */
export interface ResolutionDetail {
  kind: string | null;
  source: string | null;
  reason: string | null;
  actor: string | null;
  at: string | null;
}

/** Full provenance for one resolved metric value on one date (mirrors the backend `Provenance`). */
export interface Provenance {
  metric: string;
  date: string;
  resolvedValue: number | null;
  trust: TrustSummary;
  sources: ProvenanceSourceValue[];
  resolution: ResolutionDetail;
  rawRecordIds: string[];
}

/** Optional filter for the data explorer's raw-record list. */
export interface RawRecordFilter {
  source?: string;
  fetcher?: string;
  method?: string;
  from?: string;
  to?: string;
}

/** Metadata for one raw ingestion record, without the payload bytes. */
export interface RawRecordSummary {
  id: string;
  sourceSystem: string;
  fetcherIdentity: string;
  fetchMethod: string;
  contentType: string;
  characterEncoding: string | null;
  fetchedAt: string;
  byteLength: number;
  sha256: string;
}

/** One raw record's metadata plus its payload. */
export interface RawRecordDetail {
  summary: RawRecordSummary;
  payload: string;
  isJson: boolean;
  sha256: string;
}

/** A browsable canonical entity type or resolved domain. */
export interface EntityDescriptor {
  id: string;
  label: string;
  placeholder: boolean;
}

/** One entity/domain row rendered as an ordered column map. */
export interface GenericRow {
  id: string;
  columns: Record<string, string>;
}

/** A paged result for the explorer's list endpoints. */
export interface DataPage<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}
