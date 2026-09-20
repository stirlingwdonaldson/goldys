"use client";

import { useApiData } from "@/lib/use-api-data";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import type { ConnectorRunStatus } from "@/lib/api";

const STATUS_LABEL: Record<ConnectorRunStatus, string> = {
  success: "Success",
  partial: "Partial",
  failed: "Failed",
  no_new_data: "No new data",
  never_run: "Never run",
};

const STATUS_VARIANT: Record<ConnectorRunStatus, "default" | "secondary" | "destructive" | "outline"> = {
  success: "secondary",
  partial: "outline",
  failed: "destructive",
  no_new_data: "outline",
  never_run: "outline",
};

export default function ConnectorsPage() {
  const { data, loading, error, reload } = useApiData((api) => api.listConnectorStatuses());

  if (loading) return <LoadingState rows={4} />;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load connector status"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }
  if (!data || data.length === 0) {
    return (
      <EmptyState
        title="No connector data"
        description="Run status will appear here once ingestion starts."
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Connectors</h1>
        <p className="text-sm text-muted-foreground">
          In-scope Phase 1 sources and their latest run.
        </p>
      </div>
      <div className="rounded-lg border">
        {data.map((c, i) => (
          <div
            key={c.source}
            className={`flex items-center justify-between gap-4 p-4 ${i > 0 ? "border-t" : ""}`}
          >
            <div>
              <p className="text-sm font-medium">{c.source}</p>
              <p className="text-xs text-muted-foreground">
                {c.connectorName}
                {c.lastRunAt ? ` · last run ${new Date(c.lastRunAt).toLocaleString()}` : " · never run"}
              </p>
            </div>
            <Badge variant={STATUS_VARIANT[c.status]}>{STATUS_LABEL[c.status]}</Badge>
          </div>
        ))}
      </div>
    </div>
  );
}
