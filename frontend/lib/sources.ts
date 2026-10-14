import type { ConnectorStatus, ConnectorRunStatus } from "@/lib/api";

/**
 * The one registry of source systems: display name, monogram, and identity
 * colour classes. Both `sourceLabel` (rule-logic) and the source tiles read
 * from here, so a source is named and coloured the same way everywhere.
 * Colours encode identity only, never state.
 */
export interface SourceIdentity {
  label: string;
  initials: string;
  /** Tailwind classes for the tinted tile (tokens in globals.css). */
  className: string;
}

const KNOWN: { match: RegExp; identity: SourceIdentity }[] = [
  {
    match: /lightspeed|kounta/i,
    identity: { label: "Lightspeed", initials: "LS", className: "bg-source-lightspeed-soft text-source-lightspeed" },
  },
  {
    match: /cooking|^ctb$|^ctb[-_ ]/i,
    identity: { label: "Cooking the Books", initials: "CB", className: "bg-source-ctb-soft text-source-ctb" },
  },
  {
    match: /opentable/i,
    identity: { label: "OpenTable", initials: "OT", className: "bg-source-opentable-soft text-source-opentable" },
  },
  {
    match: /deputy/i,
    identity: { label: "Deputy", initials: "DP", className: "bg-source-deputy-soft text-source-deputy" },
  },
];

/** Resolves a source code or name ("LIGHTSPEED", "CTB", "opentable") to its identity. */
export function sourceIdentity(source: string): SourceIdentity {
  const known = KNOWN.find((k) => k.match.test(source));
  if (known) return known.identity;
  const initials = source
    .split(/[\s_-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join("");
  return { label: source, initials: initials || "?", className: "bg-muted text-muted-foreground" };
}

/**
 * Display name for a connector tile. Most sources have one connector and read the
 * registry label; OpenTable has several exports (the four datasets share the OPENTABLE
 * source), so each gets a distinct name.
 */
export function connectorDisplayName(source: string, connectorName: string): string {
  if (source.toUpperCase() !== "OPENTABLE") return sourceIdentity(source).label;
  switch (connectorName) {
    case "opentable-reservations":
      return "OpenTable reservations";
    case "opentable-reviews":
      return "OpenTable reviews";
    case "opentable-menu-items":
      return "OpenTable menu";
    case "opentable-csv-drop":
      return "OpenTable legacy CSV";
    default:
      return sourceIdentity(source).label;
  }
}

/** The connector name when the tile is an upload target, else null (run-only or deprecated). */
export function uploadTargetFor(connectorName: string): string | null {
  switch (connectorName) {
    case "opentable-reservations":
    case "opentable-reviews":
    case "opentable-menu-items":
      return connectorName;
    default:
      return null;
  }
}

/** True for the deprecated thin 8-column OpenTable CSV drop (raw-only, no upload action). */
export function isLegacyCsvDrop(connectorName: string): boolean {
  return connectorName === "opentable-csv-drop";
}

/** Short label for an OpenTable dataset, for the single-card upload picker. */
export function opentableDatasetLabel(connectorName: string): string {
  switch (connectorName) {
    case "opentable-reservations":
      return "Reservations";
    case "opentable-reviews":
      return "Reviews";
    case "opentable-menu-items":
      return "Menu";
    default:
      return connectorDisplayName("OPENTABLE", connectorName);
  }
}

/** How bad a run status is, for aggregating several connectors under one source. */
const STATUS_SEVERITY: Record<ConnectorRunStatus, number> = {
  success: 0,
  no_new_data: 1,
  never_run: 1,
  running: 2,
  partial: 3,
  failed: 4,
};

/** Connectors grouped under one source system, with an aggregate status. */
export interface SourceGroup {
  source: string;
  connectors: ConnectorStatus[];
  status: ConnectorRunStatus;
  lastRunAt: string | null;
  failureCount: number;
  failure: ConnectorStatus["failure"];
  runnable: boolean;
  syncable: boolean;
}

/**
 * Groups connectors by source system so each source is one card (OpenTable's several
 * exports and Lightspeed's two feeds collapse into one), dropping the deprecated thin
 * OpenTable CSV drop. The card's status is the worst of its members.
 */
export function groupConnectors(connectors: ConnectorStatus[]): SourceGroup[] {
  const bySource = new Map<string, ConnectorStatus[]>();
  for (const c of connectors) {
    if (isLegacyCsvDrop(c.connectorName)) continue;
    const key = c.source.toUpperCase();
    const list = bySource.get(key) ?? [];
    list.push(c);
    bySource.set(key, list);
  }
  return [...bySource.entries()].map(([, members]) => ({
    source: members[0].source,
    connectors: members,
    status: members.reduce<ConnectorRunStatus>(
      (worst, m) => (STATUS_SEVERITY[m.status] > STATUS_SEVERITY[worst] ? m.status : worst),
      "success",
    ),
    lastRunAt: members.reduce<string | null>((latest, m) => {
      if (m.lastRunAt == null) return latest;
      return latest == null || m.lastRunAt > latest ? m.lastRunAt : latest;
    }, null),
    failureCount: members.reduce((sum, m) => sum + m.failureCount, 0),
    failure: members.find((m) => m.failure)?.failure ?? null,
    runnable: members.some((m) => m.runnable),
    syncable: members[0].source.toUpperCase() === "OPENTABLE",
  }));
}
