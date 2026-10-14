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
