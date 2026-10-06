"use client";

import { useState } from "react";
import { Trash2 } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { useApi } from "@/lib/demo-mode";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import { parseWidgetSpecs } from "@/components/widgets/parse";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";

function DashboardView({ id }: { id: string }) {
  const rendered = useApiData((api) => api.renderDashboard(id), [id]);
  if (rendered.loading) return <LoadingState rows={2} />;
  if (rendered.error) {
    return (
      <ErrorState
        title="Couldn't render dashboard"
        message={rendered.error.message}
        correlationId={rendered.error.correlationId}
        onRetry={rendered.reload}
      />
    );
  }
  const widgets = parseWidgetSpecs(rendered.data ?? []);
  if (widgets.length === 0) {
    return <p className="text-sm text-muted-foreground">This dashboard has no renderable widgets.</p>;
  }
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      {widgets.map((w) => (
        <WidgetRenderer key={w.id} widget={w} />
      ))}
    </div>
  );
}

export default function DashboardsPage() {
  const api = useApi();
  const list = useApiData((api) => api.listDashboards());
  const [selectedId, setSelectedId] = useState<string | null>(null);

  if (list.loading) return <LoadingState rows={3} />;
  if (list.error) {
    if (list.error.code === "NOT_PERMITTED") return <PermissionDenied subject="saved dashboards" />;
    return (
      <ErrorState
        title="Couldn't load dashboards"
        message={list.error.message}
        correlationId={list.error.correlationId}
        onRetry={list.reload}
      />
    );
  }
  if (!list.data || list.data.length === 0) {
    return (
      <EmptyState
        title="No saved dashboards"
        description="Ask Goldy's a reporting question, then save the answer as a dashboard."
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Dashboards</h1>
        <p className="text-sm text-muted-foreground">Saved reporting views, rendered live.</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {list.data.map((d) => (
          <button
            key={d.id}
            onClick={() => setSelectedId(d.id === selectedId ? null : d.id)}
            className={`rounded-md border px-3 py-1.5 text-sm ${
              d.id === selectedId ? "bg-muted" : "hover:bg-muted"
            }`}
          >
            {d.title}
          </button>
        ))}
      </div>

      {selectedId ? (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-muted-foreground">
              {list.data.find((d) => d.id === selectedId)?.title}
            </h2>
            <Button
              variant="ghost"
              size="sm"
              onClick={async () => {
                await api.deleteDashboard(selectedId);
                setSelectedId(null);
                await list.reload();
              }}
            >
              <Trash2 className="h-4 w-4" aria-hidden="true" />
              Delete
            </Button>
          </div>
          <DashboardView id={selectedId} />
        </div>
      ) : null}
    </div>
  );
}
