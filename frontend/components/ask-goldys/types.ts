import type { WidgetSpec } from "@/components/widgets/types";
import type { DashboardFilters, SavedWidget } from "@/lib/api/types";

export type { WidgetSpec };

export interface TraceEntry {
  tool: string;
  description: string;
  provenance: MetricProvenance[];
}

/**
 * Where a metric result came from. Mirrors the backend's `MetricProvenance` record: the metric
 * id, its catalogue definition version, the exact range/grain it was evaluated at, the source
 * domain, the freshness of the source data, the periods that had no resolved data, and the
 * calculation version.
 */
export interface MetricProvenance {
  metric: string;
  definitionVersion: string;
  range: { from: string; to: string; calendar: "TRADING" | "CALENDAR" };
  grain: "DAY" | "WEEK" | "MONTH";
  sourceDomain: string;
  dataFreshness: string;
  missingPeriods: string[];
  calculationVersion: string;
}

/**
 * A validated-but-not-persisted dashboard proposal attached to an answer. It carries only the
 * fields a user confirms before saving: title, description, filters and widgets. The id,
 * visibility, created-by and revision metadata are assigned by `saveDashboard` on confirm.
 */
export interface DashboardDraft {
  title: string;
  description: string | null;
  filters: DashboardFilters;
  widgets: SavedWidget[];
  /** Set when the backend has already persisted this draft; null/absent otherwise. */
  dashboardId?: string | null;
}

export interface AnswerPayload {
  widgets: WidgetSpec[];
  trace: TraceEntry[];
  asOf: string;
  notices: string[];
  draft?: DashboardDraft | null;
}

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}
