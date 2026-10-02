"use client";

import { useState } from "react";
import { useApi, useDemoMode } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { buildKnownFields, buildRuleRows } from "@/lib/rule-logic";
import { RecomputeBanner } from "@/components/rules/recompute-banner";
import { RuleList } from "@/components/rules/rule-list";
import { RuleEditor } from "@/components/rules/rule-editor";
import { RuleAudit } from "@/components/rules/rule-audit";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import type { ResolutionRule } from "@/lib/api";

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
      <div>
        <h1 className="text-xl font-semibold">Resolution rules</h1>
        <p className="text-sm text-muted-foreground">
          How disagreements between sources are resolved, per field.
        </p>
      </div>

      {status ? <RecomputeBanner status={status} /> : null}

      <RuleList
        rows={rows}
        onEdit={(id) => setEditing(rules?.find((r) => r.id === id) ?? null)}
        onDelete={async (id) => {
          await api.deleteResolutionRule(id);
          await Promise.all([reload(), reloadStatus(), reloadAudit()]);
        }}
        onNew={() => setCreating(true)}
      />

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-muted-foreground">Change history</h2>
        <RuleAudit entries={audit ?? []} />
      </section>

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
