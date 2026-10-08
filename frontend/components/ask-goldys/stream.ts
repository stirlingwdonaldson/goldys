import type { AnswerPayload, DashboardDraft, MetricProvenance, TraceEntry } from "./types";
import type {
  DashboardFilters,
  FreshnessState,
  MetricQuery,
  SavedWidget,
  TrustState,
  TrustSummary,
  WidgetLayout,
} from "@/lib/api/types";
import { parseWidgetSpecs } from "@/components/widgets/parse";

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

function strOrNull(v: unknown): string | null {
  return typeof v === "string" ? v : null;
}

function strArray(v: unknown): string[] {
  return Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];
}

function parseLayout(v: unknown): WidgetLayout {
  if (!isRecord(v)) return { w: 0, h: 0 };
  return { w: typeof v.w === "number" ? v.w : 0, h: typeof v.h === "number" ? v.h : 0 };
}

function parseRange(v: unknown): MetricQuery["range"] | null {
  if (!isRecord(v) || typeof v.from !== "string" || typeof v.to !== "string") return null;
  return {
    from: v.from,
    to: v.to,
    calendar: v.calendar === "CALENDAR" ? "CALENDAR" : "TRADING",
  };
}

const TRUST_STATES = new Set<TrustState>([
  "VERIFIED",
  "RESOLVED_BY_RULE",
  "MANUALLY_OVERRIDDEN",
  "SINGLE_SOURCE",
  "CONFLICTED",
  "INCOMPLETE",
  "NOT_RECEIVED",
]);

const FRESHNESS_STATES = new Set<FreshnessState>(["FRESH", "STALE", "SOURCE_FAILURE", "UNKNOWN"]);

/**
 * Validate an untrusted trust summary at the boundary. Returns null when the shape is not a
 * usable summary (missing a known {@link TrustState}); unknown fields degrade to null defaults.
 */
function parseTrust(v: unknown): TrustSummary | null {
  if (!isRecord(v) || typeof v.state !== "string" || !TRUST_STATES.has(v.state as TrustState)) {
    return null;
  }
  return {
    state: v.state as TrustState,
    freshness:
      typeof v.freshness === "string" && FRESHNESS_STATES.has(v.freshness as FreshnessState)
        ? (v.freshness as FreshnessState)
        : "UNKNOWN",
    authoritativeSource: strOrNull(v.authoritativeSource),
    resolvedAt: strOrNull(v.resolvedAt),
    lastIngestionAt: strOrNull(v.lastIngestionAt),
    threshold: typeof v.threshold === "number" ? v.threshold : null,
  };
}

function parseMetricQuery(v: unknown): MetricQuery | null {
  if (!isRecord(v) || typeof v.metric !== "string") return null;
  const range = parseRange(v.range);
  if (!range) return null;
  return {
    metric: v.metric,
    range,
    grain: v.grain === "WEEK" || v.grain === "MONTH" ? v.grain : "DAY",
    dimensions: strArray(v.dimensions),
    comparison: strOrNull(v.comparison),
  };
}

function parseSavedWidget(v: unknown): SavedWidget | null {
  if (!isRecord(v) || typeof v.id !== "string" || typeof v.renderType !== "string") return null;
  return {
    id: v.id,
    renderType: v.renderType,
    queries: Array.isArray(v.queries)
      ? v.queries.map(parseMetricQuery).filter((q): q is MetricQuery => q !== null)
      : [],
    layout: parseLayout(v.layout),
  };
}

function parseFilters(v: unknown): DashboardFilters {
  if (!isRecord(v)) return { dateRange: null, comparison: null, dimensions: [] };
  return {
    dateRange: parseRange(v.dateRange),
    comparison: strOrNull(v.comparison),
    dimensions: strArray(v.dimensions),
  };
}

/**
 * Validate the untrusted dashboard draft at the boundary. Returns null when the shape is not a
 * usable draft (missing title); malformed widgets are dropped, filters degrade to empty.
 */
export function parseDraft(v: unknown): DashboardDraft | null {
  if (!isRecord(v) || typeof v.title !== "string") return null;
  return {
    title: v.title,
    description: strOrNull(v.description),
    filters: parseFilters(v.filters),
    widgets: Array.isArray(v.widgets)
      ? v.widgets.map(parseSavedWidget).filter((w): w is SavedWidget => w !== null)
      : [],
    dashboardId: strOrNull(v.dashboardId),
  };
}

/**
 * Validate the untrusted provenance list at the boundary. Malformed provenance entries are
 * dropped wholesale (falling back to an empty provenance for that trace entry) rather than
 * partially parsed.
 */
function parseProvenance(v: unknown): MetricProvenance[] {
  if (!Array.isArray(v)) return [];
  const out: MetricProvenance[] = [];
  for (const p of v) {
    if (!isRecord(p) || typeof p.metric !== "string" || typeof p.definitionVersion !== "string") {
      continue;
    }
    out.push({
      metric: p.metric,
      definitionVersion: p.definitionVersion,
      range: parseRange(p.range) ?? { from: "", to: "", calendar: "CALENDAR" },
      grain: p.grain === "WEEK" || p.grain === "MONTH" ? p.grain : "DAY",
      sourceDomain: typeof p.sourceDomain === "string" ? p.sourceDomain : "",
      dataFreshness: typeof p.dataFreshness === "string" ? p.dataFreshness : "",
      missingPeriods: strArray(p.missingPeriods),
      calculationVersion: typeof p.calculationVersion === "string" ? p.calculationVersion : "",
      trust: parseTrust(p.trust),
    });
  }
  return out;
}

function parseTrace(v: unknown): TraceEntry[] {
  if (!Array.isArray(v)) return [];
  const out: TraceEntry[] = [];
  for (const t of v) {
    if (!isRecord(t) || typeof t.tool !== "string" || typeof t.description !== "string") continue;
    out.push({ tool: t.tool, description: t.description, provenance: parseProvenance(t.provenance) });
  }
  return out;
}

/** Parse a single `\n\n`-delimited SSE block into its event name and data string. */
export function parseSseBlock(block: string): { event: string; data: string } {
  let event = "message";
  const dataLines: string[] = [];
  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    else if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
  }
  return { event, data: dataLines.join("\n") };
}

/** The CSRF token Spring stores in a cookie, read back into the X-XSRF-TOKEN header. */
function csrfToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

interface StreamHandlers {
  onText: (delta: string) => void;
  onAnswer: (answer: AnswerPayload) => void;
  onError: (message: string) => void;
}

/**
 * POST a single user turn to the chat endpoint and stream the SSE response. The backend owns the
 * thread and conversation history (server-authoritative), so the client only sends `threadId` and
 * the new `message` — never the assistant messages or a full history array. Native `fetch` +
 * `ReadableStream` (not `EventSource`, which cannot POST).
 */
export async function streamChat(
  threadId: string,
  message: string,
  handlers: StreamHandlers,
): Promise<void> {
  const csrf = csrfToken();
  const response = await fetch("/api/conversational/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}),
    },
    credentials: "same-origin",
    body: JSON.stringify({ threadId, message }),
  });

  if (!response.ok) {
    let message = `Request failed (HTTP ${response.status}).`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body?.message) message = body.message;
    } catch {
      // keep the default message
    }
    handlers.onError(message);
    return;
  }

  const reader = response.body?.getReader();
  if (!reader) {
    handlers.onError("Could not read the response stream.");
    return;
  }

  const decoder = new TextDecoder();
  let buffer = "";
  const dispatch = (raw: string) => {
    const { event, data } = parseSseBlock(raw);
    if (!data) return;
    try {
      const payload = JSON.parse(data) as Record<string, unknown>;
      if (event === "text" && typeof payload.delta === "string") handlers.onText(payload.delta);
      else if (event === "answer") handlers.onAnswer(parseAnswer(payload));
      else if (event === "error" && typeof payload.message === "string")
        handlers.onError(payload.message);
    } catch {
      // a malformed event is dropped, not fatal
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split("\n\n");
    buffer = blocks.pop() ?? "";
    for (const block of blocks) dispatch(block);
  }
  buffer += decoder.decode();
  if (buffer.trim()) dispatch(buffer);
}

/** Validate the untrusted answer payload at the boundary; malformed widgets are dropped. */
export function parseAnswer(payload: Record<string, unknown>): AnswerPayload {
  return {
    widgets: parseWidgetSpecs(payload.widgets),
    trace: parseTrace(payload.trace),
    asOf: typeof payload.asOf === "string" ? payload.asOf : "",
    notices: Array.isArray(payload.notices)
      ? payload.notices.filter((n): n is string => typeof n === "string")
      : [],
    draft: parseDraft(payload.draft),
  };
}
