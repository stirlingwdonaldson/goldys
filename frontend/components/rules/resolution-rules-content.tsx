"use client";

import { invalidateBusinessReads } from "@/lib/query/invalidation";

import { useState } from "react";
import { useApi, useDemoMode } from "@/lib/demo-mode";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { buildKnownFields, buildRuleRows, entityLabel, fieldLabel, summarizeRule } from "@/lib/rule-logic";
import { useToast } from "@/components/feedback/toast";
import { Button } from "@/components/ui/button";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { CircleHelp } from "lucide-react";
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

/** A "?" next to the page title explaining that rule changes take effect immediately. */
function RulesApplyHelp() {
  return (
    <HoverCard openDelay={150}>
      <HoverCardTrigger asChild>
        <button
          type="button"
          className="rounded-full text-muted-foreground/70 hover:text-foreground"
          aria-label="About resolution rules"
        >
          <CircleHelp className="size-4" aria-hidden="true" />
        </button>
      </HoverCardTrigger>
      <HoverCardContent className="w-80 text-sm">
        <p className="font-medium">Rules apply immediately</p>
        <p className="mt-1 text-muted-foreground">
          Saving a rule recomputes the resolved views straight away, so every screen reflects the
          new decision without a separate &ldquo;apply&rdquo; step.
        </p>
      </HoverCardContent>
    </HoverCard>
  );
}

/**
 * The resolution-rules screen: the rule list, editor, recompute banner, and audit.
 * Backed by the demo fixtures in demo mode and the live rule-engine endpoints in
 * live mode.
 */
export function ResolutionRulesContent() {
  const queryApi = useApi();
  const api = useApi();
  const queryClient = useQueryClient();
  const saveRule = useMutation({ mutationFn: api.saveResolutionRule });
  const deleteRule = useMutation({ mutationFn: api.deleteResolutionRule });
  const { demo } = useDemoMode();
  const { data: rules, isPending: loading, error, refetch: reload } = useQuery({ queryKey: ["listResolutionRules"], queryFn: ({ signal }) => queryApi.listResolutionRules({ signal }) });
  const { data: status } = useQuery({ queryKey: ["getRecomputeStatus"], queryFn: ({ signal }) => queryApi.getRecomputeStatus({ signal }) });
  const { data: audit } = useQuery({ queryKey: ["listRuleAudit"], queryFn: ({ signal }) => queryApi.listRuleAudit({ signal }) });
  const { data: products } = useQuery({ queryKey: ["listProducts"], queryFn: ({ signal }) => queryApi.listProducts({ signal }) });

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
      await deleteRule.mutateAsync(deleting.id);
      toast({
        title: "Rule deleted",
        description: `${entityLabel(deleting.entityType)} · ${fieldLabel(deleting.entityType, deleting.fieldKey)}`,
        tone: "success",
      });
      setDeleting(null);
      await invalidateBusinessReads(queryClient);
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
  if (error && !rules) {
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
      <PageHeader
        title="Resolution rules"
        description="How disagreements between sources are resolved, per field."
        status={<RulesApplyHelp />}
      />

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
          await saveRule.mutateAsync(input);
          setCreating(false);
          setEditing(null);
          await invalidateBusinessReads(queryClient);
        }}
        onCancel={() => {
          setCreating(false);
          setEditing(null);
        }}
      />
    </div>
  );
}
