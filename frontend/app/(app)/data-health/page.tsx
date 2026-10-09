"use client";

import { useRef, useState } from "react";
import Link from "next/link";
import { Activity, AlertTriangle, RefreshCw, Timer, Upload, Wrench } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { useApi } from "@/lib/demo-mode";
import { isApiError, type ConnectorStatus } from "@/lib/api";
import { useToast } from "@/components/feedback/toast";
import { useShellStatus } from "@/components/app-shell/shell-status";
import { PageHeader } from "@/components/layout/page-header";
import { SourceTile } from "@/components/sources/source-tile";
import { formatAgo } from "@/components/trust/trust-indicator";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { ConnectorStatusBadge } from "@/components/connectors/connector-status-badge";
import { StatCard } from "@/components/dashboard/stat-card";
import { ActivityChart } from "@/components/dashboard/activity-chart";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";

function SectionError({ message }: { message: string }) {
  return <p className="rounded-xl bg-destructive-soft p-4 text-sm text-destructive">{message}</p>;
}

export default function DataHealthPage() {
  const api = useApi();
  const connectors = useApiData((api) => api.listConnectorStatuses());
  const summary = useApiData((api) => api.getDashboardSummary());
  const activity = useApiData((api) => api.getDashboardActivity());
  const [running, setRunning] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const { toast } = useToast();
  const shell = useShellStatus();
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function run(source: string) {
    setRunning(source);
    try {
      const result = await api.runConnector(source);
      await connectors.reload();
      shell.refresh();
      toast(
        result.status === "failed"
          ? { title: `${source} run failed`, description: "See the card for what went wrong.", tone: "error" }
          : { title: `${source} ran`, description: result.status === "no_new_data" ? "No new data since the last run." : "New data is in.", tone: "success" },
      );
    } catch (e) {
      toast({
        title: `Couldn't run ${source}`,
        description: isApiError(e) ? e.message : "Something went wrong starting the connector. Try again in a minute.",
        tone: "error",
      });
    } finally {
      setRunning(null);
    }
  }

  async function uploadCsv(file: File) {
    setUploading(true);
    try {
      await api.uploadOpenTableCsv(file);
      toast({ title: "OpenTable CSV imported", description: file.name, tone: "success" });
      await connectors.reload();
      shell.refresh();
    } catch (e) {
      toast({
        title: "Couldn't import that CSV",
        description: isApiError(e) ? e.message : "Check it's the reservations export from OpenTable and try again.",
        tone: "error",
      });
    } finally {
      setUploading(false);
    }
  }

  if (connectors.loading) return <LoadingState rows={4} />;
  if (connectors.error) {
    if (connectors.error.code === "NOT_PERMITTED")
      return <PermissionDenied subject="connector status" />;
    return (
      <ErrorState
        title="Couldn't load data health"
        message={connectors.error.message}
        correlationId={connectors.error.correlationId}
        onRetry={connectors.reload}
      />
    );
  }
  if (!connectors.data || connectors.data.length === 0) {
    return (
      <EmptyState
        title="No connector data"
        description="Run status will appear here once ingestion starts."
      />
    );
  }

  const failing = connectors.data.filter((c) => c.status === "failed").length;

  return (
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
          if (file) uploadCsv(file);
          e.target.value = "";
        }}
      />

      <section className="grid gap-4 lg:grid-cols-2" aria-label="Sources">
        {connectors.data.map((c) => (
          <ConnectorCard
            key={c.source}
            connector={c}
            running={running === c.source}
            busy={running !== null}
            uploading={uploading}
            onRun={() => run(c.source)}
            onUpload={() => fileInputRef.current?.click()}
          />
        ))}
      </section>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Ingestion summary">
        {summary.loading ? (
          <Skeleton className="h-28 w-full rounded-xl" />
        ) : summary.error ? (
          <SectionError message="Couldn't load the ingestion summary. Refresh the page to try again." />
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

      <section className="flex flex-col gap-3">
        {activity.loading ? (
          <Skeleton className="h-40 w-full rounded-xl" />
        ) : activity.error ? (
          <SectionError message="Couldn't load run activity. Refresh the page to try again." />
        ) : (
          <ActivityChart title="Run activity · last 14 days" points={activity.data ?? []} />
        )}
      </section>
    </div>
  );
}

function formatRunTime(iso: string): string {
  return new Date(iso).toLocaleString("en-AU", {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
  });
}

/**
 * One source. A failure states what happened and puts the fix next to it
 * (heuristic 9); "No new data" stays neutral because it is an expected state,
 * not a fault (docs/design-system.md).
 */
function ConnectorCard({
  connector: c,
  running,
  busy,
  uploading,
  onRun,
  onUpload,
}: {
  connector: ConnectorStatus;
  running: boolean;
  busy: boolean;
  uploading: boolean;
  onRun: () => void;
  onUpload: () => void;
}) {
  const failed = c.status === "failed";
  const isUpload = c.source.toLowerCase() === "opentable";
  const action = isUpload ? (
    <Button variant="outline" size="sm" onClick={onUpload} disabled={uploading}>
      <Upload aria-hidden="true" />
      {uploading ? "Uploading…" : "Upload CSV"}
    </Button>
  ) : c.runnable ? (
    <Button variant={failed ? "default" : "outline"} size="sm" onClick={onRun} disabled={busy}>
      <RefreshCw className={running ? "animate-spin" : undefined} aria-hidden="true" />
      {running ? "Running…" : failed ? "Run again" : "Run now"}
    </Button>
  ) : (
    <span className="text-xs text-muted-foreground">Sends data to us automatically</span>
  );

  return (
    <article
      className={`flex flex-col gap-4 rounded-xl border p-4 ${failed ? "border-destructive/30" : ""}`}
      aria-label={c.source}
    >
      <div className="flex items-center gap-3">
        <SourceTile source={c.source} size="lg" />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{c.source}</p>
          <p className="truncate text-xs text-muted-foreground">{c.connectorName}</p>
        </div>
        <div className="ml-auto">
          <ConnectorStatusBadge status={c.status} />
        </div>
      </div>

      {c.failure ? (
        <Alert variant="destructive" className="border-0 bg-destructive-soft">
          <AlertTriangle aria-hidden="true" />
          <AlertTitle className="text-foreground">
            {c.failure.type} at {formatRunTime(c.failure.at)}
          </AlertTitle>
          <AlertDescription className="text-muted-foreground">
            {c.failure.message ?? "The source didn't say why."} Figures from {c.source} after this run
            are missing until it succeeds. Check the connection, then run it again.
          </AlertDescription>
        </Alert>
      ) : failed ? (
        <Alert variant="destructive" className="border-0 bg-destructive-soft">
          <AlertTriangle aria-hidden="true" />
          <AlertTitle className="text-foreground">The latest run failed</AlertTitle>
          <AlertDescription className="text-muted-foreground">
            Figures from {c.source} may be out of date.{" "}
            <Link href="/logs" className="font-medium text-foreground underline underline-offset-2">
              Check the logs
            </Link>{" "}
            for the cause{c.runnable ? ", then run it again" : ""}.
          </AlertDescription>
        </Alert>
      ) : null}

      <dl className="grid grid-cols-2 gap-3 text-xs">
        <div>
          <dt className="text-muted-foreground">Last run</dt>
          <dd className="mt-0.5 text-sm font-medium">
            {c.lastRunAt ? (
              <span title={formatRunTime(c.lastRunAt)}>{formatAgo(c.lastRunAt)}</span>
            ) : (
              "Never"
            )}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Recent failures</dt>
          <dd className="mt-0.5 text-sm font-medium tabular-nums">{c.failureCount}</dd>
        </div>
      </dl>

      <div className="mt-auto flex items-center gap-2">{action}</div>
    </article>
  );
}
