import { deriveExceptions } from "../reconciliation-logic";
import { ApiError } from "./errors";
import type {
  Api,
  ConnectorStatus,
  DashboardSummary,
  ReconciliationException,
  ReconciliationRecord,
  SaveOverrideInput,
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
  { source: "Lightspeed", connectorName: "lightspeed-scrape", lastRunAt: "2026-09-20T09:05:00Z", status: "success", failureCount: 0 },
  { source: "Cooking the Books", connectorName: "ctb-export", lastRunAt: "2026-09-20T09:00:00Z", status: "partial", failureCount: 1 },
  { source: "Deputy", connectorName: "deputy-api", lastRunAt: "2026-09-19T22:30:00Z", status: "failed", failureCount: 2 },
  { source: "OpenTable", connectorName: "opentable-guestcenter", lastRunAt: null, status: "never_run", failureCount: 0 },
];

export const demoApi: Api = {
  async getDashboardSummary(): Promise<DashboardSummary> {
    await delay(400);
    return {
      ingestionCompleteness: 92,
      openConflicts: deriveExceptions(records).length,
      timeToDetectFailure: "42m avg",
      overrideUsage: { count: 3, period: "this week" },
    };
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

  async listConnectorStatuses(): Promise<ConnectorStatus[]> {
    await delay(400);
    return connectorStatuses;
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
};
