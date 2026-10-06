"use client";

import { useRef, useState } from "react";
import { Activity, Timer, Wrench } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { useApi } from "@/lib/demo-mode";
import { isApiError } from "@/lib/api";
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
  return <p className="text-sm text-destructive">{message}</p>;
}

export default function DataHealthPage() {
  const api = useApi();
  const connectors = useApiData((api) => api.listConnectorStatuses());
  const summary = useApiData((api) => api.getDashboardSummary());
  const activity = useApiData((api) => api.getDashboardActivity());
  const [running, setRunning] = useState<string | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadMessage, setUploadMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function run(source: string) {
    setRunning(source);
    setRunError(null);
    try {
      await api.runConnector(source);
      await connectors.reload();
    } catch (e) {
      setRunError(isApiError(e) ? e.message : "Something went wrong running the connector.");
    } finally {
      setRunning(null);
    }
  }

  async function uploadCsv(file: File) {
    setUploading(true);
    setUploadMessage(null);
    try {
      await api.uploadOpenTableCsv(file);
      setUploadMessage({ ok: true, text: "CSV uploaded and ingested." });
      await connectors.reload();
    } catch (e) {
      setUploadMessage({ ok: false, text: isApiError(e) ? e.message : "Upload failed." });
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

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Data health</h1>
        <p className="text-sm text-muted-foreground">
          Ingestion and connector status across your sources.
        </p>
      </div>

      {runError && <p className="text-sm text-destructive">{runError}</p>}
      {uploadMessage && (
        <p className={`text-sm ${uploadMessage.ok ? "text-emerald-600" : "text-destructive"}`}>
          {uploadMessage.text}
        </p>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {summary.loading ? (
          <Skeleton className="h-24 w-full" />
        ) : summary.error ? (
          <SectionError message="Couldn't load ingestion summary." />
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

      <section className="rounded-lg border">
        {connectors.data.map((c, i) => (
          <div
            key={c.source}
            className={`flex items-center justify-between gap-4 p-4 ${i > 0 ? "border-t" : ""}`}
          >
            <div>
              <p className="text-sm font-medium">{c.source}</p>
              <p className="text-xs text-muted-foreground">
                {c.connectorName}
                {c.lastRunAt
                  ? ` · last run ${new Date(c.lastRunAt).toLocaleString()}`
                  : " · never run"}
              </p>
              {c.failure ? (
                <p className="text-xs text-destructive">
                  {c.failure.type}: {c.failure.message}
                </p>
              ) : null}
            </div>
            <div className="flex items-center gap-2">
              <ConnectorStatusBadge status={c.status} />
              {c.source.toLowerCase() === "opentable" ? (
                <>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".csv,text/csv"
                    className="hidden"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) uploadCsv(file);
                      e.target.value = "";
                    }}
                  />
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={uploading}
                  >
                    {uploading ? "Uploading…" : "Upload CSV"}
                  </Button>
                </>
              ) : c.runnable ? (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => run(c.source)}
                  disabled={running !== null}
                >
                  {running === c.source ? "Running…" : "Run now"}
                </Button>
              ) : (
                <span className="text-xs text-muted-foreground">Push-only</span>
              )}
            </div>
          </div>
        ))}
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-muted-foreground">Run activity · last 14 days</h2>
        {activity.loading ? (
          <Skeleton className="h-40 w-full" />
        ) : activity.error ? (
          <SectionError message="Couldn't load activity." />
        ) : (
          <ActivityChart points={activity.data ?? []} />
        )}
      </section>
    </div>
  );
}
