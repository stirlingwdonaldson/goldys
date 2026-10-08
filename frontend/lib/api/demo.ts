import { deriveExceptions } from "../reconciliation-logic";
import { ApiError } from "./errors";
import type { WidgetSpec } from "@/components/widgets/types";
import type {
  ActivityPoint,
  Api,
  ConnectorStatus,
  ConversationMessage,
  ConversationThreadSummary,
  ConversationThreadView,
  DailySales,
  DashboardBootstrap,
  DashboardDocument,
  DashboardFilters,
  DashboardRevisionSummary,
  DashboardSharing,
  DashboardSummary,
  DashboardTemplate,
  LatestSales,
  MetricQuery,
  OverrideResult,
  ProductOverrideInput,
  RecomputeStatus,
  ReconciliationException,
  ReconciliationRecord,
  RenderedWidget,
  ResolutionRule,
  RuleAuditEntry,
  SalesTrendPoint,
  SaveDashboardInput,
  SaveOverrideInput,
  SaveResolutionRuleInput,
  SavedDashboardSummary,
  SavedWidget,
  TopSeller,
} from "./types";

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

// In-memory, mutable fixtures so the override flow genuinely resolves an exception
// and it drops from the list — the demo behaves like the real data would.
const records: Record<string, ReconciliationRecord> = {
  "sale-4821": {
    id: "sale-4821",
    entity: "Sale #4821",
    entityType: "sale",
    fields: [
      {
        name: "quantity_sold",
        label: "Quantity sold",
        overridden: false,
        sources: [
          { source: "Lightspeed", value: "14" },
          { source: "Cooking the Books", value: "12" },
        ],
      },
      {
        name: "amount",
        label: "Net amount",
        overridden: false,
        sources: [
          { source: "Lightspeed", value: "$248.50" },
          { source: "Cooking the Books", value: "$212.00" },
        ],
      },
      {
        name: "gross_sales",
        label: "Gross sales",
        overridden: false,
        sources: [
          { source: "Lightspeed", value: "$260.00" },
          { source: "Cooking the Books", value: null },
        ],
      },
    ],
  },
  "sale-4836": {
    id: "sale-4836",
    entity: "Sale #4836",
    entityType: "sale",
    fields: [
      {
        name: "quantity_sold",
        label: "Quantity sold",
        overridden: false,
        sources: [
          { source: "Lightspeed", value: "3" },
          { source: "Cooking the Books", value: "3" },
        ],
      },
      {
        name: "amount",
        label: "Net amount",
        overridden: false,
        sources: [
          { source: "Lightspeed", value: "$54.00" },
          { source: "Cooking the Books", value: "$51.25" },
        ],
      },
    ],
  },
};

const connectorStatuses: ConnectorStatus[] = [
  { source: "Lightspeed", connectorName: "lightspeed-scrape", lastRunAt: "2026-09-20T09:05:00Z", status: "success", failureCount: 0, runnable: false },
  { source: "Cooking the Books", connectorName: "ctb-export", lastRunAt: "2026-09-20T09:00:00Z", status: "partial", failureCount: 1, runnable: true },
  { source: "Deputy", connectorName: "deputy-api", lastRunAt: "2026-09-19T22:30:00Z", status: "failed", failureCount: 2, runnable: false },
  { source: "OpenTable", connectorName: "opentable-guestcenter", lastRunAt: null, status: "never_run", failureCount: 0, runnable: false },
];

let productExceptions: ReconciliationException[] = [
  {
    id: "2026-09-14:garlic aioli",
    recordId: "garlic aioli",
    entity: "garlic aioli",
    field: "garlic aioli",
    sources: [
      { source: "Lightspeed", value: "150 × $380.88" },
      { source: "Cooking the Books", value: "127 × $322.46" },
    ],
    status: "conflict",
  },
];

// Mutable per-product records for the drill-in; the override flips overridden here too.
const productRecords: Record<string, ReconciliationRecord> = {
  "garlic aioli": {
    id: "garlic aioli",
    entity: "garlic aioli",
    entityType: "product",
    fields: [
      {
        name: "garlic aioli",
        label: "garlic aioli",
        sources: [
          { source: "Lightspeed", value: "150 × $380.88" },
          { source: "Cooking the Books", value: "127 × $322.46" },
        ],
        overridden: false,
      },
    ],
  },
};

/** A plausible trailing-14-day run series, ending today (UTC), for the demo chart. */
function activityFixture(): ActivityPoint[] {
  const points: ActivityPoint[] = [];
  const today = new Date();
  for (let i = 13; i >= 0; i--) {
    const day = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate() - i));
    const date = day.toISOString().slice(0, 10);
    // Mostly clean days, a couple of failed runs sprinkled in.
    const failed = i === 2 || i === 7 ? 2 : i === 10 ? 1 : 0;
    const clean = 3 + (i % 3);
    points.push({ date, clean, failed });
  }
  return points;
}

// Demo-only product list: in live mode the product names come from canonical
// product sales, but no "list products" endpoint exists yet, so the editor's
// product picker falls back to these fixtures. They are demo data, not real menu
// items, and are surfaced as such in the UI.
const DEMO_PRODUCTS = ["garlic aioli", "pint carlton draught"];

let rules: ResolutionRule[] = [
  {
    id: "rule-1",
    entityType: "daily_sales",
    fieldKey: "daily_sales",
    strategy: "priority",
    sourcePriority: ["Cooking the Books", "Lightspeed"],
    updatedAt: "2026-09-29T18:00:00Z",
    updatedBy: "Stirling Donaldson",
  },
  {
    id: "rule-2",
    entityType: "product_sales",
    fieldKey: "garlic aioli",
    strategy: "priority",
    sourcePriority: ["Lightspeed", "Cooking the Books"],
    updatedAt: "2026-09-28T09:30:00Z",
    updatedBy: "Stirling Donaldson",
  },
  {
    id: "rule-3",
    entityType: "product_sales",
    fieldKey: "*",
    strategy: "manual",
    updatedAt: "2026-09-27T16:00:00Z",
    updatedBy: "Stirling Donaldson",
  },
];

let recomputeStatus: RecomputeStatus = {
  state: "complete",
  lastChangedAt: "2026-09-30T08:00:00Z",
};

let ruleAudit: RuleAuditEntry[] = [
  {
    ruleId: "rule-1",
    entityType: "daily_sales",
    fieldKey: "daily_sales",
    change: "created",
    at: "2026-09-29T18:00:00Z",
    by: "Stirling Donaldson",
  },
];

// In-memory Ask Goldy's conversation history. Mutable so rename/delete behave like the live
// endpoints: a renamed thread keeps its spot, a deleted one drops from the list.
let demoThreads: ConversationThreadSummary[] = [
  {
    id: "thread-1",
    title: "What were sales last week?",
    updatedAt: "2026-10-05T09:12:00Z",
    lastPreview: "Gross sales were $43,691.48 last week, up 4% on the week before…",
  },
  {
    id: "thread-2",
    title: "Show covers vs no-shows for the weekend",
    updatedAt: "2026-10-04T18:40:00Z",
    lastPreview: "Saturday had 214 covers and a 9.4% no-show rate.",
  },
];

const demoThreadMessages: Record<string, ConversationMessage[]> = {
  "thread-1": [
    {
      id: "m1",
      threadId: "thread-1",
      role: "user",
      content: "What were sales last week?",
      toolTrace: [],
      createdAt: "2026-10-05T09:11:00Z",
    },
    {
      id: "m2",
      threadId: "thread-1",
      role: "assistant",
      content: "Gross sales were $43,691.48 last week, up 4% on the week before.",
      toolTrace: [
        { tool: "query_metric", description: "sales.gross over the last trading week", provenance: [] },
      ],
      createdAt: "2026-10-05T09:12:00Z",
    },
  ],
  "thread-2": [
    {
      id: "m3",
      threadId: "thread-2",
      role: "user",
      content: "Show covers vs no-shows for the weekend",
      toolTrace: [],
      createdAt: "2026-10-04T18:39:00Z",
    },
    {
      id: "m4",
      threadId: "thread-2",
      role: "assistant",
      content: "Saturday had 214 covers and a 9.4% no-show rate.",
      toolTrace: [
        { tool: "query_metric", description: "reservations.covers and no-shows", provenance: [] },
      ],
      createdAt: "2026-10-04T18:40:00Z",
    },
  ],
};

export const demoApi: Api = {
  async getDashboardBootstrap(): Promise<DashboardBootstrap> {
    await delay(400);
    return {
      summary: {
        ingestionCompleteness: 92,
        openConflicts: deriveExceptions(records).length + productExceptions.length,
        timeToDetectFailure: "42m avg",
        overrideUsage: { count: 3, period: "this week" },
      },
      latestSales: { date: "2026-10-05", total: 10865.72, authoritativeSource: "agreed" },
      salesTrend: [
        { date: "2026-10-01", total: 9582.11 },
        { date: "2026-10-02", total: 33909.35 },
        { date: "2026-10-03", total: 43618.92 },
        { date: "2026-10-04", total: 29605.13 },
        { date: "2026-10-05", total: 10865.72 },
      ],
      activity: activityFixture(),
      topSellers: [
        { name: "pint carlton draught", quantitySold: 493, amount: 7904.25, hasConflict: false },
        { name: "chicken schnitzel", quantitySold: 211, amount: 5591.5, hasConflict: false },
        { name: "garlic aioli", quantitySold: 150, amount: 380.88, hasConflict: true },
        { name: "parma", quantitySold: 132, amount: 3696, hasConflict: false },
        { name: "house red", quantitySold: 98, amount: 1078, hasConflict: false },
      ],
    };
  },

  async getDashboardSummary(): Promise<DashboardSummary> {
    await delay(400);
    return {
      ingestionCompleteness: 92,
      openConflicts: deriveExceptions(records).length + productExceptions.length,
      timeToDetectFailure: "42m avg",
      overrideUsage: { count: 3, period: "this week" },
    };
  },

  async getDashboardActivity(): Promise<ActivityPoint[]> {
    await delay(400);
    return activityFixture();
  },

  async listReconciliationExceptions(): Promise<ReconciliationException[]> {
    await delay(400);
    return deriveExceptions(records);
  },

  async getReconciliationRecord(id: string): Promise<ReconciliationRecord> {
    await delay(300);
    const record = records[id];
    if (!record) throw new ApiError("VALIDATION_FAILED", `No record with id ${id}.`);
    return record;
  },

  async getProductRecord(date: string, product: string): Promise<ReconciliationRecord> {
    await delay(300);
    const record = productRecords[product];
    if (!record) throw new ApiError("VALIDATION_FAILED", `No product record ${product}.`);
    return record;
  },

  async listConnectorStatuses(): Promise<ConnectorStatus[]> {
    await delay(400);
    return connectorStatuses;
  },

  async runConnector(source: string): Promise<ConnectorStatus> {
    await delay(600);
    const existing = connectorStatuses.find((c) => c.source === source);
    if (!existing) throw new ApiError("VALIDATION_FAILED", `Unknown source: ${source}`);
    existing.lastRunAt = new Date().toISOString();
    existing.status = "success";
    existing.failureCount = 0;
    return existing;
  },

  async uploadOpenTableCsv(_file: File): Promise<void> {
    void _file; // demo no-op: nothing to upload
    await delay(600);
    const opentable = connectorStatuses.find((c) => c.source === "OpenTable");
    if (opentable) {
      opentable.lastRunAt = new Date().toISOString();
      opentable.status = "success";
      opentable.failureCount = 0;
    }
  },

  async saveOverride(input: SaveOverrideInput): Promise<{ ok: true; recordId: string; field: string }> {
    await delay(500);
    const record = records[input.recordId];
    const field = record?.fields.find((f) => f.name === input.field);
    if (!record || !field) {
      throw new ApiError("VALIDATION_FAILED", "Unknown record or field.");
    }
    field.overridden = true;
    field.authoritativeSource = input.source;
    return { ok: true, recordId: input.recordId, field: input.field };
  },

  async listProductExceptions(): Promise<ReconciliationException[]> {
    await delay(400);
    return productExceptions;
  },

  async saveProductOverride(input: ProductOverrideInput): Promise<OverrideResult> {
    await delay(500);
    const record = productRecords[input.product];
    if (!record) throw new ApiError("VALIDATION_FAILED", `Unknown product ${input.product}.`);
    record.fields[0].overridden = true;
    record.fields[0].authoritativeSource = input.source;
    productExceptions = productExceptions.filter((e) => e.recordId !== input.product);
    return { ok: true, recordId: input.product, field: input.product };
  },

  async listResolutionRules(): Promise<ResolutionRule[]> {
    await delay(400);
    return [...rules];
  },

  async saveResolutionRule(input: SaveResolutionRuleInput): Promise<ResolutionRule> {
    await delay(500);
    const now = new Date().toISOString();
    if (input.id) {
      const existing = rules.find((r) => r.id === input.id);
      if (!existing) throw new ApiError("VALIDATION_FAILED", `No rule with id ${input.id}.`);
      Object.assign(existing, {
        entityType: input.entityType,
        fieldKey: input.fieldKey,
        strategy: input.strategy,
        sourcePriority: input.sourcePriority,
        customLogic: input.customLogic,
        updatedAt: now,
      });
      ruleAudit = [
        {
          ruleId: existing.id,
          entityType: existing.entityType,
          fieldKey: existing.fieldKey,
          change: "updated",
          at: now,
          by: "You",
        },
        ...ruleAudit,
      ];
      recomputeStatus = { state: "complete", lastChangedAt: now };
      return { ...existing };
    }
    const created: ResolutionRule = {
      id: `rule-${Date.now()}`,
      entityType: input.entityType,
      fieldKey: input.fieldKey,
      strategy: input.strategy,
      sourcePriority: input.sourcePriority,
      customLogic: input.customLogic,
      updatedAt: now,
      updatedBy: "You",
    };
    rules = [...rules, created];
    ruleAudit = [
      {
        ruleId: created.id,
        entityType: created.entityType,
        fieldKey: created.fieldKey,
        change: "created",
        at: now,
        by: "You",
      },
      ...ruleAudit,
    ];
    recomputeStatus = { state: "complete", lastChangedAt: now };
    return created;
  },

  async deleteResolutionRule(id: string): Promise<void> {
    await delay(400);
    const existing = rules.find((r) => r.id === id);
    if (!existing) throw new ApiError("VALIDATION_FAILED", `No rule with id ${id}.`);
    rules = rules.filter((r) => r.id !== id);
    ruleAudit = [
      {
        ruleId: null,
        entityType: existing.entityType,
        fieldKey: existing.fieldKey,
        change: "deleted",
        at: new Date().toISOString(),
        by: "You",
      },
      ...ruleAudit,
    ];
  },

  async getRecomputeStatus(): Promise<RecomputeStatus> {
    await delay(300);
    return { ...recomputeStatus };
  },

  async listRuleAudit(): Promise<RuleAuditEntry[]> {
    await delay(300);
    return [...ruleAudit];
  },

  async listProducts(): Promise<string[]> {
    await delay(300);
    return [...DEMO_PRODUCTS];
  },

  async listDailySales(): Promise<DailySales[]> {
    await delay(300);
    return [
      { date: "2026-10-05", source: "CTB", totalSales: 10865.72, gst: 985.44, net: 9880.28 },
      { date: "2026-10-04", source: "CTB", totalSales: 29605.13, gst: 2689.54, net: 26915.6 },
      { date: "2026-10-03", source: "CTB", totalSales: 43618.92, gst: 3960.39, net: 39658.53 },
    ];
  },

  async getLatestSales(): Promise<LatestSales> {
    await delay(300);
    return { date: "2026-10-05", total: 10865.72, authoritativeSource: "agreed" };
  },

  async getReservationSummary(date: string): Promise<import("./types").ReservationSummary | undefined> {
    await delay(300);
    return {
      date,
      bookings: 96,
      attended: 82,
      covers: 314,
      cancelled: 5,
      noShows: 9,
      walkIns: 12,
      avgPartySize: 3.83,
      noShowRate: 0.0938,
      bookingToCoverConversion: 0.8542,
    };
  },

  async getTopSellers(): Promise<TopSeller[]> {
    await delay(300);
    return [
      { name: "pint carlton draught", quantitySold: 493, amount: 7904.25, hasConflict: false },
      { name: "chicken schnitzel", quantitySold: 211, amount: 5591.5, hasConflict: false },
      { name: "garlic aioli", quantitySold: 150, amount: 380.88, hasConflict: true },
      { name: "parma", quantitySold: 132, amount: 3696, hasConflict: false },
      { name: "house red", quantitySold: 98, amount: 1078, hasConflict: false },
    ];
  },

  async getSalesTrend(): Promise<SalesTrendPoint[]> {
    await delay(300);
    return [
      { date: "2026-10-01", total: 9582.11 },
      { date: "2026-10-02", total: 33909.35 },
      { date: "2026-10-03", total: 43618.92 },
      { date: "2026-10-04", total: 29605.13 },
      { date: "2026-10-05", total: 10865.72 },
    ];
  },

  async listDashboards(): Promise<SavedDashboardSummary[]> {
    await delay(300);
    return savedDashboards.map((d) => ({
      id: d.id,
      title: d.title,
      updatedAt: d.updatedAt,
      description: d.description,
      createdBy: d.createdBy,
      pinned: d.pinned,
      visibility: d.visibility,
    }));
  },

  async getDashboard(id: string): Promise<DashboardDocument> {
    await delay(300);
    const found = savedDashboards.find((d) => d.id === id);
    if (!found) throw new ApiError("VALIDATION_FAILED", `No dashboard with id ${id}.`);
    return found;
  },

  async saveDashboard(input: SaveDashboardInput): Promise<DashboardDocument> {
    await delay(400);
    const now = new Date().toISOString();
    const created: DashboardDocument = {
      id: `dash-${Date.now()}`,
      schemaVersion: 2,
      title: input.title,
      description: input.description ?? null,
      layout: input.layout ?? "grid",
      widgets: input.widgets,
      filters: input.filters ?? emptyFilters(),
      visibility: input.visibility ?? "PRIVATE",
      pinned: false,
      createdBy: "You",
      createdAt: now,
      updatedAt: now,
    };
    savedDashboards = [created, ...savedDashboards];
    return created;
  },

  async updateDashboard(id: string, input: SaveDashboardInput): Promise<DashboardDocument> {
    await delay(400);
    const found = savedDashboards.find((d) => d.id === id);
    if (!found) throw new ApiError("VALIDATION_FAILED", `No dashboard with id ${id}.`);
    found.title = input.title;
    found.description = input.description ?? null;
    found.layout = input.layout ?? "grid";
    found.widgets = input.widgets;
    found.filters = input.filters ?? emptyFilters();
    found.visibility = input.visibility ?? "PRIVATE";
    found.updatedAt = new Date().toISOString();
    return { ...found };
  },

  async deleteDashboard(id: string): Promise<void> {
    await delay(300);
    savedDashboards = savedDashboards.filter((d) => d.id !== id);
  },

  async renderDashboard(id: string): Promise<RenderedWidget[]> {
    await delay(300);
    const found = savedDashboards.find((d) => d.id === id);
    if (!found) throw new ApiError("VALIDATION_FAILED", `No dashboard with id ${id}.`);
    // Demo renders each saved widget as the fixed daily-sales time-series spec (the demo has no
    // real metric query runtime), so the shape mirrors the live RenderedWidget contract.
    return found.widgets.map((w) => ({
      widgetId: w.id,
      widget: demoWidgetSpec(),
      deniedResource: null,
    }));
  },

  async listDashboardTemplates(): Promise<DashboardTemplate[]> {
    await delay(300);
    return demoTemplates;
  },

  async createDashboardFromTemplate(templateId: string): Promise<DashboardDocument> {
    await delay(400);
    const template = demoTemplates.find((t) => t.id === templateId);
    if (!template) throw new ApiError("VALIDATION_FAILED", `Unknown template: ${templateId}`);
    const now = new Date().toISOString();
    const created: DashboardDocument = {
      id: `dash-${Date.now()}`,
      schemaVersion: 2,
      title: template.name,
      description: template.description,
      layout: "grid",
      widgets: template.widgets,
      filters: emptyFilters(),
      visibility: "PRIVATE",
      pinned: false,
      createdBy: "You",
      createdAt: now,
      updatedAt: now,
    };
    savedDashboards = [created, ...savedDashboards];
    return created;
  },

  async listDashboardRevisions(id: string): Promise<DashboardRevisionSummary[]> {
    await delay(300);
    const found = savedDashboards.find((d) => d.id === id);
    if (!found) throw new ApiError("VALIDATION_FAILED", `No dashboard with id ${id}.`);
    return [{ revision: 1, createdBy: found.createdBy, createdAt: found.createdAt }];
  },

  async restoreDashboardRevision(id: string, revision: number): Promise<DashboardDocument> {
    await delay(400);
    const found = savedDashboards.find((d) => d.id === id);
    if (!found) throw new ApiError("VALIDATION_FAILED", `No dashboard with id ${id}.`);
    if (revision !== 1) {
      throw new ApiError("VALIDATION_FAILED", `No revision ${revision} for dashboard ${id}.`);
    }
    return { ...found, updatedAt: new Date().toISOString() };
  },

  async toggleDashboardPin(id: string): Promise<DashboardDocument> {
    await delay(300);
    const found = savedDashboards.find((d) => d.id === id);
    if (!found) throw new ApiError("VALIDATION_FAILED", `No dashboard with id ${id}.`);
    found.pinned = !found.pinned;
    return { ...found };
  },

  async getDashboardSharing(id: string): Promise<DashboardSharing> {
    await delay(300);
    const found = savedDashboards.find((d) => d.id === id);
    if (!found) throw new ApiError("VALIDATION_FAILED", `No dashboard with id ${id}.`);
    return { visibility: found.visibility, roles: [] };
  },

  async setDashboardSharing(id: string, sharing: DashboardSharing): Promise<DashboardSharing> {
    await delay(400);
    const found = savedDashboards.find((d) => d.id === id);
    if (!found) throw new ApiError("VALIDATION_FAILED", `No dashboard with id ${id}.`);
    found.visibility = sharing.visibility;
    return { ...sharing };
  },

  async listThreads(): Promise<ConversationThreadSummary[]> {
    await delay(300);
    return [...demoThreads];
  },

  async getThread(id: string): Promise<ConversationThreadView> {
    await delay(300);
    const summary = demoThreads.find((t) => t.id === id);
    if (!summary) throw new ApiError("VALIDATION_FAILED", `No thread with id ${id}.`);
    return { id, title: summary.title, messages: demoThreadMessages[id] ?? [] };
  },

  async renameThread(id: string, title: string): Promise<ConversationThreadView> {
    await delay(400);
    const summary = demoThreads.find((t) => t.id === id);
    if (!summary) throw new ApiError("VALIDATION_FAILED", `No thread with id ${id}.`);
    summary.title = title;
    summary.updatedAt = new Date().toISOString();
    return { id, title, messages: demoThreadMessages[id] ?? [] };
  },

  async deleteThread(id: string): Promise<void> {
    await delay(300);
    demoThreads = demoThreads.filter((t) => t.id !== id);
    delete demoThreadMessages[id];
  },
};

let savedDashboards: DashboardDocument[] = [];

const emptyFilters = (): DashboardFilters => ({ dateRange: null, comparison: null, dimensions: [] });

/** The fixed spec the demo render endpoint returns for every saved widget. */
function demoWidgetSpec(): WidgetSpec {
  return {
    schemaVersion: 2,
    id: "demo-sales",
    type: "time-series",
    title: "Daily sales",
    description: "Resolved gross sales per day.",
    series: [
      {
        key: "grossSales",
        label: "Gross sales",
        points: [
          { x: "2026-10-01", y: 9582.11 },
          { x: "2026-10-02", y: 33909.35 },
          { x: "2026-10-03", y: 43618.92 },
          { x: "2026-10-04", y: 29605.13 },
          { x: "2026-10-05", y: 10865.72 },
        ],
      },
    ],
    yFormat: "currency",
  };
}

function demoQuery(metric: string): MetricQuery {
  const today = new Date();
  const from = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate() - 6))
    .toISOString()
    .slice(0, 10);
  const to = today.toISOString().slice(0, 10);
  return {
    metric,
    range: { from, to, calendar: "CALENDAR" },
    grain: "DAY",
    dimensions: [],
    comparison: null,
  };
}

function demoTsWidget(id: string, metric: string): SavedWidget {
  return { id, renderType: "time-series", queries: [demoQuery(metric)], layout: { w: 6, h: 2 } };
}

function demoTableWidget(id: string, metrics: string[]): SavedWidget {
  return { id, renderType: "table", queries: metrics.map(demoQuery), layout: { w: 12, h: 2 } };
}

const demoTemplates: DashboardTemplate[] = [
  {
    id: "daily",
    name: "Daily Management",
    description: "Today's sales, covers, labour and conflicts.",
    widgets: [
      demoTsWidget("w1", "sales.gross"),
      demoTsWidget("w2", "reservations.covers"),
      demoTsWidget("w3", "labour.cost"),
    ],
  },
  {
    id: "weekly-foh",
    name: "Weekly — Front of House",
    description: "Covers and reservations for the week.",
    widgets: [demoTsWidget("w1", "reservations.covers")],
  },
  {
    id: "sales",
    name: "Sales Performance",
    description: "Gross and net sales trend.",
    widgets: [demoTsWidget("w1", "sales.gross"), demoTsWidget("w2", "sales.net")],
  },
  {
    id: "labour",
    name: "Labour",
    description: "Scheduled/actual hours, cost and variance.",
    widgets: [
      demoTableWidget("w1", [
        "labour.scheduled_hours",
        "labour.actual_hours",
        "labour.cost",
        "labour.hours_variance",
      ]),
    ],
  },
];
