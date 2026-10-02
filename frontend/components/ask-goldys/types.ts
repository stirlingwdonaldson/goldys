export type WidgetType = "stat" | "table" | "line-chart" | "bar-chart";

export interface WidgetSpec {
  version: number;
  type: WidgetType;
  title: string;
  description?: string | null;
  data: Record<string, unknown>[];
}

export interface TraceEntry {
  tool: string;
  description: string;
}

export interface AnswerPayload {
  widgets: WidgetSpec[];
  trace: TraceEntry[];
  asOf: string;
  notices: string[];
}

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}
