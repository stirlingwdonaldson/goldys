import type { WidgetSpec } from "@/components/widgets/types";
import type { DashboardFilters, SavedWidget } from "@/lib/api/types";

export type { WidgetSpec };

export interface TraceEntry {
  tool: string;
  description: string;
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
