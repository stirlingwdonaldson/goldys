import { deriveExceptions } from "../reconciliation-logic";
import { ApiError } from "./errors";
import type {
  ActivityPoint,
  Api,
  ConnectorStatus,
  DailySales,
  DashboardSummary,
  LatestSales,
  OverrideResult,
  ProductOverrideInput,
  RecomputeStatus,
  ReconciliationException,
  ReconciliationRecord,
  ResolutionRule,
  RuleAuditEntry,
  SalesTrendPoint,
  SaveOverrideInput,
  SaveResolutionRuleInput,
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

export const demoApi: Api = {
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

  async getTopSellers(): Promise<TopSeller[]> {
    await delay(300);
    return [
      { name: "pint carlton draught", quantitySold: 493, amount: 7904.25 },
      { name: "chicken schnitzel", quantitySold: 211, amount: 5591.5 },
      { name: "garlic aioli", quantitySold: 150, amount: 380.88 },
      { name: "parma", quantitySold: 132, amount: 3696 },
      { name: "house red", quantitySold: 98, amount: 1078 },
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
};
