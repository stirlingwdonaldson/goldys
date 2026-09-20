"use client";

import { useState } from "react";
import { useApi } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { useToast } from "@/components/feedback/toast";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { ReconciliationDrillIn } from "@/components/reconciliation/drill-in";

export default function ReconciliationPage() {
  const api = useApi();
  const { data: exceptions, loading, error, reload } = useApiData((a) =>
    a.listReconciliationExceptions(),
  );
  const { toast } = useToast();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function handleSave(field: string, source: string, reason?: string) {
    if (!selectedId) return;
    setSaving(true);
    try {
      await api.saveOverride({ recordId: selectedId, field, source, reason });
      toast({
        title: "Override saved",
        description: `${field} is now resolved from ${source}.`,
        tone: "success",
      });
      await reload();
      setSelectedId(null);
    } catch (e) {
      toast({
        title: "Couldn't save override",
        description: e instanceof Error ? e.message : undefined,
        tone: "error",
      });
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <LoadingState rows={4} />;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load exceptions"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }

  if (selectedId) {
    return (
      <ReconciliationDrillIn
        recordId={selectedId}
        onBack={() => setSelectedId(null)}
        onSave={handleSave}
        saving={saving}
      />
    );
  }

  if (!exceptions || exceptions.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        <div>
          <h1 className="text-xl font-semibold">Reconciliation</h1>
          <p className="text-sm text-muted-foreground">
            Field-level conflicts between sources, shown exception-first.
          </p>
        </div>
        <EmptyState
          title="No conflicts to review"
          description="When two sources disagree on the same fact, the exception appears here."
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Reconciliation</h1>
        <p className="text-sm text-muted-foreground">
          Field-level conflicts between sources, shown exception-first.
        </p>
      </div>
      <div className="flex flex-col gap-3">
        {exceptions.map((ex) => (
          <button
            key={ex.id}
            type="button"
            onClick={() => setSelectedId(ex.id.split(":")[0])}
            className="flex items-center justify-between rounded-lg border bg-card p-4 text-left hover:bg-accent"
          >
            <div>
              <p className="text-sm font-medium">{ex.entity}</p>
              <p className="text-sm text-muted-foreground">{ex.field}</p>
            </div>
            <Badge variant={ex.status === "conflict" ? "secondary" : "outline"}>
              {ex.status === "conflict" ? "Conflict" : "Missing data"}
            </Badge>
          </button>
        ))}
      </div>
    </div>
  );
}
