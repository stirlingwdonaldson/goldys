import type { WidgetSpec } from "@/components/widgets/types";

export type { WidgetSpec };

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
