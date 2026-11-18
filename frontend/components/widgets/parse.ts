import { z } from "zod";
import { ApiError } from "@/lib/api/errors";


const base = {
  schemaVersion: z.literal(2), id: z.string(), title: z.string(), description: z.string().nullable().optional(),
  query: z.object({ tool: z.string(), input: z.record(z.string(), z.unknown()) }).nullable().optional(),
};
const series = z.array(z.object({ key: z.string(), label: z.string(), points: z.array(z.object({ x: z.string(), y: z.number().finite().nullable() })) }));
const cell = z.union([z.string(), z.number().finite(), z.boolean(), z.null()]);
const chart = z.object({ ...base, type: z.literal("chart"),
  kind: z.enum(["LINE", "AREA", "BAR", "PIE", "DONUT", "RADAR", "RADIAL"]),
  series: series.min(1).max(6), yFormat: z.enum(["currency", "number", "percent"]),
  stacked: z.boolean(), horizontal: z.boolean(),
}).superRefine((value, context) => {
  const fail = (message: string) => context.addIssue({ code: "custom", message });
  if (value.stacked && !["AREA", "BAR"].includes(value.kind)) fail("Unsupported stacking");
  if (value.horizontal && value.kind !== "BAR") fail("Unsupported orientation");
  if (new Set(value.series.map(s => s.key)).size !== value.series.length) fail("Duplicate series keys");
  if (value.series.length === 0) return;
  const axis = value.series[0].points.map(p => p.x);
  if (axis.length < 2 || axis.length > 366 || new Set(axis).size !== axis.length) fail("Invalid chart categories");
  for (const item of value.series) {
    if (item.points.length !== axis.length || item.points.some((p, index) => p.x !== axis[index])) fail("Unaligned chart series");
  }
  const points = value.series.flatMap(s => s.points);
  if (!points.some(p => p.y !== null)) fail("No chart observations");
  const polar = ["PIE", "DONUT", "RADAR", "RADIAL"].includes(value.kind);
  if ((polar || value.stacked) && points.some(p => p.y === null || p.y < 0)) fail("Incomplete or negative angular/stacked data");
  if (polar && axis.length > 12) fail("Too many polar categories");
  if (value.kind === "RADAR" && axis.length < 3) fail("Radar requires three categories");
  if (["PIE", "DONUT", "RADIAL"].includes(value.kind) &&
      (value.series.length !== 1 || points.every(p => p.y === 0))) fail("Invalid angular series");
});
export const widgetSchema = z.discriminatedUnion("type", [
  chart,
  z.object({ ...base, type: z.literal("stat"), value: z.number().finite().nullable(), format: z.string().nullable().optional(), hint: z.string().nullable().optional() }),
  z.object({ ...base, type: z.literal("time-series"), series, yFormat: z.string().nullable().optional() }),
  z.object({ ...base, type: z.literal("bar-chart"), series, yFormat: z.string().nullable().optional(), stacked: z.boolean().optional() }),
  z.object({ ...base, type: z.literal("table"), columns: z.array(z.object({ key: z.string(), label: z.string(), format: z.string().nullable().optional() })), rows: z.array(z.record(z.string(), cell)) }),
  z.object({ ...base, type: z.literal("ranked-list"), items: z.array(z.object({ label: z.string(), primary: z.string().nullable().optional(), secondary: z.string().nullable().optional(), badge: z.string().nullable().optional() })) }),
]);

export type WidgetSpec = z.infer<typeof widgetSchema>;

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
