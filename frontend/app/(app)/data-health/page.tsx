"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Activity, AlertTriangle, LogIn, RefreshCw, Timer, Upload, Wrench } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { useApi } from "@/lib/demo-mode";
import { isApiError, type ConnectorStatus } from "@/lib/api";
import { useToast } from "@/components/feedback/toast";
import { useShellStatus } from "@/components/app-shell/shell-status";
import { PageHeader } from "@/components/layout/page-header";
import { SourceTile } from "@/components/sources/source-tile";
import { connectorDisplayName, groupConnectors, opentableDatasetLabel, sourceIdentity, type SourceGroup } from "@/lib/sources";
import { formatAgo, formatDateTime } from "@/lib/format";
import { InlineError } from "@/components/states/inline-error";
import { Section } from "@/components/layout/section";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { ConnectorStatusBadge } from "@/components/connectors/connector-status-badge";
import { OpenTableSyncDialog } from "@/components/connectors/opentable-sync-dialog";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { StatCard } from "@/components/data-display/stat-card";
import { ActivityChart } from "@/components/dashboard/activity-chart";
import { PipelineMap } from "@/components/pipeline/pipeline-map";
import { DependenciesSection } from "@/components/platform-map/dependencies-section";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";

export default function DataHealthPage() {
  const api = useApi();
  const connectors = useApiData((api) => api.listConnectorStatuses());
  const summary = useApiData((api) => api.getDashboardSummary());
  const activity = useApiData((api) => api.getDashboardActivity());
  const [running, setRunning] = useState<string | null>(null);
  const [uploading, setUploading] = useState<string | null>(null);
  const [uploadTarget, setUploadTarget] = useState<string | null>(null);
  const { toast } = useToast();
  const shell = useShellStatus();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [syncOpen, setSyncOpen] = useState(false);
  const reloadConnectors = connectors.reload;
  const refreshShell = shell.refresh;
  const onOpenTableSynced = useCallback(() => {
    void reloadConnectors();
    refreshShell();
  }, [reloadConnectors, refreshShell]);

  // A pull runs asynchronously on the server: "Run now" returns while the run is still
  // RUNNING. Poll quietly while any source reports "running" so the card stays disabled
  // until the run actually finishes (whoever started it: this tab, another tab, or the
  // schedule), then announce the outcome of runs started from this page.
  const anyRunning = connectors.data?.some((c) => c.status === "running") ?? false;
  const refreshConnectors = connectors.refresh;
  const startedHere = useRef(new Set<string>());
  useEffect(() => {
    if (!anyRunning) return;
    const id = setInterval(() => void refreshConnectors(), RUN_POLL_MS);
    return () => clearInterval(id);
  }, [anyRunning, refreshConnectors]);
  useEffect(() => {
    for (const c of connectors.data ?? []) {
      if (c.status === "running" || !startedHere.current.has(c.source)) continue;
      startedHere.current.delete(c.source);
      refreshShell();
      toast(runOutcomeToast(c));
    }
  }, [connectors.data, refreshShell, toast]);

  async function run(source: string) {
    setRunning(source);
    try {
      const result = await api.runConnector(source);
      if (result.status === "running") {
        startedHere.current.add(source);
        toast({
          title: `${sourceIdentity(source).label} is running`,
          description: "This can take a few minutes. We'll let you know when it finishes.",
          tone: "info",
        });
        await connectors.refresh();
      } else {
        await connectors.reload();
        shell.refresh();
        toast(runOutcomeToast(result));
      }
    } catch (e) {
      toast({
        title: `Couldn't run ${sourceIdentity(source).label}`,
        description: isApiError(e) ? e.message : "Something went wrong starting the connector. Try again in a minute.",
        tone: "error",
      });
    } finally {
      setRunning(null);
    }
  }

  async function uploadCsv(connectorName: string, file: File) {
    setUploading(connectorName);
    try {
      await api.uploadConnectorCsv(connectorName, file);
      toast({
        title: `${connectorDisplayName("OPENTABLE", connectorName)} imported`,
        description: file.name,
        tone: "success",
      });
      await connectors.reload();
      shell.refresh();
    } catch (e) {
      toast({
        title: "Couldn't import that CSV",
        description: isApiError(e) ? e.message : "Check the file matches this export and try again.",
        tone: "error",
      });
    } finally {
      setUploading(null);
    }
  }

  // Every branch renders `<>{body}{syncDialog}</>` so the dialog keeps the same position in the
  // tree: finishing a sync reloads the connector list (briefly showing the loading state), and the
  // open dialog, with its result, must stay mounted through that.
  const syncDialog = (
    <OpenTableSyncDialog open={syncOpen} onOpenChange={setSyncOpen} onSynced={onOpenTableSynced} />
  );

  let placeholder: React.ReactNode = null;
  if (connectors.loading) {
    placeholder = <LoadingState rows={4} />;
  } else if (connectors.error) {
    placeholder =
      connectors.error.code === "NOT_PERMITTED" ? (
        <PermissionDenied subject="connector status" />
      ) : (
        <ErrorState
          title="Couldn't load data health"
          message={connectors.error.message}
          correlationId={connectors.error.correlationId}
          onRetry={connectors.reload}
        />
      );
  } else if (!connectors.data || connectors.data.length === 0) {
    placeholder = (
      <EmptyState
        title="No connector data"
        description="Run status will appear here once ingestion starts."
      />
    );
  }
  if (placeholder !== null || !connectors.data) {
    return (
      <>
        {placeholder}
        {syncDialog}
      </>
    );
  }

  const failing = connectors.data.filter((c) => c.status === "failed").length;
  const groups = groupConnectors(connectors.data);

  return (
    <>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Data health"
          status={
            failing > 0 ? (
              <Badge variant="failed">
                {failing} {failing === 1 ? "source" : "sources"} failing
              </Badge>
            ) : (
              <Badge variant="success">All sources healthy</Badge>
            )
          }
          description="When each source last delivered data, and what to do when one stops."
        />

        {/* Hidden file input for the OpenTable CSV upload, triggered from its card. */}
        <input
          ref={fileInputRef}
          type="file"
          accept=".csv,text/csv"
          className="hidden"
          aria-hidden="true"
          tabIndex={-1}
          onChange={(e) => {
            const file = e.target.files?.[0];
            const target = uploadTarget;
            if (file && target) uploadCsv(target, file);
            setUploadTarget(null);
            e.target.value = "";
          }}
        />

        <section className="grid gap-4 lg:grid-cols-2" aria-label="Sources">
          {groups.map((group) => {
            const anyRunning = group.connectors.some((c) => c.status === "running");
            return (
              <ConnectorCard
                key={group.source}
                group={group}
                running={running === group.source || anyRunning}
                busy={running !== null || anyRunning}
                uploading={group.connectors.some((c) => uploading === c.connectorName)}
                onRun={() => run(group.source)}
                onUpload={(connectorName) => {
                  setUploadTarget(connectorName);
                  fileInputRef.current?.click();
                }}
                onSync={group.syncable ? () => setSyncOpen(true) : undefined}
              />
            );
          })}
        </section>

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Ingestion summary">
          {summary.loading ? (
            <Skeleton className="h-28 w-full rounded-xl" />
          ) : summary.error ? (
            <InlineError>Couldn&apos;t load the ingestion summary. Refresh the page to try again.</InlineError>
          ) : (
            <>
              <StatCard
                label="Ingestion completeness"
                value={
                  summary.data?.ingestionCompleteness == null
                    ? "—"
                    : `${summary.data.ingestionCompleteness}%`
                }
                hint="Share of scheduled connector runs that succeed"
                icon={Activity}
              />
              <StatCard
                label="Time to detect failure"
                value={summary.data?.timeToDetectFailure ?? "—"}
                hint="How quickly a failed run becomes visible"
                icon={Timer}
              />
              <StatCard
                label="Manual overrides"
                value={summary.data?.overrideUsage ? String(summary.data.overrideUsage.count) : "—"}
                hint={summary.data?.overrideUsage?.period ?? "How often staff resolve by hand"}
                icon={Wrench}
              />
            </>
          )}
        </section>

        <Section
          title="How data flows"
          description="Each source lands in the raw ledger, becomes canonical entities, then resolves into the figures your screens show. Select a step to open it."
          card
        >
          <PipelineMap connectors={connectors.data} />
        </Section>

        <DependenciesSection />

        <section className="flex flex-col gap-3">
          {activity.loading ? (
            <Skeleton className="h-40 w-full rounded-xl" />
          ) : activity.error ? (
            <InlineError>Couldn&apos;t load run activity. Refresh the page to try again.</InlineError>
          ) : (
            <ActivityChart title="Run activity · last 14 days" points={activity.data ?? []} />
          )}
        </section>
      </div>
      {syncDialog}
    </>
  );
}

const RUN_POLL_MS = 3000;

function runOutcomeToast(c: ConnectorStatus) {
  const label = sourceIdentity(c.source).label;
  return c.status === "failed"
    ? { title: `${label} run failed`, description: "See the card for what went wrong.", tone: "error" as const }
    : {
        title: `${label} ran`,
        description: c.status === "no_new_data" ? "No new data since the last run." : "New data is in.",
        tone: "success" as const,
      };
}

/**
 * One source system. A failure states what happened and puts the fix next to it
 * (heuristic 9); "No new data" stays neutral because it is an expected state,
 * not a fault (docs/design/design-system.md).
 */
function ConnectorCard({
  group,
  running,
  busy,
  uploading,
  onRun,
  onUpload,
  onSync,
}: {
  group: SourceGroup;
  running: boolean;
  busy: boolean;
  uploading: boolean;
  onRun: () => void;
  onUpload: (connectorName: string) => void;
  onSync: (() => void) | undefined;
}) {
  const failed = group.status === "failed";
  const isSync = onSync !== undefined;
  // Sources arrive as codes ("LIGHTSPEED"); the card is named by the source system.
  const name = sourceIdentity(group.source).label;

  // OpenTable: sign in through the live view to sync, or drop one of the three CSV exports.
  const action = isSync ? (
    <>
      <Button variant={failed ? "default" : "outline"} size="sm" onClick={onSync}>
        <LogIn aria-hidden="true" />
        Sync OpenTable
      </Button>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="sm" disabled={uploading}>
            <Upload aria-hidden="true" />
            {uploading ? "Uploading…" : "Upload CSV"}
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {group.connectors.map((c) => (
            <DropdownMenuItem key={c.connectorName} onSelect={() => onUpload(c.connectorName)}>
              {opentableDatasetLabel(c.connectorName)}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </>
  ) : group.runnable ? (
    <Button variant={failed ? "default" : "outline"} size="sm" onClick={onRun} disabled={busy}>
      <RefreshCw className={running ? "animate-spin" : undefined} aria-hidden="true" />
      {running ? "Running…" : failed ? "Run again" : "Run now"}
    </Button>
  ) : (
    <span className="text-xs text-muted-foreground">Sends data to us automatically</span>
  );

  return (
    <Card
      role="group"
      className={cn("flex flex-col gap-4 p-4", failed && "border-destructive/30")}
      aria-label={name}
    >
      <div className="flex items-center gap-3">
        <SourceTile source={group.source} size="lg" />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{name}</p>
          {isSync ? (
            <p className="truncate text-xs text-muted-foreground">Reservations · Reviews · Menu</p>
          ) : null}
        </div>
        <div className="ml-auto">
          <ConnectorStatusBadge status={group.status} />
        </div>
      </div>

      {group.failure ? (
        <Alert variant="destructive" className="border-0 bg-destructive-soft">
          <AlertTriangle aria-hidden="true" />
          <AlertTitle className="text-foreground">
            {group.failure.type} at {formatDateTime(group.failure.at)}
          </AlertTitle>
          <AlertDescription className="text-muted-foreground">
            {group.failure.message ?? "The source didn't say why."} Figures from {name} after this run
            are missing until it succeeds. Check the connection, then run it again.
          </AlertDescription>
        </Alert>
      ) : failed ? (
        <Alert variant="destructive" className="border-0 bg-destructive-soft">
          <AlertTriangle aria-hidden="true" />
          <AlertTitle className="text-foreground">The latest run failed</AlertTitle>
          <AlertDescription className="text-muted-foreground">
            Figures from {name} may be out of date.{" "}
            <Link href="/logs" className="font-medium text-foreground underline underline-offset-2">
              Check the logs
            </Link>{" "}
            for the cause{group.runnable ? ", then run it again" : ""}.
          </AlertDescription>
        </Alert>
      ) : null}

      <dl className="grid gap-3 text-xs">
        <div>
          <dt className="text-muted-foreground">Last run</dt>
          <dd className="mt-0.5 text-sm font-medium">
            {group.lastRunAt ? (
              <span title={formatDateTime(group.lastRunAt)}>{formatAgo(group.lastRunAt)}</span>
            ) : (
              "Never"
            )}
          </dd>
        </div>
      </dl>

      <div className="mt-auto flex items-center gap-2">{action}</div>
    </Card>
  );
}
