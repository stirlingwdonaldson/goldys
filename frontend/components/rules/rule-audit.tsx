import { Badge } from "@/components/ui/badge";
import type { RuleAuditEntry } from "@/lib/api";

/** The rule change history (who / what / when). */
export function RuleAudit({ entries }: { entries: RuleAuditEntry[] }) {
  if (entries.length === 0) {
    return <p className="text-sm text-muted-foreground">No changes yet.</p>;
  }

  return (
    <div className="rounded-lg border">
      {entries.map((e, i) => (
        <div
          key={e.id}
          className={`flex items-center justify-between gap-4 p-3 ${i > 0 ? "border-t" : ""}`}
        >
          <div className="min-w-0">
            <p className="text-sm font-medium">{e.field}</p>
            <p className="truncate text-xs text-muted-foreground">
              {e.by} · {new Date(e.at).toLocaleString()}
            </p>
          </div>
          <Badge variant={e.change === "deleted" ? "destructive" : "secondary"}>{e.change}</Badge>
        </div>
      ))}
    </div>
  );
}
