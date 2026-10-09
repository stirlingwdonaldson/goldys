import { cn } from "@/lib/utils";
import { sourceIdentity } from "@/lib/sources";

/**
 * Each source system keeps one colour and monogram everywhere it appears, so
 * people recognise it at a glance instead of reading a label (heuristic 6).
 * Identity comes from the registry in lib/sources.ts.
 */
interface SourceTileProps {
  source: string;
  size?: "sm" | "lg";
  className?: string;
}

export function SourceTile({ source, size = "sm", className }: SourceTileProps) {
  const style = sourceIdentity(source);
  return (
    <span
      aria-hidden="true"
      className={cn(
        "inline-grid shrink-0 place-items-center font-bold",
        size === "sm" ? "size-5 rounded-md text-2xs" : "size-9 rounded-md text-xs",
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
      {children ?? <span className="text-muted-foreground">{sourceIdentity(source).label}</span>}
    </span>
  );
}
