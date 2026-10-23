"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { DashboardCard } from "@/components/dashboards/dashboard-card";
import { DashboardEditor } from "@/components/dashboards/dashboard-editor";
import type { Api, SavedDashboardSummary } from "@/lib/api/types";
import { PageHeader } from "@/components/layout/page-header";

/** Pinned dashboards first, then most recently updated. */
function sortDashboards(list: SavedDashboardSummary[]): SavedDashboardSummary[] {
  return [...list].sort((a, b) => {
    const aPinned = a.pinned === true;
    const bPinned = b.pinned === true;
    if (aPinned !== bPinned) return aPinned ? -1 : 1;
    return b.updatedAt.localeCompare(a.updatedAt);
  });
}

/**
 * The dashboards library: pinned-first cards with a pin toggle, a "New from template" flow, and
 * the editor once a dashboard is opened. Takes an explicit `api` so tests can inject a stub; the
 * page passes the demo/live singleton.
 */
export function DashboardsPageView({ api }: { api: Api }) {
  const queryClient = useQueryClient();
  const [actionError, setActionError] = useState<string | null>(null);
  const list = useQuery({ queryKey: ["listDashboards"], queryFn: ({ signal }) => api.listDashboards({ signal }) });
  const templates = useQuery({ queryKey: ["listDashboardTemplates"], queryFn: ({ signal }) => api.listDashboardTemplates({ signal }) });

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [templatesOpen, setTemplatesOpen] = useState(false);
  const [creating, setCreating] = useState<string | null>(null);

  const pin = useMutation({
    mutationFn: api.toggleDashboardPin,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["listDashboards"] }),
    onError: error => setActionError(error.message),
  });
  const create = useMutation({
    mutationFn: api.createDashboardFromTemplate,
    onSuccess: created => {
      void queryClient.invalidateQueries({ queryKey: ["listDashboards"] });
      setTemplatesOpen(false);
      setSelectedId(created.id);
    },
    onError: error => setActionError(error.message),
    onSettled: () => setCreating(null),
  });
  function togglePin(id: string) {
    if (pin.isPending) return;
    setActionError(null);
    pin.mutate(id);
  }
  function createFromTemplate(templateId: string) {
    if (create.isPending) return;
    setActionError(null);
    setCreating(templateId);
    create.mutate(templateId);
  }

  const templateDialog = (
    <Dialog open={templatesOpen} onOpenChange={setTemplatesOpen}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>New from template</DialogTitle>
          <DialogDescription>Pick a starting point. A private copy is created for you.</DialogDescription>
        </DialogHeader>
        {actionError ? <p role="alert">{actionError}</p> : null}
        {templates.isPending ? (
          <LoadingState rows={2} />
        ) : templates.error ? (
          <ErrorState
            title="Couldn't load templates"
            message={templates.error.message}
            onRetry={templates.refetch}
          />
        ) : (
          <ul className="flex flex-col gap-2">
            {(templates.data ?? []).map((t) => (
              <li key={t.id}>
                <button
                  type="button"
                  disabled={creating !== null}
                  onClick={() => createFromTemplate(t.id)}
                  className="flex w-full flex-col gap-1 rounded-md border p-3 text-left hover:bg-muted disabled:opacity-50"
                >
                  <span className="text-sm font-medium">{t.name}</span>
                  <span className="text-xs text-muted-foreground">{t.description}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  );

  if (list.isPending) return <LoadingState rows={3} />;
  if (list.isLoadingError) {
    if (list.error.code === "NOT_PERMITTED") return <PermissionDenied subject="saved dashboards" />;
    return (
      <ErrorState
        title="Couldn't load dashboards"
        message={list.error.message}
        correlationId={list.error.correlationId}
        onRetry={list.refetch}
      />
    );
  }

  if (selectedId) {
    return (
      <DashboardEditor
        api={api}
        dashboardId={selectedId}
        onClose={() => setSelectedId(null)}
        onDeleted={async () => {
          setSelectedId(null);
          await list.refetch();
        }}
        onChanged={() => { void queryClient.invalidateQueries({ queryKey: ["listDashboards"] }); }}
      />
    );
  }

  if (!list.data || list.data.length === 0) {
    return (
      <>
        <EmptyState
          title="No saved dashboards"
          description="Start from a template, or ask Goldy's a reporting question and save the answer as a dashboard."
          action={
            <Button onClick={() => setTemplatesOpen(true)}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              New from template
            </Button>
          }
        />
        {templateDialog}
      </>
    );
  }

  const sorted = sortDashboards(list.data);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Custom dashboards"
        description="Saved reporting views, rendered live."
        actions={
          <>
            <Button onClick={() => setTemplatesOpen(true)}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          New from template
        </Button>
          </>
        }
      />

      {actionError ? <p role="alert">{actionError}</p> : null}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {sorted.map((d) => (
          <DashboardCard
            key={d.id}
            dashboard={d}
            onOpen={() => setSelectedId(d.id)}
            onTogglePin={() => togglePin(d.id)}
          />
        ))}
      </div>

      {templateDialog}
    </div>
  );
}
