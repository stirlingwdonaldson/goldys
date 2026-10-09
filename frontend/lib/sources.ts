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
