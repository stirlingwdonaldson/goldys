"use client";

import { useState } from "react";
import { Copy, ScrollText } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { ConnectorStatusBadge } from "@/components/connectors/connector-status-badge";
import { LoadingState } from "@/components/states/loading-state";
import { useApiData } from "@/lib/use-api-data";
import { logLine } from "./log-line";
import type { ConnectorStatus } from "@/lib/api";

async function copy(text: string) {
  await navigator.clipboard.writeText(text);
}

/** A global drawer listing each source's latest run + failure, with copy affordances. */
export function LogsDrawer() {
  const [open, setOpen] = useState(false);
  const connectors = useApiData((api) => api.listConnectorStatuses());

  const allText = connectors.data?.map(logLine).join("\n\n") ?? "";

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm" className="gap-1.5">
          <ScrollText className="h-4 w-4" aria-hidden="true" />
          Logs
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="flex w-full flex-col gap-4 sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Logs</SheetTitle>
        </SheetHeader>

        <div className="flex items-center justify-end">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => copy(allText)}
            disabled={!allText}
            aria-label="Copy all logs"
          >
            <Copy className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Copy all
          </Button>
        </div>

        <div className="flex-1 space-y-2 overflow-y-auto">
          {connectors.loading ? <LoadingState rows={4} /> : null}
          {connectors.error ? (
            <p className="text-sm text-destructive">{connectors.error.message}</p>
          ) : null}
          {connectors.data?.map((c) => (
            <LogRow key={c.source} connector={c} />
          ))}
        </div>
      </SheetContent>
    </Sheet>
  );
}

function LogRow({ connector: c }: { connector: ConnectorStatus }) {
  return (
    <div className="rounded-md border p-3">
      <div className="flex items-center justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-medium">{c.source}</p>
          <p className="text-xs text-muted-foreground">{c.connectorName}</p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <ConnectorStatusBadge status={c.status} />
          <Button
            variant="ghost"
            size="sm"
            aria-label={`Copy ${c.source} log`}
            onClick={() => copy(logLine(c))}
          >
            <Copy className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </div>
      {c.failure ? (
        <p className="mt-2 text-xs text-destructive">
          {c.failure.at} · {c.failure.type}: {c.failure.message}
        </p>
      ) : (
        <p className="mt-2 text-xs text-muted-foreground">
          {c.lastRunAt ? `Last run ${new Date(c.lastRunAt).toLocaleString()}` : "Never run"}
        </p>
      )}
    </div>
  );
}
