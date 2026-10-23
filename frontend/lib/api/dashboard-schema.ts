import { z } from "zod";
import { widgetSchema } from "@/components/widgets/parse";

export const rangeSchema = z.object({ from: z.iso.date(), to: z.iso.date(), calendar: z.enum(["TRADING", "CALENDAR"]) });
export const metricQuerySchema = z.object({
  metric: z.string().min(1), range: rangeSchema, grain: z.enum(["DAY", "WEEK", "MONTH", "TOTAL"]),
  dimensions: z.array(z.string()), comparison: z.string().nullable(),
});
export const savedWidgetSchema = z.object({
  id: z.string().min(1), renderType: z.enum(["stat", "time-series", "bar-chart", "table", "ranked-list", "query"]),
  queries: z.array(metricQuerySchema), layout: z.object({ w: z.number().int().min(1).max(12), h: z.number().int().positive() }),
  savedQueryId: z.string().nullable().optional(),
}).superRefine((widget, ctx) => {
  if (widget.renderType === "query" && !widget.savedQueryId) ctx.addIssue({ code: "custom", path: ["savedQueryId"], message: "Query widgets require a saved query" });
});
export const dashboardFiltersSchema = z.object({ dateRange: rangeSchema.nullable(), comparison: z.string().nullable(), dimensions: z.array(z.string()) });
export const trustSchema = z.object({
  state: z.enum(["VERIFIED", "RESOLVED_BY_RULE", "MANUALLY_OVERRIDDEN", "SINGLE_SOURCE", "CONFLICTED", "INCOMPLETE", "NOT_RECEIVED"]),
  freshness: z.enum(["FRESH", "STALE", "SOURCE_FAILURE", "UNKNOWN"]),
  authoritativeSource: z.string().nullable(), resolvedAt: z.string().nullable(), lastIngestionAt: z.string().nullable(), threshold: z.number().nullable(),
});
export const dashboardSchema = z.object({
  id: z.string(), schemaVersion: z.literal(2), title: z.string(), description: z.string().nullable(), layout: z.string(),
  widgets: z.array(savedWidgetSchema), filters: dashboardFiltersSchema, visibility: z.enum(["PRIVATE", "SHARED", "ORG_WIDE"]),
  pinned: z.boolean(), createdBy: z.string(), createdAt: z.string(), updatedAt: z.string(),
});
export const renderedWidgetsSchema = z.array(z.object({
  widgetId: z.string(), widget: widgetSchema.nullable(), deniedResource: z.string().nullable(),
  notices: z.array(z.string()).optional(), trust: trustSchema.nullable(),
}));
export const draftSchema = z.object({
  title: z.string(), description: z.string().nullable().default(null), widgets: z.array(savedWidgetSchema),
  filters: dashboardFiltersSchema.default({ dateRange: null, comparison: null, dimensions: [] }),
  dashboardId: z.string().nullable().default(null),
});
export const provenanceSchema = z.object({
  metric: z.string(), definitionVersion: z.string(), range: rangeSchema, grain: metricQuerySchema.shape.grain,
  sourceDomain: z.string(), dataFreshness: z.string().nullable(), missingPeriods: z.array(z.string()),
  calculationVersion: z.string(), trust: trustSchema.nullable().default(null),
  coverage: z.object({ from: z.iso.date(), to: z.iso.date(), observedDates: z.array(z.iso.date()), unresolvedDates: z.array(z.iso.date()), absentDates: z.array(z.iso.date()), expectedTradingPopulationKnown: z.boolean() }).nullable().optional(),
});
export const traceSchema = z.array(z.object({ tool: z.string(), description: z.string(), provenance: z.array(provenanceSchema) }));
export const answerSchema = z.object({
  widgets: z.array(widgetSchema), trace: traceSchema, asOf: z.string(), notices: z.array(z.string()), draft: draftSchema.nullable().optional(),
});
export const threadSchema = z.object({ id: z.string(), title: z.string(), messages: z.array(z.object({
  id: z.string(), threadId: z.string(), role: z.enum(["user", "assistant"]), content: z.string(), toolTrace: traceSchema, createdAt: z.string(),
})) });
