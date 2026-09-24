"use client";

import { useRef, useState } from "react";
import { useApiData } from "@/lib/use-api-data";
import { useApi } from "@/lib/demo-mode";
import { isApiError } from "@/lib/api";
import { ConnectorStatusBadge } from "@/components/connectors/connector-status-badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";

export default function ConnectorsPage() {
  const api = useApi();
  const { data, loading, error, reload } = useApiData((api) => api.listConnectorStatuses());
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
      await reload();
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
      await reload();
    } catch (e) {
      setUploadMessage({ ok: false, text: isApiError(e) ? e.message : "Upload failed." });
    } finally {
      setUploading(false);
    }
  }

  if (loading) return <LoadingState rows={4} />;
  if (error) {
    if (error.code === "NOT_PERMITTED") return <PermissionDenied subject="connector status" />;
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
      {runError && <p className="text-sm text-destructive">{runError}</p>}
      {uploadMessage && (
        <p className={`text-sm ${uploadMessage.ok ? "text-emerald-600" : "text-destructive"}`}>
          {uploadMessage.text}
        </p>
      )}
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
              ) : (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => run(c.source)}
                  disabled={running !== null}
                >
                  {running === c.source ? "Running…" : "Run now"}
                </Button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
