import type {
  ConnectorStatus,
  InvoiceIngestFlag,
  ReconciliationException,
  RecomputeStatus,
} from "@/lib/api/types";
import { formatDay, humanizeKey } from "@/lib/format";
import { sourceLabel } from "@/lib/rule-logic";

/**
 * The management perspectives a person can read Goldy's from. A perspective only
 * changes what is surfaced first; it never widens access (the backend still decides
 * what each signal returns for the signed-in role).
 */
export type Focus = "business" | "venue" | "kitchen" | "foh";

export const FOCUS_OPTIONS: { value: Focus; label: string; description: string }[] = [
  { value: "business", label: "Whole business", description: "Owner and GM: everything, highest risk first." },
  { value: "venue", label: "Venue", description: "Day-to-day trading across front and back of house." },
  { value: "kitchen", label: "Kitchen", description: "Food cost, invoices, suppliers and stock." },
  { value: "foh", label: "Front of house", description: "Sales, bookings and service." },
];

/**
 * A sensible starting perspective from the profile. Owners default to the whole
 * business; a BOH or FOH department defaults to its own area; anyone else (department
 * ALL, or no profile, e.g. demo mode) gets the venue view.
 */
export function defaultFocus(user: { department?: string | null; seniority?: string | null } | null): Focus {
  if (user?.seniority?.trim().toUpperCase() === "OWNER") return "business";
  const dept = user?.department?.trim().toUpperCase();
  if (dept === "BOH") return "kitchen";
  if (dept === "FOH") return "foh";
  return "venue";
}

export type WorkCategory = "decision" | "source" | "invoice" | "recompute";
export type WorkSeverity = "high" | "medium" | "low";

export const CATEGORY_LABEL: Record<WorkCategory, string> = {
  decision: "Figures to decide",
  source: "Data feeds",
  invoice: "Invoices",
  recompute: "Rule changes",
};

/** One thing that needs a person: what it is, how urgent, and where to act on it. */
export interface WorkItem {
  id: string;
  category: WorkCategory;
  severity: WorkSeverity;
  title: string;
  detail: string;
  href: string;
  /** When the underlying signal happened, if known (ISO timestamp or date). */
  at: string | null;
  /** Perspectives this item is relevant to. "business" always sees everything. */
  focus: Focus[];
}

/** Each signal is null when it couldn't be read (not permitted, or the request failed). */
export interface WorkQueueInput {
  dailyExceptions: ReconciliationException[] | null;
  productExceptions: ReconciliationException[] | null;
  connectors: ConnectorStatus[] | null;
  invoiceFlags: InvoiceIngestFlag[] | null;
  recompute: RecomputeStatus | null;
}

/**
 * Which areas a source mainly feeds. A failing source matters most to the people whose
 * numbers it carries; owners and GMs see every failure regardless.
 */
const SOURCE_FOCUS: Record<string, Focus[]> = {
  LIGHTSPEED: ["venue", "foh", "kitchen"],
  CTB: ["venue", "kitchen"],
  COOKING_THE_BOOKS: ["venue", "kitchen"],
  OPENTABLE: ["venue", "foh"],
  DEPUTY: ["venue"],
};

function focusForSource(source: string): Focus[] {
  const key = source.trim().toUpperCase().replace(/[\s-]+/g, "_");
  return SOURCE_FOCUS[key] ?? ["venue"];
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function fieldLabel(field: string): string {
  return humanizeKey(field).toLowerCase();
}

/** Daily-sales records are keyed by business date; other records carry their own name. */
function subjectOf(ex: ReconciliationException): string {
  return ISO_DATE.test(ex.recordId) ? formatDay(ex.recordId) : ex.entity || ex.recordId;
}

function capitalise(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function describeSources(ex: ReconciliationException): string {
  const parts = ex.sources.map((s) => `${sourceLabel(s.source)} ${s.value ?? "no data"}`);
  return parts.join(" vs ");
}

function decisionItems(input: WorkQueueInput): WorkItem[] {
  const daily = (input.dailyExceptions ?? []).map<WorkItem>((ex) => ({
    id: `daily:${ex.id}`,
    category: "decision",
    // A disagreement between sources is reported as disputed until someone decides,
    // so it outranks a figure that's simply missing from one source.
    severity: ex.status === "conflict" ? "high" : "medium",
    title:
      ex.status === "conflict"
        ? `Sources disagree on ${fieldLabel(ex.field)} for ${subjectOf(ex)}`
        : `${capitalise(fieldLabel(ex.field))} missing from a source for ${subjectOf(ex)}`,
    detail: describeSources(ex),
    href: `/reconciliation?record=${encodeURIComponent(ex.recordId)}`,
    at: ISO_DATE.test(ex.recordId) ? ex.recordId : null,
    focus: ["venue", "foh"],
  }));
  const product = (input.productExceptions ?? []).map<WorkItem>((ex) => {
    // Product exception ids are "<date>:<product>"; see the reconciliation page.
    const date = ex.id.split(":")[0];
    return {
      id: `product:${ex.id}`,
      category: "decision",
      severity: ex.status === "conflict" ? "medium" : "low",
      title:
        ex.status === "conflict"
          ? `Sources disagree on ${ex.recordId} sold ${formatDay(date)}`
          : `${capitalise(ex.recordId)} on ${formatDay(date)} is missing from a source`,
      detail: describeSources(ex),
      href: `/reconciliation?tab=product&date=${encodeURIComponent(date)}&product=${encodeURIComponent(ex.recordId)}`,
      at: date,
      focus: ["venue", "foh", "kitchen"],
    };
  });
  return [...daily, ...product];
}

function sourceItems(input: WorkQueueInput): WorkItem[] {
  return (input.connectors ?? []).flatMap<WorkItem>((c) => {
    const name = sourceLabel(c.source);
    const base = { category: "source" as const, href: "/data-health", focus: focusForSource(c.source) };
    if (c.status === "failed") {
      return [
        {
          ...base,
          id: `source:${c.source}`,
          severity: "high",
          title: `${name} feed is failing`,
          detail:
            c.failure?.message ??
            `${c.failureCount} failed ${c.failureCount === 1 ? "run" : "runs"}; figures from ${name} may be incomplete.`,
          at: c.failure?.at ?? c.lastRunAt,
        },
      ];
    }
    if (c.status === "partial") {
      return [
        {
          ...base,
          id: `source:${c.source}`,
          severity: "medium",
          title: `${name} only partly loaded`,
          detail: "The latest run skipped some records. Check Data health for what was missed.",
          at: c.lastRunAt,
        },
      ];
    }
    if (c.status === "never_run") {
      return [
        {
          ...base,
          id: `source:${c.source}`,
          severity: "low",
          title: `${name} hasn't delivered any data yet`,
          detail: "Reports that depend on it will show as not received.",
          at: null,
        },
      ];
    }
    return [];
  });
}

const FLAG_LABEL: Record<string, string> = {
  PDF_ONLY_LINE: "Invoice line only on the PDF",
  CSV_ONLY_LINE: "Invoice line only in the export",
  MISSING_PDF: "Invoice PDF missing",
};

function invoiceItems(input: WorkQueueInput): WorkItem[] {
  return (input.invoiceFlags ?? []).map<WorkItem>((f, i) => ({
    id: `invoice:${f.flagType}:${f.invoiceNumber ?? f.pdfFilename ?? i}:${f.stockCode ?? ""}`,
    category: "invoice",
    severity: "medium",
    title: `${FLAG_LABEL[f.flagType] ?? f.flagType.replace(/_/g, " ").toLowerCase()}${
      f.invoiceNumber ? ` · ${f.invoiceNumber}` : ""
    }`,
    detail: f.detail ?? "Check the invoice against the supplier's copy.",
    href: "/kitchen",
    at: f.occurredAt,
    focus: ["kitchen", "venue"],
  }));
}

function recomputeItems(input: WorkQueueInput): WorkItem[] {
  if (input.recompute?.state !== "failed") return [];
  return [
    {
      id: "recompute",
      category: "recompute",
      severity: "high",
      title: "A rule change didn't finish applying",
      detail: "Resolved figures may still reflect the old rule. Retry from Resolution rules.",
      href: "/resolution-rules",
      at: input.recompute.lastChangedAt,
      focus: ["venue"],
    },
  ];
}

const SEVERITY_RANK: Record<WorkSeverity, number> = { high: 0, medium: 1, low: 2 };

/** Newest first; unknown times sort last. ISO dates and timestamps compare lexically. */
function compareAt(a: string | null, b: string | null): number {
  if (a === b) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  return a < b ? 1 : -1;
}

/**
 * Turns the signals Goldy's already exposes into one prioritised queue: most severe
 * first, then most recent. Pure, so the ordering rules are unit-testable.
 */
export function buildWorkQueue(input: WorkQueueInput): WorkItem[] {
  return [...recomputeItems(input), ...sourceItems(input), ...decisionItems(input), ...invoiceItems(input)].sort(
    (a, b) => SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity] || compareAt(a.at, b.at),
  );
}

/** Items relevant to a perspective. The whole-business view sees everything. */
export function filterByFocus(items: WorkItem[], focus: Focus): WorkItem[] {
  if (focus === "business") return items;
  return items.filter((i) => i.focus.includes(focus));
}
