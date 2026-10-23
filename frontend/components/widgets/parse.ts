import { z } from "zod";
import { ApiError } from "@/lib/api/errors";
import type { WidgetSpec } from "./types";

const base = {
  schemaVersion: z.literal(2), id: z.string(), title: z.string(), description: z.string().nullable().optional(),
  query: z.object({ tool: z.string(), input: z.record(z.string(), z.unknown()) }).nullable().optional(),
};
const series = z.array(z.object({ key: z.string(), label: z.string(), points: z.array(z.object({ x: z.string(), y: z.number().finite().nullable() })) }));
const cell = z.union([z.string(), z.number().finite(), z.boolean(), z.null()]);
export const widgetSchema = z.discriminatedUnion("type", [
  z.object({ ...base, type: z.literal("stat"), value: z.number().finite().nullable(), format: z.string().nullable().optional(), hint: z.string().nullable().optional() }),
  z.object({ ...base, type: z.literal("time-series"), series, yFormat: z.string().nullable().optional() }),
  z.object({ ...base, type: z.literal("bar-chart"), series, yFormat: z.string().nullable().optional(), stacked: z.boolean().optional() }),
  z.object({ ...base, type: z.literal("table"), columns: z.array(z.object({ key: z.string(), label: z.string(), format: z.string().nullable().optional() })), rows: z.array(z.record(z.string(), cell)) }),
  z.object({ ...base, type: z.literal("ranked-list"), items: z.array(z.object({ label: z.string(), primary: z.string().nullable().optional(), secondary: z.string().nullable().optional(), badge: z.string().nullable().optional() })) }),
]);

/** Single-widget callers can render an explicit invalid-widget state. */
export function parseWidgetSpec(value: unknown): WidgetSpec | null {
  const result = widgetSchema.safeParse(value);
  return result.success ? result.data : null;
}

/** Reject the complete response rather than silently omitting business data. */
export function parseWidgetSpecs(value: unknown): WidgetSpec[] {
  const result = z.array(widgetSchema).safeParse(value);
  if (!result.success) throw new ApiError("INVALID_WIDGET", "The response contains an invalid widget.", undefined, undefined, {
    kind: "protocol", diagnostics: result.error.issues.map(issue => ({ path: issue.path.map(String), code: issue.code })),
  });
  return result.data;
}
