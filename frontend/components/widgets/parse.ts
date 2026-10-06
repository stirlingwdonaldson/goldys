import type {
  Column,
  RankedItem,
  Series,
  SeriesPoint,
  WidgetQuery,
  WidgetSpec,
} from "./types";

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

function strOrNull(v: unknown): string | null {
  return typeof v === "string" ? v : null;
}

function parsePoint(v: unknown): SeriesPoint | null {
  if (!isRecord(v) || typeof v.x !== "string") return null;
  return { x: v.x, y: typeof v.y === "number" ? v.y : null };
}

function parseSeries(v: unknown): Series[] {
  if (!Array.isArray(v)) return [];
  const out: Series[] = [];
  for (const s of v) {
    if (!isRecord(s) || typeof s.key !== "string" || typeof s.label !== "string") continue;
    const points = Array.isArray(s.points)
      ? s.points.map(parsePoint).filter((p): p is SeriesPoint => p !== null)
      : [];
    out.push({ key: s.key, label: s.label, points });
  }
  return out;
}

function parseColumns(v: unknown): Column[] {
  if (!Array.isArray(v)) return [];
  const out: Column[] = [];
  for (const c of v) {
    if (!isRecord(c) || typeof c.key !== "string") continue;
    out.push({
      key: c.key,
      label: typeof c.label === "string" ? c.label : c.key,
      format: strOrNull(c.format),
    });
  }
  return out;
}

function parseItems(v: unknown): RankedItem[] {
  if (!Array.isArray(v)) return [];
  const out: RankedItem[] = [];
  for (const i of v) {
    if (!isRecord(i) || typeof i.label !== "string") continue;
    out.push({
      label: i.label,
      primary: strOrNull(i.primary),
      secondary: strOrNull(i.secondary),
      badge: strOrNull(i.badge),
    });
  }
  return out;
}

function parseQuery(v: unknown): WidgetQuery | null {
  if (!isRecord(v) || typeof v.tool !== "string" || !isRecord(v.input)) return null;
  return { tool: v.tool, input: v.input };
}

/**
 * Validate an untrusted widget document at the boundary. Returns null for any malformed or
 * unknown shape so the renderer degrades safely instead of crashing.
 */
export function parseWidgetSpec(value: unknown): WidgetSpec | null {
  if (!isRecord(value) || value.schemaVersion !== 2) return null;
  if (typeof value.id !== "string" || typeof value.title !== "string") return null;

  const base = {
    schemaVersion: 2 as const,
    id: value.id,
    title: value.title,
    description: strOrNull(value.description),
    query: parseQuery(value.query),
  };

  switch (value.type) {
    case "stat":
      return {
        ...base,
        type: "stat",
        value: typeof value.value === "number" ? value.value : null,
        format: strOrNull(value.format),
        hint: strOrNull(value.hint),
      };
    case "time-series":
      return { ...base, type: "time-series", series: parseSeries(value.series), yFormat: strOrNull(value.yFormat) };
    case "bar-chart":
      return {
        ...base,
        type: "bar-chart",
        series: parseSeries(value.series),
        yFormat: strOrNull(value.yFormat),
        stacked: value.stacked === true,
      };
    case "table":
      return {
        ...base,
        type: "table",
        columns: parseColumns(value.columns),
        rows: Array.isArray(value.rows) ? value.rows.filter(isRecord) : [],
      };
    case "ranked-list":
      return { ...base, type: "ranked-list", items: parseItems(value.items) };
    default:
      return null;
  }
}

/** Parse a list of widgets, dropping any that are malformed. */
export function parseWidgetSpecs(value: unknown): WidgetSpec[] {
  if (!Array.isArray(value)) return [];
  return value.map(parseWidgetSpec).filter((w): w is WidgetSpec => w !== null);
}
