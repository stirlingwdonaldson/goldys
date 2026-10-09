"use client";

import { useState } from "react";
import { useApi, useDemoMode } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { buildKnownFields, buildRuleRows, entityLabel, fieldLabel, summarizeRule } from "@/lib/rule-logic";
import { useToast } from "@/components/feedback/toast";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { RecomputeBanner } from "@/components/rules/recompute-banner";
import { RuleList } from "@/components/rules/rule-list";
import { RuleEditor } from "@/components/rules/rule-editor";
import { RuleAudit } from "@/components/rules/rule-audit";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import type { ResolutionRule } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";

const ENTITIES = ["daily_sales", "product_sales"];

/**
 * The resolution-rules screen: the rule list, editor, recompute banner, and audit.
 * Backed by the demo fixtures in demo mode and the live rule-engine endpoints in
 * live mode.
 */
export function ResolutionRulesContent() {
  const api = useApi();
  const { demo } = useDemoMode();
  const { data: rules, loading, error, reload } = useApiData((api) => api.listResolutionRules());
  const { data: status, reload: reloadStatus } = useApiData((api) => api.getRecomputeStatus());
  const { data: audit, reload: reloadAudit } = useApiData((api) => api.listRuleAudit());
  const { data: products } = useApiData((api) => api.listProducts());

  const [editing, setEditing] = useState<ResolutionRule | null>(null);
  const [creating, setCreating] = useState(false);
  // Deleting a rule can't be undone, so it is confirmed first (heuristic 5).
  const [deleting, setDeleting] = useState<ResolutionRule | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const { toast } = useToast();

  async function confirmDelete() {
    if (!deleting) return;
    setDeleteBusy(true);
    try {
      await api.deleteResolutionRule(deleting.id);
      toast({
        title: "Rule deleted",
        description: `${entityLabel(deleting.entityType)} · ${fieldLabel(deleting.entityType, deleting.fieldKey)}`,
        tone: "success",
      });
      setDeleting(null);
      await Promise.all([reload(), reloadStatus(), reloadAudit()]);
    } catch (e) {
      toast({
        title: "Couldn't delete the rule",
        description: e instanceof Error ? e.message : "Try again in a moment.",
        tone: "error",
      });
    } finally {
      setDeleteBusy(false);
    }
  }

  if (loading) return <LoadingState rows={4} />;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load resolution rules"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }

  const knownFields = buildKnownFields(products ?? []);
  const rows = buildRuleRows(rules ?? [], knownFields);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Resolution rules" description="How disagreements between sources are resolved, per field." />

      {status ? <RecomputeBanner status={status} /> : null}

      <RuleList
        rows={rows}
        onEdit={(id) => setEditing(rules?.find((r) => r.id === id) ?? null)}
        onDelete={(id) => setDeleting(rules?.find((r) => r.id === id) ?? null)}
        onNew={() => setCreating(true)}
      />

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-muted-foreground">Change history</h2>
        <RuleAudit entries={audit ?? []} />
      </section>

      <Dialog open={deleting !== null} onOpenChange={(o) => !o && !deleteBusy && setDeleting(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Delete this rule?</DialogTitle>
            <DialogDescription>
              {deleting
                ? `${entityLabel(deleting.entityType)} · ${fieldLabel(deleting.entityType, deleting.fieldKey)}: ${summarizeRule(deleting)}. `
                : null}
              Figures it currently decides will need a manual decision in Reconciliation again. The
              deletion is kept in the change history.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setDeleting(null)} disabled={deleteBusy}>
              Keep rule
            </Button>
            <Button variant="destructive" onClick={confirmDelete} disabled={deleteBusy}>
              {deleteBusy ? "Deleting…" : "Delete rule"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <RuleEditor
        open={creating || editing !== null}
        initial={editing}
        entities={ENTITIES}
        fieldsByEntity={knownFields}
        demo={demo}
        onSave={async (input) => {
          await api.saveResolutionRule(input);
          setCreating(false);
          setEditing(null);
          await Promise.all([reload(), reloadStatus(), reloadAudit()]);
        }}
        onCancel={() => {
          setCreating(false);
          setEditing(null);
        }}
      />
    </div>
  );
}
