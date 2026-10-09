"use client";

import { useState } from "react";
import { Copy } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { Button } from "@/components/ui/button";
import { ConnectorStatusBadge } from "@/components/connectors/connector-status-badge";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import type { ConnectorStatus } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";

function stackTraceOf(c: ConnectorStatus): string {
  return c.failure?.stackTrace ?? "";
}

async function copy(text: string) {
  await navigator.clipboard.writeText(text);
}

export default function LogsPage() {
  const connectors = useApiData((api) => api.listConnectorStatuses());
  const [expanded, setExpanded] = useState<string | null>(null);

  if (connectors.loading) return <LoadingState rows={4} />;
  if (connectors.error) {
    return (
      <ErrorState
        title="Couldn't load logs"
        message={connectors.error.message}
        onRetry={connectors.reload}
      />
    );
  }
  const rows = connectors.data ?? [];
  const allText = rows.map(stackTraceOf).filter(Boolean).join("\n\n");

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Logs"
        description="Connector runs and the full failure detail behind each."
        actions={
          <>
            <Button variant="outline" size="sm" onClick={() => copy(allText)} disabled={!allText}>
          <Copy className="mr-1.5 h-4 w-4" aria-hidden="true" />
          Copy all
        </Button>
          </>
        }
      />

      <div className="rounded-lg border">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              <th className="px-4 py-2 font-medium">Source</th>
              <th className="px-4 py-2 font-medium">Connector</th>
              <th className="px-4 py-2 font-medium">Status</th>
              <th className="px-4 py-2 font-medium">Last run</th>
              <th className="px-4 py-2 font-medium">Failure</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {rows.map((c) => {
              const isOpen = expanded === c.source;
              return (
                <FragmentRow
                  key={c.source}
                  connector={c}
                  isOpen={isOpen}
                  onToggle={() => setExpanded(isOpen ? null : c.source)}
                />
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function FragmentRow({
  connector: c,
  isOpen,
  onToggle,
}: {
  connector: ConnectorStatus;
  isOpen: boolean;
  onToggle: () => void;
}) {
  return (
    <>
      <tr
        className="cursor-pointer border-b hover:bg-muted/40"
        onClick={onToggle}
        data-testid={`row-${c.source}`}
      >
        <td className="px-4 py-2 font-medium">{c.source}</td>
        <td className="px-4 py-2 text-muted-foreground">{c.connectorName}</td>
        <td className="px-4 py-2">
          <ConnectorStatusBadge status={c.status} />
        </td>
        <td className="px-4 py-2 text-muted-foreground">
          {c.lastRunAt ? new Date(c.lastRunAt).toLocaleString() : "—"}
        </td>
        <td className="px-4 py-2 text-destructive">
          {c.failure ? `${c.failure.type}: ${c.failure.message ?? ""}` : "—"}
        </td>
        <td className="px-4 py-2 text-right">
          <Button
            variant="ghost"
            size="sm"
            aria-label={`Copy ${c.source} log`}
            disabled={!stackTraceOf(c)}
            onClick={(e) => {
              e.stopPropagation();
              copy(stackTraceOf(c));
            }}
          >
            <Copy className="h-4 w-4" aria-hidden="true" />
          </Button>
        </td>
      </tr>
      {isOpen && c.failure?.stackTrace ? (
        <tr className="border-b bg-muted/20">
          <td colSpan={6} className="px-4 py-3">
            <pre className="whitespace-pre-wrap text-xs text-muted-foreground">
              {c.failure.stackTrace}
            </pre>
          </td>
        </tr>
      ) : null}
    </>
  );
}
