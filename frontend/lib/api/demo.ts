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
  DataPage,
  EntityDescriptor,
  GenericRow,
  RawRecordDetail,
  RawRecordFilter,
  RawRecordSummary,
  DashboardBootstrap,
  DashboardDocument,
  DashboardFilters,
  DashboardRevisionSummary,
  DashboardSharing,
  DashboardSummary,
  DashboardTemplate,
  LatestSales,
  DailyCovers,
  InventorySummary,
  InvoiceIngestFlag,
  InvoiceGraphNode,
  LabourSummary,
  LineGraphNode,
  Provenance,
  MetricQuery,
  OverrideResult,
  ProductOverrideInput,
  RecomputeStatus,
  ReconciliationException,
  ReconciliationRecord,
  ReconciliationAuditEntry,
  RenderedWidget,
  ResolutionRule,
  RuleAuditEntry,
  SalesTrendPoint,
  SupplierGraphNode,
  SaveDashboardInput,
  SaveOverrideInput,
  SaveResolutionRuleInput,
  SavedDashboardSummary,
  SavedWidget,
  TopSeller,
  TrustSummary,
  PaymentRow,
  DeletedSaleRow,
  SaleItemRow,
  PaymentMix,
  DeletedSaleDay,
  SaleItemMix,
  PaymentFilter,
  DeletedSaleFilter,
  SaleItemFilter,
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
  { source: "LIGHTSPEED", connectorName: "lightspeed-scrape", lastRunAt: "2026-09-20T09:05:00Z", status: "success", failureCount: 0, runnable: false },
  { source: "CTB", connectorName: "ctb-export", lastRunAt: "2026-09-20T09:00:00Z", status: "partial", failureCount: 1, runnable: true },
  { source: "DEPUTY", connectorName: "deputy-api", lastRunAt: "2026-09-19T22:30:00Z", status: "failed", failureCount: 2, runnable: false },
  { source: "OPENTABLE", connectorName: "opentable-guestcenter", lastRunAt: null, status: "never_run", failureCount: 0, runnable: false },
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
    sourcePriority: ["CTB", "LIGHTSPEED"],
    updatedAt: "2026-09-29T18:00:00Z",
    updatedBy: "Stirling Donaldson",
  },
  {
    id: "rule-2",
    entityType: "product_sales",
    fieldKey: "garlic aioli",
    strategy: "priority",
    sourcePriority: ["LIGHTSPEED", "CTB"],
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

// Combined rule + override history surfaced on the reconciliation screen. Demo fixtures mirror
// the live `/api/reconciliation/audit` shape (rule created/updated/deleted, override set/removed).
let reconciliationAudit: ReconciliationAuditEntry[] = [
  {
    kind: "rule",
    change: "created",
    entityType: "daily_sales",
    fieldKey: "daily_sales",
    source: null,
    reason: null,
    by: "Stirling Donaldson",
    at: "2026-09-29T18:00:00Z",
  },
  {
    kind: "override",
    change: "set",
    entityType: "daily_sales",
    fieldKey: "daily_sales",
    source: "Cooking the Books",
    reason: "Till reconciliation matched the bank.",
    by: "Stirling Donaldson",
    at: "2026-09-30T08:15:00Z",
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

// Data-explorer demo fixtures: a couple of raw records, the descriptor lists, and a few generic rows
// so the /data screen renders without a backend.
const demoRawRecords: RawRecordSummary[] = [
  {
    id: "raw-1",
    sourceSystem: "CTB",
    fetcherIdentity: "ctb-invoices-ajax",
    fetchMethod: "API",
    contentType: "application/json",
    characterEncoding: "UTF-8",
    fetchedAt: "2026-10-07T12:00:00Z",
    byteLength: 312,
    sha256: "0".repeat(64),
  },
  {
    id: "raw-2",
    sourceSystem: "LIGHTSPEED",
    fetcherIdentity: "lightspeed-insights",
    fetchMethod: "FILE_EXPORT",
    contentType: "application/json",
    characterEncoding: "UTF-8",
    fetchedAt: "2026-10-07T11:30:00Z",
    byteLength: 1540,
    sha256: "1".repeat(64),
  },
];

demoRawRecords.push({
  id: "raw-3",
  sourceSystem: "DEPUTY",
  fetcherIdentity: "deputy-timesheets",
  fetchMethod: "API",
  contentType: "application/json",
  characterEncoding: "UTF-8",
  fetchedAt: "2026-10-07T10:15:00Z",
  byteLength: 2210,
  sha256: "2".repeat(64),
});

const demoCanonicalEntities: EntityDescriptor[] = [
  { id: "daily_sales", label: "Daily sales", placeholder: false },
  { id: "product_sales", label: "Product sales", placeholder: false },
  { id: "invoice", label: "Invoices", placeholder: false },
  { id: "shift", label: "Shifts", placeholder: true },
];

const demoResolvedDomains: EntityDescriptor[] = [
  { id: "resolved_daily_sales", label: "Daily sales", placeholder: false },
  { id: "resolved_inventory_day", label: "Inventory day", placeholder: false },
];

const demoCanonicalRows: Record<string, GenericRow[]> = {
  daily_sales: [
    {
      id: "daily-1",
      columns: {
        source_system: "CTB",
        source_record_ref: "2026-10-05",
        trading_date: "2026-10-05",
        total_sales: "10865.7200",
      },
    },
  ],
  invoice: [
    {
      id: "invoice-1",
      columns: {
        source_system: "CTB",
        source_record_ref: "INV-1",
        invoice_number: "INV-1",
        supplier_name: "Acme Supplies",
      },
    },
  ],
};

const demoResolvedRows: Record<string, GenericRow[]> = {
  resolved_daily_sales: [
    {
      id: "2026-10-05",
      columns: {
        trading_date: "2026-10-05",
        total_sales: "10865.7200",
        resolution_type: "agreed",
        authoritative_source: "Lightspeed",
      },
    },
  ],
};

// Sales-detail demo fixtures: a few payments across two dates and three types, two deleted
// orders, and a few sale line items across two categories, so the /sales-detail screen renders
// without a backend. The list methods filter these the way the live endpoints do.
const demoPayments: PaymentRow[] = [
  {
    tradingDate: "2026-10-04",
    saleNumber: "SALE-1001",
    paymentTypeName: "Tyro",
    paymentTypeCode: "4",
    paymentSourceType: "EFTPOS",
    lspayPaymentMode: "Tyro",
    clearingAccount: "Westpac 123",
    amount: 45.5,
    tip: 2.0,
    tendered: 47.5,
    surcharge: 0,
    paymentCount: 1,
    tipCount: 1,
    reconciled: "Yes",
    registerCode: "REG-1",
    registerName: "Main Bar",
    staffName: "Stirling Donaldson",
    staffCode: "S-1",
    siteId: "SITE-1",
    customerName: null,
  },
  {
    tradingDate: "2026-10-04",
    saleNumber: "SALE-1001",
    paymentTypeName: "Cash",
    paymentTypeCode: "0",
    paymentSourceType: "CASH",
    lspayPaymentMode: null,
    clearingAccount: "Till",
    amount: 12.0,
    tip: 0,
    tendered: 20.0,
    surcharge: 0,
    paymentCount: 1,
    tipCount: 0,
    reconciled: "Yes",
    registerCode: "REG-1",
    registerName: "Main Bar",
    staffName: "Stirling Donaldson",
    staffCode: "S-1",
    siteId: "SITE-1",
    customerName: null,
  },
  {
    tradingDate: "2026-10-05",
    saleNumber: "SALE-1002",
    paymentTypeName: "Tyro",
    paymentTypeCode: "4",
    paymentSourceType: "EFTPOS",
    lspayPaymentMode: "Tyro",
    clearingAccount: "Westpac 123",
    amount: 78.9,
    tip: 3.5,
    tendered: 82.4,
    surcharge: 0,
    paymentCount: 1,
    tipCount: 1,
    reconciled: "Yes",
    registerCode: "REG-2",
    registerName: "Bistro",
    staffName: "Alex Smith",
    staffCode: "S-2",
    siteId: "SITE-1",
    customerName: "Table 12",
  },
  {
    tradingDate: "2026-10-05",
    saleNumber: "SALE-1002",
    paymentTypeName: "Visa",
    paymentTypeCode: "VISA",
    paymentSourceType: "EFTPOS",
    lspayPaymentMode: null,
    clearingAccount: "Westpac 123",
    amount: 30.0,
    tip: 0,
    tendered: 30.0,
    surcharge: 0.45,
    paymentCount: 1,
    tipCount: 0,
    reconciled: "Yes",
    registerCode: "REG-2",
    registerName: "Bistro",
    staffName: "Alex Smith",
    staffCode: "S-2",
    siteId: "SITE-1",
    customerName: "Table 12",
  },
];

const demoDeletedSales: DeletedSaleRow[] = [
  {
    tradingDate: "2026-10-04",
    saleNumber: "SALE-999",
    orderType: "Dine-in",
    note: "Voided — wrong table",
    totalIncTax: 120.0,
    totalExTax: 109.09,
    totalTax: 10.91,
    totalCost: 40.0,
    openedRegisterName: "Main Bar",
    deletedRegisterName: "Main Bar",
    staffName: "Stirling Donaldson",
    deletedByStaffName: "Stirling Donaldson",
    tableNumber: "8",
    siteId: "SITE-1",
    customerName: null,
  },
  {
    tradingDate: "2026-10-05",
    saleNumber: "SALE-998",
    orderType: "Takeaway",
    note: null,
    totalIncTax: 65.5,
    totalExTax: 59.55,
    totalTax: 5.95,
    totalCost: null,
    openedRegisterName: "Bistro",
    deletedRegisterName: "Bistro",
    staffName: "Alex Smith",
    deletedByStaffName: "Alex Smith",
    tableNumber: null,
    siteId: "SITE-1",
    customerName: "Walk-up",
  },
];

const demoSaleItems: SaleItemRow[] = [
  {
    tradingDate: "2026-10-04",
    saleNumber: "SALE-1001",
    receiptLineId: "LINE-1",
    itemName: "pint carlton draught",
    productNumber: "P-100",
    sku: "SKU-100",
    categoryName: "Beer",
    quantitySold: 2,
    amount: 24.0,
    soldPriceIncTax: 12.0,
    totalTax: 2.18,
    costIncTax: 6.4,
    orderType: "Dine-in",
    saleType: "Sale",
    staffName: "Stirling Donaldson",
    registerName: "Main Bar",
    tableNumber: "8",
  },
  {
    tradingDate: "2026-10-04",
    saleNumber: "SALE-1001",
    receiptLineId: "LINE-2",
    itemName: "house red",
    productNumber: "P-101",
    sku: "SKU-101",
    categoryName: "Beer",
    quantitySold: 1,
    amount: 11.0,
    soldPriceIncTax: 11.0,
    totalTax: 1.0,
    costIncTax: 4.0,
    orderType: "Dine-in",
    saleType: "Sale",
    staffName: "Stirling Donaldson",
    registerName: "Main Bar",
    tableNumber: "8",
  },
  {
    tradingDate: "2026-10-05",
    saleNumber: "SALE-1002",
    receiptLineId: "LINE-3",
    itemName: "chicken schnitzel",
    productNumber: "P-200",
    sku: "SKU-200",
    categoryName: "Food",
    quantitySold: 1,
    amount: 26.5,
    soldPriceIncTax: 26.5,
    totalTax: 2.41,
    costIncTax: 9.8,
    orderType: "Dine-in",
    saleType: "Sale",
    staffName: "Alex Smith",
    registerName: "Bistro",
    tableNumber: "12",
  },
  {
    tradingDate: "2026-10-05",
    saleNumber: "SALE-1002",
    receiptLineId: "LINE-4",
    itemName: "parma",
    productNumber: "P-201",
    sku: "SKU-201",
    categoryName: "Food",
    quantitySold: 1,
    amount: 28.0,
    soldPriceIncTax: 28.0,
    totalTax: 2.55,
    costIncTax: 11.2,
    orderType: "Dine-in",
    saleType: "Sale",
    staffName: "Alex Smith",
    registerName: "Bistro",
    tableNumber: "12",
  },
];

const demoPaymentMix: PaymentMix[] = [
  { tradingDate: "2026-10-04", paymentTypeName: "Tyro", amount: 45.5, tip: 2.0, count: 1, hasConflict: false },
  { tradingDate: "2026-10-04", paymentTypeName: "Cash", amount: 12.0, tip: 0, count: 1, hasConflict: false },
  { tradingDate: "2026-10-05", paymentTypeName: "Tyro", amount: 78.9, tip: 3.5, count: 1, hasConflict: false },
  { tradingDate: "2026-10-05", paymentTypeName: "Visa", amount: 30.0, tip: 0, count: 1, hasConflict: false },
];

const demoDeletedSaleTotals: DeletedSaleDay[] = [
  { tradingDate: "2026-10-04", count: 1, totalIncTax: 120.0, totalTax: 10.91, hasConflict: false },
  { tradingDate: "2026-10-05", count: 1, totalIncTax: 65.5, totalTax: 5.95, hasConflict: false },
];

const demoSaleItemMix: SaleItemMix[] = [
  { tradingDate: "2026-10-04", categoryName: "Beer", quantity: 3, amount: 35.0, hasConflict: false },
  { tradingDate: "2026-10-05", categoryName: "Food", quantity: 2, amount: 54.5, hasConflict: false },
];

/** Inclusive ISO-date range check (string comparison is safe for YYYY-MM-DD). */
function inDateRange(date: string, from?: string, to?: string): boolean {
  if (from && date < from) return false;
  if (to && date > to) return false;
  return true;
}

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
    field.overrideReason = input.reason ?? null;
    field.overrideActor = "You";
    field.overrideAt = new Date().toISOString();
    reconciliationAudit = [
      {
        kind: "override",
        change: "set",
        entityType: "daily_sales",
        fieldKey: input.field,
        source: input.source,
        reason: input.reason ?? null,
        by: "You",
        at: new Date().toISOString(),
      },
      ...reconciliationAudit,
    ];
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
    record.fields[0].overrideReason = input.reason ?? null;
    record.fields[0].overrideActor = "You";
    record.fields[0].overrideAt = new Date().toISOString();
    productExceptions = productExceptions.filter((e) => e.recordId !== input.product);
    reconciliationAudit = [
      {
        kind: "override",
        change: "set",
        entityType: "product_sales",
        fieldKey: input.product,
        source: input.source,
        reason: input.reason ?? null,
        by: "You",
        at: new Date().toISOString(),
      },
      ...reconciliationAudit,
    ];
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
      reconciliationAudit = [
        {
          kind: "rule",
          change: "updated",
          entityType: existing.entityType,
          fieldKey: existing.fieldKey,
          source: null,
          reason: null,
          by: "You",
          at: now,
        },
        ...reconciliationAudit,
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
    reconciliationAudit = [
      {
        kind: "rule",
        change: "created",
        entityType: created.entityType,
        fieldKey: created.fieldKey,
        source: null,
        reason: null,
        by: "You",
        at: now,
      },
      ...reconciliationAudit,
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
    reconciliationAudit = [
      {
        kind: "rule",
        change: "deleted",
        entityType: existing.entityType,
        fieldKey: existing.fieldKey,
        source: null,
        reason: null,
        by: "You",
        at: new Date().toISOString(),
      },
      ...reconciliationAudit,
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

  async listReconciliationAudit(): Promise<ReconciliationAuditEntry[]> {
    await delay(300);
    return [...reconciliationAudit];
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

  async getInventoryLines(
    from: string,
    to: string,
  ): Promise<import("./types").InventoryLineBreakdown> {
    await delay(300);
    return {
      from,
      to,
      unitCostByUom: [
        { uom: "KG", lineTotal: 4820.5, quantity: 310, unitCost: 15.55 },
        { uom: "EACH", lineTotal: 1240.0, quantity: 200, unitCost: 6.2 },
        { uom: null, lineTotal: 890.0, quantity: 120, unitCost: 7.42 },
      ],
      cogsBySupplier: [
        { supplier: "Paramount Liquor", lineTotal: 9240.0, wetAmount: 610.5 },
        { supplier: "Oranges & Lemons", lineTotal: 4820.5, wetAmount: 0 },
        { supplier: "Sealane Beverages", lineTotal: 1240.0, wetAmount: 0 },
      ],
    };
  },

  async getInventorySummary(from: string, to: string): Promise<InventorySummary> {
    await delay(300);
    return { from, to, purchases: 6950.5, wastage: 412.8, foodCostPercent: 0.2914 };
  },

  async getInvoiceGraphSuppliers(from: string, to: string): Promise<SupplierGraphNode[]> {
    await delay(300);
    void from;
    void to;
    return [
      { name: "Paramount Liquor", invoiceCount: 214, totalSpend: 9240.0 },
      { name: "Oranges & Lemons", invoiceCount: 98, totalSpend: 4820.5 },
      { name: "Sealane Beverages", invoiceCount: 51, totalSpend: 1240.0 },
      { name: "Unknown", invoiceCount: 0, totalSpend: 12.4 },
    ];
  },

  async getInvoiceGraphInvoices(
    supplier: string,
    from: string,
    to: string,
  ): Promise<InvoiceGraphNode[]> {
    await delay(300);
    void supplier;
    void from;
    void to;
    return [
      {
        invoiceNumber: "INV-1042",
        invoiceDate: "2026-09-28",
        totalAmount: 1420.15,
        purchaseNumber: "PO-88",
        pdfFilename: "inv-1042.pdf",
      },
      {
        invoiceNumber: "INV-1091",
        invoiceDate: "2026-09-21",
        totalAmount: 980.4,
        purchaseNumber: "PO-91",
        pdfFilename: "inv-1091.pdf",
      },
    ];
  },

  async listInvoiceFlags(): Promise<InvoiceIngestFlag[]> {
    await delay(300);
    return [
      { flagType: "PDF_ONLY_LINE", invoiceNumber: "INV-1042", pdfFilename: "inv-1042.pdf", stockCode: "CB-1", detail: "PDF line has no matching CSV line", occurredAt: "2026-10-08T18:30:00Z" },
      { flagType: "MISSING_PDF", invoiceNumber: "INV-1091", pdfFilename: null, stockCode: null, detail: "no PDF filename in CSV", occurredAt: "2026-10-09T00:24:00Z" },
    ];
  },

  async getInvoiceGraphLines(invoiceNumber: string): Promise<LineGraphNode[]> {
    await delay(300);
    void invoiceNumber;
    return [
      { productNameKey: "chicken breast", stockCode: "CB-1", quantity: 4, unitCost: 12.5, lineTotal: 50.0, uom: "CTN" },
      { productNameKey: "beef mince", stockCode: "BM-2", quantity: 10, unitCost: 9.0, lineTotal: 90.0, uom: "KG" },
    ];
  },

  async getLabourSummary(from: string, to: string): Promise<LabourSummary> {
    await delay(300);
    return {
      from,
      to,
      scheduledHours: 1184,
      actualHours: 1231.5,
      labourCost: 41872.4,
      variance: 47.5,
      hoursPerCover: 0.29,
      labourCostPerCover: 9.86,
      fohLabourCostPercent: 0.1412,
      bohLabourCostPercent: 0.1637,
    };
  },

  async listDailyCovers(from: string, to: string): Promise<DailyCovers[]> {
    await delay(300);
    return demoCovers(from, to);
  },

  async getProvenance(metricId: string, date: string): Promise<Provenance> {
    await delay(300);
    return demoProvenance(metricId, date);
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
      trust: demoTrust(),
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

  async listRawRecords(
    _filter: RawRecordFilter,
    page: number,
    size: number,
  ): Promise<DataPage<RawRecordSummary>> {
    await delay(300);
    const items = _filter.source
      ? demoRawRecords.filter((r) => r.sourceSystem.toLowerCase() === _filter.source!.toLowerCase())
      : demoRawRecords;
    return { items, total: items.length, page, size };
  },

  async getRawRecord(id: string): Promise<RawRecordDetail> {
    await delay(300);
    const record = demoRawRecords.find((r) => r.id === id);
    if (!record) throw new ApiError("NOT_FOUND", `No raw record ${id}.`);
    return {
      summary: record,
      payload: '{"supplier": "Acme Supplies"}',
      isJson: true,
      sha256: record.sha256,
    };
  },

  async listCanonicalEntities(): Promise<EntityDescriptor[]> {
    await delay(300);
    return demoCanonicalEntities;
  },

  async listCanonicalRows(entity: string, page: number, size: number): Promise<DataPage<GenericRow>> {
    await delay(300);
    const items = demoCanonicalRows[entity] ?? [];
    return { items, total: items.length, page, size };
  },

  async listResolvedDomains(): Promise<EntityDescriptor[]> {
    await delay(300);
    return demoResolvedDomains;
  },

  async listResolvedRows(domain: string, page: number, size: number): Promise<DataPage<GenericRow>> {
    await delay(300);
    const items = demoResolvedRows[domain] ?? [];
    return { items, total: items.length, page, size };
  },

  async listPayments(
    filter: PaymentFilter,
    page: number,
    size: number,
  ): Promise<DataPage<PaymentRow>> {
    await delay(300);
    const items = demoPayments.filter(
      (p) =>
        inDateRange(p.tradingDate, filter.from, filter.to) &&
        (!filter.paymentType || p.paymentTypeName === filter.paymentType) &&
        (!filter.saleNumber || p.saleNumber === filter.saleNumber),
    );
    return { items, total: items.length, page, size };
  },

  async listDeletedSales(
    filter: DeletedSaleFilter,
    page: number,
    size: number,
  ): Promise<DataPage<DeletedSaleRow>> {
    await delay(300);
    const items = demoDeletedSales.filter(
      (d) =>
        inDateRange(d.tradingDate, filter.from, filter.to) &&
        (!filter.saleNumber || d.saleNumber === filter.saleNumber),
    );
    return { items, total: items.length, page, size };
  },

  async listSaleItems(
    filter: SaleItemFilter,
    page: number,
    size: number,
  ): Promise<DataPage<SaleItemRow>> {
    await delay(300);
    const items = demoSaleItems.filter(
      (s) =>
        inDateRange(s.tradingDate, filter.from, filter.to) &&
        (!filter.category || s.categoryName === filter.category) &&
        (!filter.saleNumber || s.saleNumber === filter.saleNumber),
    );
    return { items, total: items.length, page, size };
  },

  async getPaymentMix(from: string, to: string): Promise<PaymentMix[]> {
    await delay(300);
    return demoPaymentMix.filter((m) => inDateRange(m.tradingDate, from, to));
  },

  async getDeletedSaleTotals(from: string, to: string): Promise<DeletedSaleDay[]> {
    await delay(300);
    return demoDeletedSaleTotals.filter((d) => inDateRange(d.tradingDate, from, to));
  },

  async getSaleItemMix(from: string, to: string): Promise<SaleItemMix[]> {
    await delay(300);
    return demoSaleItemMix.filter((m) => inDateRange(m.tradingDate, from, to));
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

/** A representative trust summary for the demo render endpoint (verified, freshly resolved). */
function demoTrust(): TrustSummary {
  return {
    state: "VERIFIED",
    freshness: "FRESH",
    authoritativeSource: "Lightspeed",
    resolvedAt: new Date().toISOString(),
    lastIngestionAt: new Date().toISOString(),
    threshold: null,
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

/** A deterministic covers series for the demo: weekends busier, one conflicted day. */
function demoCovers(from: string, to: string): DailyCovers[] {
  const out: DailyCovers[] = [];
  const end = new Date(`${to}T00:00:00Z`);
  for (let d = new Date(`${from}T00:00:00Z`); d <= end; d.setUTCDate(d.getUTCDate() + 1)) {
    const dow = d.getUTCDay();
    const base = dow === 5 || dow === 6 ? 340 : dow === 0 ? 290 : 210;
    const date = d.toISOString().slice(0, 10);
    out.push({
      date,
      covers: base + ((d.getUTCDate() * 37) % 45),
      authoritativeSource: "OPENTABLE",
      hasConflict: d.getUTCDate() % 11 === 0,
    });
  }
  return out.reverse();
}

/**
 * Demo provenance: two sources that disagree on odd days (resolved by the source-priority rule)
 * and agree on even days, so the provenance graph shows both shapes.
 */
function demoProvenance(metricId: string, date: string): Provenance {
  const day = Number(date.slice(-2)) || 1;
  const base = metricId.startsWith("reservations") ? 300 + day : 10000 + day * 137.25;
  const conflict = day % 2 === 1;
  const primary = { sourceSystem: "LIGHTSPEED", value: base, recordedAt: `${date}T23:40:00Z` };
  const secondary = {
    sourceSystem: "CTB",
    value: conflict ? Math.round((base - 42.5) * 100) / 100 : base,
    recordedAt: `${date}T23:55:00Z`,
  };
  return {
    metric: metricId,
    date,
    resolvedValue: base,
    trust: {
      state: conflict ? "RESOLVED_BY_RULE" : "VERIFIED",
      freshness: "FRESH",
      authoritativeSource: "LIGHTSPEED",
      resolvedAt: `${date}T23:59:00Z`,
      lastIngestionAt: `${date}T23:55:00Z`,
      threshold: 0.5,
    },
    sources: [primary, secondary],
    resolution: conflict
      ? { kind: "rule", source: "LIGHTSPEED", reason: "priority", actor: "owner@goldys.example", at: `${date}T23:59:00Z` }
      : { kind: "agreed", source: "LIGHTSPEED", reason: "sources agree within tolerance", actor: null, at: `${date}T23:59:00Z` },
    rawRecordIds: ["raw-2", "raw-1"],
  };
}
