import type { CustomLogic, ResolutionRule, RuleStrategy } from "@/lib/rule-logic";

export type { CustomLogic, ResolutionRule, RuleStrategy };

/** The validated staff profile for the current Spring session. */
export type { CurrentUser } from "./auth-schema";

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

/** Assisted OpenTable sign-in state (`OpenTableSessionApplicationService.SessionState`). */
export type OpenTableSessionStateName =
  | "DISCONNECTED"
  | "LOGIN_REQUIRED"
  | "SYNCING"
  | "DONE"
  | "FAILED";

/** INCREMENTAL: recent + upcoming reservations, last 120 days of reviews. FULL: all history. */
export type OpenTableSyncMode = "INCREMENTAL" | "FULL";

export interface OpenTableSessionState {
  state: OpenTableSessionStateName;
  mode: OpenTableSyncMode;
  /** The sync run's terminal status (SUCCESS, PARTIAL, …) once known. */
  runStatus: string | null;
  /** A short operator message; never page content. */
  detail: string | null;
  at: string;
}

/** One live-view frame. `width`/`height` are the remote page's CSS viewport. */
export interface OpenTableFrame {
  jpegBase64: string;
  width: number;
  height: number;
}

/**
 * One input event for the remote browser, in the remote page's CSS pixels. Printable
 * characters and pastes go as `text`; `key` is limited server-side to editing keys.
 * These carry what the owner types (their OpenTable password): never log them.
 */
export type OpenTableInput =
  | {
      type: "mouse";
      action: "down" | "up" | "move";
      x: number;
      y: number;
      button?: "none" | "left" | "middle" | "right";
      clickCount?: number;
    }
  | { type: "wheel"; x: number; y: number; deltaX: number; deltaY: number }
  | { type: "key"; action: "down" | "up"; key: string; modifiers?: number }
  | { type: "text"; text: string };

export interface OpenTableSessionHandlers {
  onState(state: OpenTableSessionState): void;
  onFrame(frame: OpenTableFrame): void;
  /** The stream ended (another viewer took over, the session ended, or the connection dropped). */
  onClosed(): void;
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

/** One node of a network graph (mirrors `semantic.NetworkNodeDto`). */
export interface NetworkNodeDto {
  id: string;
  /** `source`, `domain`, `metric`, `derived-metric`, `dashboard`, `permission` or `product`. */
  kind: string;
  label: string;
  subtitle: string | null;
  /** `ok`, `warn`, `fail`, `missing`, `info` or `neutral`. */
  tone: string;
  size: number;
  group: string | null;
  href: string | null;
}

/** One undirected edge of a network graph (mirrors `semantic.NetworkEdgeDto`). */
export interface NetworkEdgeDto {
  source: string;
  target: string;
  weight: number;
  label: string | null;
}

/** The platform map (`GET /api/platform-map`, mirrors `semantic.NetworkGraphDto`). */
export interface NetworkGraphDto {
  nodes: NetworkNodeDto[];
  edges: NetworkEdgeDto[];
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
 * One current invoice header for the browse list (mirrors the backend
 * `InvoiceReportingService.InvoiceSummary`). `invoiceDate` is an ISO date string; the other
 * nullable fields stay `| null` and are never defaulted.
 */
export interface InvoiceSummary {
  supplierName: string;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string | null;
  totalAmount: number | null;
  purchaseNumber: string | null;
  hasPdf: boolean;
}

/** One invoice header plus its line graph nodes (mirrors the backend `InvoiceDetail`). */
export interface InvoiceDetail extends InvoiceSummary {
  lines: LineGraphNode[];
}

/** Browse-list filters; an absent field means "any". */
export interface InvoiceFilter {
  supplier?: string;
  from?: string;
  to?: string;
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

/** Per-dish costing: CTB recipe cost vs Lightspeed price/cost (mirrors backend `RecipeCosting`). */
export interface RecipeCosting {
  recipeId: number;
  name: string | null;
  folder: string | null;
  portionSize: string | null;
  costPerPortion: number | null;
  actualCost: number | null;
  costGap: number | null;
  menuPrice: number | null;
  margin: number | null;
  gpPercent: number | null;
  /** "ON_MENU" | "OFF_MENU" | "UNKNOWN" — the recipe's current menu status. */
  menuState: string;
  /** "CURRENT" | "HISTORICAL" | "NONE" — where the effective menu price came from. */
  priceSource: string;
  lastSoldPrice: number | null;
  lastSoldCost: number | null;
  lastSoldDate: string | null;
}

/**
 * One recipe's resolved ingredient line (mirrors backend `RecipeIngredient`). `lineRecipeName` is
 * the recipe the line belongs to (the parent for main lines, a sub-recipe for sub-recipe lines).
 * Quantity and cost are null when CTB sent nothing parseable — never a fabricated zero.
 */
export interface RecipeIngredient {
  parentRecipeId: number;
  lineRecipeId: number | null;
  lineRecipeName: string | null;
  isSubRecipe: boolean | null;
  recipeDetailId: number;
  stockId: number | null;
  stockCode: string | null;
  stockDescription: string | null;
  portionWeight: number | null;
  unitOfMeasurement: string | null;
  costPerQty: number | null;
  costPerUnit: number | null;
  supplierName: string | null;
}

/** One curated category in the taxonomy (mirrors backend `CategoryNode`). `groupName` is null for flat (top-level) categories. */
export interface CategoryNode {
  id: string;
  name: string;
  groupName: string | null;
  productCount: number;
}

/**
 * One node of the agglomerative dendrogram for a classification run (mirrors backend
 * `DendrogramNode`). Leaves are products (`productNameKey` set); internal nodes are merges
 * (`productNameKey` null) with a `mergeHeight` and a `size` of leaves beneath them.
 */
export interface DendrogramNode {
  id: string;
  parentId: string | null;
  isLeaf: boolean;
  productNameKey: string | null;
  mergeHeight: number;
  size: number;
}

/**
 * One product's position in the 2-D embedding projection of the latest run (mirrors backend
 * `ProductPoint`). `categoryId` and `confidence` are null while the product is unassigned.
 */
export interface ProductPoint {
  productNameKey: string;
  x: number;
  y: number;
  categoryId: string | null;
  confidence: number | null;
}

/**
 * A product→category assignment as surfaced to the UI (mirrors backend `AssignmentView`).
 * `categoryId`/`categoryName`/`groupName`/`confidence`/`source` are null when unassigned.
 */
export interface AssignmentRow {
  productNameKey: string;
  categoryId: string | null;
  categoryName: string | null;
  groupName: string | null;
  confidence: number | null;
  source: string | null;
  status: string;
}

/** Body for `POST /api/products/categories/categories` (mirrors the controller's `CreateCategoryRequest`). */
export interface CreateCategoryInput {
  name: string;
  groupName?: string | null;
  displayOrder: number;
  description?: string | null;
}

/** Body for `PATCH /api/products/categories/categories/{id}`. A field left null is left unchanged. */
export interface UpdateCategoryInput {
  name?: string | null;
  groupName?: string | null;
}

/** One invoice-ingestion anomaly, read-only (mirrors the backend `InvoiceIngestFlagView`). */
export interface InvoiceIngestFlag {
  flagType: string;
  invoiceNumber: string | null;
  pdfFilename: string | null;
  stockCode: string | null;
  detail: string | null;
  occurredAt: string;
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

/** A generic metric-query result (mirrors the backend sealed `MetricResult`). */
export type MetricResult = TimeSeriesMetricResult | RankedListMetricResult;

/** One bucket's value in a metric series; `status` explains a null value. */
export interface MetricSeriesPoint {
  bucketStart: string;
  value: number | string | null;
  status: MissingDataStatus | null;
}

/** One series of points; `dimensionValue` is null for an ungrouped series. */
export interface MetricSeries {
  dimensionValue: string | null;
  points: MetricSeriesPoint[];
}

/** One row in a ranked-list result (used by `product.top_sellers`). */
export interface MetricRankedItem {
  label: string;
  primary: number | string;
  secondary: number | string;
  hasConflict: boolean;
}

/** Provenance for a metric result (mirrors the backend `MetricProvenance` record). */
export interface MetricResultProvenance {
  metric: string;
  definitionVersion: string;
  range: { from: string; to: string; calendar: "TRADING" | "CALENDAR" };
  grain: "DAY" | "WEEK" | "MONTH";
  sourceDomain: string;
  dataFreshness: string | null;
  missingPeriods: string[];
  calculationVersion: string;
  trust: TrustSummary | null;
}

/** A time-series (or multi-series) metric result. */
export interface TimeSeriesMetricResult {
  metric: string;
  series: MetricSeries[];
  notices: string[];
  provenance: MetricResultProvenance;
}

/** A ranked-list metric result. */
export interface RankedListMetricResult {
  metric: string;
  items: MetricRankedItem[];
  notices: string[];
  provenance: MetricResultProvenance;
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

/** One current payment tender, for the Payments list endpoint (mirrors `semantic.PaymentRow`). */
export interface PaymentRow {
  tradingDate: string;
  saleNumber: string;
  paymentTypeName: string | null;
  paymentTypeCode: string | null;
  paymentSourceType: string | null;
  lspayPaymentMode: string | null;
  clearingAccount: string | null;
  amount: number | string;
  tip: number | string;
  tendered: number | string;
  surcharge: number | string;
  paymentCount: number;
  tipCount: number;
  reconciled: string;
  registerCode: string | null;
  registerName: string | null;
  staffName: string | null;
  staffCode: string | null;
  siteId: string | null;
  customerName: string | null;
}

/** One current deleted order, for the Deleted-sales list endpoint (mirrors `semantic.DeletedSaleRow`). */
export interface DeletedSaleRow {
  tradingDate: string;
  saleNumber: string;
  orderType: string | null;
  note: string | null;
  totalIncTax: number | string;
  totalExTax: number | string;
  totalTax: number | string;
  totalCost: number | string | null;
  openedRegisterName: string | null;
  deletedRegisterName: string | null;
  staffName: string | null;
  deletedByStaffName: string | null;
  tableNumber: string | null;
  siteId: string | null;
  customerName: string | null;
}

/** One current sale line item, for the Sale-items list endpoint (mirrors `semantic.SaleItemRow`). */
export interface SaleItemRow {
  tradingDate: string;
  saleNumber: string | null;
  /** The Lightspeed receipt-line id (the backend's `source_record_ref`). */
  receiptLineId: string;
  itemName: string | null;
  productNumber: string | null;
  sku: string | null;
  categoryName: string | null;
  quantitySold: number | null;
  amount: number | string | null;
  soldPriceIncTax: number | string | null;
  totalTax: number | string | null;
  costIncTax: number | string | null;
  orderType: string | null;
  saleType: string | null;
  staffName: string | null;
  registerName: string | null;
  tableNumber: string | null;
}

/** One date/payment-type's resolved payment mix (mirrors `semantic.PaymentMix`). */
export interface PaymentMix {
  tradingDate: string;
  paymentTypeName: string;
  amount: number | string;
  tip: number | string;
  count: number;
  hasConflict: boolean;
}

/** One date's resolved deleted-sale totals (mirrors `semantic.DeletedSaleDay`). */
export interface DeletedSaleDay {
  tradingDate: string;
  count: number;
  totalIncTax: number | string;
  totalTax: number | string;
  hasConflict: boolean;
}

/** One date/category's resolved sale-item mix (mirrors `semantic.SaleItemMix`). */
export interface SaleItemMix {
  tradingDate: string;
  categoryName: string;
  quantity: number | string;
  amount: number | string;
  hasConflict: boolean;
}

/** Optional filter for the Payments list (from/to are inclusive ISO dates). */
export interface PaymentFilter {
  from?: string;
  to?: string;
  paymentType?: string;
  saleNumber?: string;
}

/** Optional filter for the Deleted-sales list (from/to are inclusive ISO dates). */
export interface DeletedSaleFilter {
  from?: string;
  to?: string;
  saleNumber?: string;
}

/** Optional filter for the Sale-items list (from/to are inclusive ISO dates). */
export interface SaleItemFilter {
  from?: string;
  to?: string;
  category?: string;
  saleNumber?: string;
}

/** Optional filter for the Daily-sales list (from/to are inclusive ISO dates). */
export interface SalesFilter {
  from?: string;
  to?: string;
}

/** Lifecycle of a TimesFM forecast run (mirrors the backend `ForecastRunStatus`). */
export type ForecastRunStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";

/** One forecastable metric's catalogue metadata (mirrors `application.ForecastMetricDto`). */
export interface ForecastMetric {
  metric: string;
  name: string;
  unit: string;
  sourceDomain: string;
  requiredPermission: string;
}

/** One forecasted day: the median plus the p10/p90 deciles (mirrors `ForecastSeriesPointDto`). */
export interface ForecastSeriesPoint {
  /** Forecast date, YYYY-MM-DD. */
  date: string;
  /** A decile is `null` when the model had no estimate for it (NaN serialised as JSON null). */
  median: number | null;
  p10: number | null;
  p90: number | null;
}

/**
 * The public shape of a forecast run (mirrors `application.ForecastRunDto`). `series` is
 * populated only once the run has `SUCCEEDED`; until then it is `null`.
 */
export interface ForecastRun {
  id: string;
  status: ForecastRunStatus;
  targetMetric: string;
  covariates: string[];
  /** YYYY-MM-DD. */
  historyFrom: string;
  /** YYYY-MM-DD. */
  historyTo: string;
  horizonDays: number;
  /** ISO-8601 instant. */
  createdAt: string;
  series: ForecastSeriesPoint[] | null;
  errorMessage: string | null;
  modelVersion: string | null;
}

/** Body for `POST /api/forecasting/runs` (mirrors `application.CreateForecastRequest`). */
export interface CreateForecastInput {
  target: string;
  covariates: string[];
  /** YYYY-MM-DD. */
  historyFrom: string;
  horizonDays: number;
}

/** One forecast date compared against its resolved actual (mirrors `ForecastComparisonPointDto`). */
export interface ForecastComparisonPoint {
  /** YYYY-MM-DD. */
  date: string;
  predictedMedian: number;
  actual: number | null;
}

/** Forecast-versus-actual for a succeeded run (mirrors `application.ForecastComparisonDto`). */
export interface ForecastComparison {
  runId: string;
  mape: number | null;
  mae: number | null;
  points: ForecastComparisonPoint[];
}

/** The data contract the screens depend on. `demoApi` and `liveApi` both implement it. */
export interface Api {
  getDashboardBootstrap(): Promise<DashboardBootstrap>;
  getDashboardSummary(): Promise<DashboardSummary>;
  getDashboardActivity(from?: string, to?: string): Promise<ActivityPoint[]>;
  listReconciliationExceptions(page: number, size: number): Promise<DataPage<ReconciliationException>>;
  listProductExceptions(page: number, size: number): Promise<DataPage<ReconciliationException>>;
  getReconciliationRecord(id: string): Promise<ReconciliationRecord>;
  getProductRecord(date: string, product: string): Promise<ReconciliationRecord>;
  listConnectorStatuses(): Promise<ConnectorStatus[]>;
  runConnector(source: string): Promise<ConnectorStatus>;
  /** Uploads a CSV drop for one connector (the OpenTable GuestCenter datasets). */
  uploadConnectorCsv(connectorName: string, file: File): Promise<void>;
  /** Fails with NOT_CONFIGURED when the assisted sync isn't enabled on this deployment. */
  getOpenTableSession(): Promise<OpenTableSessionState>;
  startOpenTableSession(mode: OpenTableSyncMode): Promise<OpenTableSessionState>;
  /** Relays one input event; events arriving after sign-in completes are dropped silently. */
  sendOpenTableInput(input: OpenTableInput): Promise<void>;
  /** Stops the live view; `signOut` also clears the OpenTable login from the VM browser. */
  endOpenTableSession(signOut: boolean): Promise<void>;
  /** Subscribes to state and frames; returns an unsubscribe function. */
  watchOpenTableSession(handlers: OpenTableSessionHandlers): () => void;
  saveOverride(input: SaveOverrideInput): Promise<OverrideResult>;
  saveProductOverride(input: ProductOverrideInput): Promise<OverrideResult>;
  listResolutionRules(): Promise<ResolutionRule[]>;
  saveResolutionRule(input: SaveResolutionRuleInput): Promise<ResolutionRule>;
  deleteResolutionRule(id: string): Promise<void>;
  getRecomputeStatus(): Promise<RecomputeStatus>;
  listRuleAudit(): Promise<RuleAuditEntry[]>;
  listReconciliationAudit(page: number, size: number): Promise<DataPage<ReconciliationAuditEntry>>;
  listProducts(): Promise<string[]>;
  listDailySales(filter: SalesFilter, page: number, size: number): Promise<DataPage<DailySales>>;
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
  listInvoices(
    filter: InvoiceFilter,
    page: number,
    size: number,
  ): Promise<DataPage<InvoiceSummary>>;
  getInvoice(invoiceNumber: string): Promise<InvoiceDetail>;
  listInvoiceFlags(): Promise<InvoiceIngestFlag[]>;
  /** Sources → domains → metrics → dashboards; requires `connectors` READ. */
  getPlatformMap(): Promise<NetworkGraphDto>;
  getLabourSummary(from: string, to: string): Promise<LabourSummary>;
  listDailyCovers(from: string, to: string): Promise<DailyCovers[]>;
  getProvenance(metricId: string, date: string): Promise<Provenance>;
  getTopSellers(from?: string, to?: string, limit?: number): Promise<TopSeller[]>;
  getSalesTrend(from?: string, to?: string): Promise<SalesTrendPoint[]>;
  /** Runs a bounded catalogue query and returns the raw metric result (time-series or ranked list). */
  queryMetrics(query: MetricQuery): Promise<MetricResult>;
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
  listPayments(filter: PaymentFilter, page: number, size: number): Promise<DataPage<PaymentRow>>;
  listDeletedSales(
    filter: DeletedSaleFilter,
    page: number,
    size: number,
  ): Promise<DataPage<DeletedSaleRow>>;
  listSaleItems(filter: SaleItemFilter, page: number, size: number): Promise<DataPage<SaleItemRow>>;
  getPaymentMix(from: string, to: string): Promise<PaymentMix[]>;
  getDeletedSaleTotals(from: string, to: string): Promise<DeletedSaleDay[]>;
  getSaleItemMix(from: string, to: string): Promise<SaleItemMix[]>;
  listForecastMetrics(): Promise<ForecastMetric[]>;
  createForecastRun(input: CreateForecastInput): Promise<ForecastRun>;
  listForecastRuns(): Promise<ForecastRun[]>;
  getForecastRun(id: string): Promise<ForecastRun>;
  getForecastComparison(id: string): Promise<ForecastComparison>;
  getRecipes(): Promise<RecipeCosting[]>;
  getRecipeIngredients(recipeId: number): Promise<RecipeIngredient[]>;
  getCategoryTaxonomy(): Promise<CategoryNode[]>;
  getCategoryDendrogram(): Promise<DendrogramNode[]>;
  getCategoryEmbeddingSpace(): Promise<ProductPoint[]>;
  /** Omit `status` (or pass null/undefined) for all assignments; never pass an empty string. */
  getCategoryAssignments(status?: string): Promise<AssignmentRow[]>;
  createCategory(input: CreateCategoryInput): Promise<CategoryNode>;
  /** Renames and/or regroups a category; a null field is left unchanged. */
  renameCategory(id: string, input: UpdateCategoryInput): Promise<void>;
  moveProduct(productNameKey: string, categoryId: string): Promise<void>;
  clearProductOverride(productNameKey: string): Promise<void>;
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
