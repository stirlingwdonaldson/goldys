import type { ReconciliationField, ResolutionRule } from "@/lib/api";
import { summarizeRule } from "@/lib/rule-logic";

interface DisagreementExplanationProps {
  field: ReconciliationField;
  /** The current standing rule for this entity/field, if one exists. */
  rule: ResolutionRule | null;
}

/**
 * The "why" behind one reconciliation disagreement, in venue language: which sources differ, why
 * the current result was chosen (or that none has been chosen yet), and which standing rule applies.
 * Rendered read-only — it never offers a write action.
 */
export function DisagreementExplanation({ field, rule }: DisagreementExplanationProps) {
  const sources = field.sources.map((s) => `${s.source}: ${s.value ?? "no data"}`).join(", ");
  const chosen = field.overridden
    ? `Resolved to ${field.authoritativeSource}${
        field.overrideActor ? ` by ${field.overrideActor}` : ""
      }${field.overrideReason ? ` — “${field.overrideReason}”` : ""}.`
    : "No result chosen yet — review the figures and pick the authoritative source below.";

  return (
    <div className="mt-3 rounded-md border bg-muted/40 p-3 text-xs text-muted-foreground">
      <p className="font-medium text-foreground">Why this needs attention</p>
      <p className="mt-1">
        The sources disagree on {field.label}: {sources}.
      </p>
      <p className="mt-1">{chosen}</p>
      <p className="mt-1">
        {rule
          ? `Rule applied: ${summarizeRule(rule)}.`
          : "No automatic rule applies — this needs a manual decision."}
      </p>
    </div>
  );
}
