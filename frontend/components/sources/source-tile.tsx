import { cn } from "@/lib/utils";

/**
 * Each source system keeps one colour and monogram everywhere it appears, so
 * people recognise it at a glance instead of reading a label (heuristic 6).
 * Colours encode identity only, never state.
 */
interface SourceStyle {
  label: string;
  initials: string;
  className: string;
}

const KNOWN: { match: RegExp; style: SourceStyle }[] = [
  {
    match: /lightspeed|kounta/i,
    style: { label: "Lightspeed", initials: "LS", className: "bg-source-lightspeed-soft text-source-lightspeed" },
  },
  {
    match: /cooking|ctb/i,
    style: { label: "Cooking the Books", initials: "CB", className: "bg-source-ctb-soft text-source-ctb" },
  },
  {
    match: /opentable/i,
    style: { label: "OpenTable", initials: "OT", className: "bg-source-opentable-soft text-source-opentable" },
  },
  {
    match: /deputy/i,
    style: { label: "Deputy", initials: "DP", className: "bg-source-deputy-soft text-source-deputy" },
  },
];

/** Resolves a source identifier ("Lightspeed", "CTB", "opentable") to its display style. */
export function sourceStyle(source: string): SourceStyle {
  const known = KNOWN.find((k) => k.match.test(source));
  if (known) return known.style;
  const initials = source
    .split(/[\s_-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join("");
  return { label: source, initials: initials || "?", className: "bg-muted text-muted-foreground" };
}

interface SourceTileProps {
  source: string;
  size?: "sm" | "lg";
  className?: string;
}

export function SourceTile({ source, size = "sm", className }: SourceTileProps) {
  const style = sourceStyle(source);
  return (
    <span
      aria-hidden="true"
      className={cn(
        "inline-grid shrink-0 place-items-center font-bold",
        size === "sm" ? "size-5 rounded-md text-[9.5px]" : "size-9 rounded-[10px] text-xs",
        style.className,
        className,
      )}
    >
      {style.initials}
    </span>
  );
}

/** Tile plus the source's name, for inline use in lists and tables. */
export function SourceLabel({
  source,
  children,
  className,
}: {
  source: string;
  children?: React.ReactNode;
  className?: string;
}) {
  return (
    <span className={cn("inline-flex items-center gap-1.5 whitespace-nowrap text-xs", className)}>
      <SourceTile source={source} />
      {children ?? <span className="text-muted-foreground">{sourceStyle(source).label}</span>}
    </span>
  );
}
