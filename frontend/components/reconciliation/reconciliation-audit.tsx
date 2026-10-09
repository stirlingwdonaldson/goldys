import { Badge } from "@/components/ui/badge";
import { entityLabel, fieldLabel } from "@/lib/rule-logic";
import type { ReconciliationAuditChange, ReconciliationAuditEntry } from "@/lib/api";

const CHANGE_LABEL: Record<ReconciliationAuditChange, string> = {
  created: "Rule created",
  updated: "Rule changed",
  deleted: "Rule deleted",
  set: "Override set",
  removed: "Override removed",
};

function subject(e: ReconciliationAuditEntry): string {
  const entity = entityLabel(e.entityType);
  const field = fieldLabel(e.entityType, e.fieldKey);
  if (e.kind === "override") {
    return e.change === "removed"
      ? `${entity} · ${field} — ${e.source ?? "a source"} no longer authoritative`
      : `${entity} · ${field} — ${e.source ?? "a source"} chosen`;
  }
  return `${entity} · ${field}`;
}

/** The combined rule + override change history, read-only, in venue language. */
export function ReconciliationAudit({ entries }: { entries: ReconciliationAuditEntry[] }) {
  if (entries.length === 0) {
    return <p className="text-sm text-muted-foreground">No changes yet.</p>;
  }

  return (
    <div className="rounded-xl border">
      {entries.map((e, i) => (
        <div
          key={`${e.kind}:${e.change}:${e.at}:${i}`}
          className={`flex items-center justify-between gap-4 p-3 ${i > 0 ? "border-t" : ""}`}
        >
          <div className="min-w-0">
            <p className="text-sm font-medium">{subject(e)}</p>
            <p className="truncate text-xs text-muted-foreground">
              {e.by} · {new Date(e.at).toLocaleString()}
              {e.reason ? ` · “${e.reason}”` : ""}
            </p>
          </div>
          <Badge variant={e.change === "deleted" || e.change === "removed" ? "destructive" : "secondary"}>
            {CHANGE_LABEL[e.change]}
          </Badge>
        </div>
      ))}
    </div>
  );
}
