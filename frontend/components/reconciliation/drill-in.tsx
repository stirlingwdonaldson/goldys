"use client";

import { useState } from "react";
import { ArrowLeft } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { needsDecision } from "@/lib/reconciliation-logic";
import type { Api, ReconciliationRecord } from "@/lib/api";

interface DrillInProps {
  fetchRecord: (api: Api) => Promise<ReconciliationRecord>;
  deps: unknown[];
  onBack: () => void;
  onSave: (field: string, source: string, reason?: string) => Promise<void>;
  saving: boolean;
}

export function ReconciliationDrillIn({ fetchRecord, deps, onBack, onSave, saving }: DrillInProps) {
  const { data: record, loading, error, reload } = useApiData(fetchRecord, deps);
  const [selection, setSelection] = useState<Record<string, string>>({});
  const [reasons, setReasons] = useState<Record<string, string>>({});

  if (loading) return <LoadingState rows={3} />;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load this record"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }
  if (!record) return null;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="sm" onClick={onBack} aria-label="Back to list">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        </Button>
        <div>
          <h1 className="text-lg font-semibold">{record.entity}</h1>
          <p className="text-xs text-muted-foreground">Entity type: {record.entityType}</p>
        </div>
      </div>

      <div className="flex flex-col gap-3">
        {record.fields.map((field) => {
          const decide = needsDecision(field);
          return (
            <div key={field.name} className="rounded-lg border bg-card p-4">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium">{field.label}</p>
                {field.overridden ? (
                  <Badge className="border-transparent bg-status-success text-status-success-foreground">
                    Overridden · {field.authoritativeSource}
                  </Badge>
                ) : decide ? (
                  <Badge variant="outline">Needs decision</Badge>
                ) : null}
              </div>

              <div className="mt-2 grid grid-cols-2 gap-2">
                {field.sources.map((source) => (
                  <div key={source.source} className="rounded-md border p-2">
                    <p className="text-xs text-muted-foreground">{source.source}</p>
                    {source.value == null ? (
                      <Badge className="mt-1 border-transparent bg-status-missing text-status-missing-foreground">
                        No data from {source.source}
                      </Badge>
                    ) : (
                      <p className="mt-1 text-sm font-medium">{source.value}</p>
                    )}
                  </div>
                ))}
              </div>

              {decide ? (
                <div className="mt-3 flex flex-col gap-2 border-t pt-3">
                  <p className="text-xs font-medium text-muted-foreground">
                    Set authoritative source
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {field.sources
                      .filter((s) => s.value != null)
                      .map((source) => (
                        <button
                          key={source.source}
                          type="button"
                          aria-pressed={selection[field.name] === source.source}
                          onClick={() =>
                            setSelection((prev) => ({ ...prev, [field.name]: source.source }))
                          }
                          className={`rounded-md border px-3 py-1.5 text-sm ${
                            selection[field.name] === source.source
                              ? "border-primary bg-primary/10 font-medium"
                              : "hover:bg-accent"
                          }`}
                        >
                          {source.source}
                        </button>
                      ))}
                  </div>
                  <Input
                    type="text"
                    aria-label="Reason for override (optional)"
                    placeholder="Reason (optional)"
                    value={reasons[field.name] ?? ""}
                    onChange={(e) =>
                      setReasons((prev) => ({ ...prev, [field.name]: e.target.value }))
                    }
                  />
                  <Button
                    size="sm"
                    disabled={!selection[field.name] || saving}
                    onClick={() =>
                      onSave(field.name, selection[field.name], reasons[field.name] || undefined)
                    }
                  >
                    {saving ? "Saving…" : "Save override"}
                  </Button>
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
    </div>
  );
}
