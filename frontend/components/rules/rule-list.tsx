import { Pencil, Plus, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  entityLabel,
  fieldLabel,
  ruleDetail,
  summarizeRule,
  type RuleRow,
} from "@/lib/rule-logic";

interface RuleListProps {
  rows: RuleRow[];
  onEdit: (id: string) => void;
  onDelete: (id: string) => void;
  onNew: () => void;
}

/** The grouped rule list: one row per known field, unresolved fields flagged. */
export function RuleList({ rows, onEdit, onDelete, onNew }: RuleListProps) {
  let lastEntity: string | null = null;
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-muted-foreground">Rules by entity</h2>
        <Button size="sm" onClick={onNew}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          New rule
        </Button>
      </div>

      <div className="rounded-lg border">
        {rows.map((r, i) => {
          const header = r.entityType !== lastEntity ? entityLabel(r.entityType) : null;
          lastEntity = r.entityType;
          return (
            <div key={`${r.entityType}:${r.fieldKey}`}>
              {header ? (
                <div className="border-b bg-muted/40 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  {header}
                </div>
              ) : null}
              <div
                className={`flex items-center justify-between gap-4 p-3 ${i > 0 && !header ? "border-t" : ""}`}
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium">{fieldLabel(r.entityType, r.fieldKey)}</p>
                  {r.rule ? (
                    <>
                      <p className="text-sm">{summarizeRule(r.rule)}</p>
                      {ruleDetail(r.rule) ? (
                        <p className="truncate text-xs text-muted-foreground">{ruleDetail(r.rule)}</p>
                      ) : null}
                    </>
                  ) : (
                    <p className="text-xs text-muted-foreground">No rule set</p>
                  )}
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {r.rule ? (
                    <>
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Edit ${fieldLabel(r.entityType, r.fieldKey)}`}
                        onClick={() => onEdit(r.rule!.id)}
                      >
                        <Pencil className="h-4 w-4" aria-hidden="true" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Delete ${fieldLabel(r.entityType, r.fieldKey)}`}
                        onClick={() => onDelete(r.rule!.id)}
                      >
                        <Trash2 className="h-4 w-4" aria-hidden="true" />
                      </Button>
                    </>
                  ) : (
                    <Badge variant="outline" className="border-transparent bg-status-warning/15 text-status-warning-foreground">
                      Unresolved
                    </Badge>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
